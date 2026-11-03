package org.khylo.tv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.hlsSegmentApi
import org.khylo.sdk.api.client.extensions.mediaInfoApi
import org.khylo.sdk.api.client.extensions.playStateApi
import org.khylo.sdk.api.client.extensions.videosApi
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.DeviceProfile
import org.khylo.sdk.model.api.MediaSourceInfo
import org.khylo.sdk.model.api.PlayMethod
import org.khylo.sdk.model.api.PlaybackInfoDto
import org.khylo.sdk.model.api.PlaybackProgressInfo
import org.khylo.sdk.model.api.PlaybackStartInfo
import org.khylo.sdk.model.api.PlaybackStopInfo
import timber.log.Timber

/** What the player screens show. */
data class PlayerUiState(
	val isLoading: Boolean = true,
	val isPlaying: Boolean = false,
	/** The video reached its end. */
	val ended: Boolean = false,
	val positionMs: Long = 0,
	val durationMs: Long = 0,
	val error: String? = null,
	/** How the video is reaching the TV, for the Quality panel. */
	val stream: StreamInfo? = null,
	/** Goes up each time a new stream starts (e.g. after a quality change), so the picture is rebuilt. */
	val streamGeneration: Int = 0,
	val audioTracks: List<TrackChoice> = emptyList(),
	val textTracks: List<TrackChoice> = emptyList(),
)

/** What's actually playing: the original file, or the server's conversion of it. */
data class StreamInfo(
	val direct: Boolean,
	val sourceHeight: Int?,
	val sourceBitrate: Int?,
	val maxBitrate: Int?,
) {
	val resolution: String?
		get() = when {
			sourceHeight == null -> null
			sourceHeight >= 2000 -> "4K"
			sourceHeight >= 1000 -> "1080p"
			sourceHeight >= 700 -> "720p"
			else -> "${sourceHeight}p"
		}
}

/**
 * Subtitles for this stretch of watching. Off unless the viewer turns them on; once on they stay on
 * (same language) for every episode, the next Khylo TV show and so on, until the viewer leaves the
 * player for the rest of the app or closes it.
 */
object SubtitleSession {
	/** The viewer turned subtitles on. */
	var on = false
		internal set

	/** The language (and name) of the track they chose, to pick the same again next time. */
	internal var language: String? = null
	internal var label: String? = null

	fun reset() {
		on = false
		language = null
		label = null
	}
}

/** One audio or subtitle track the viewer can pick. */
data class TrackChoice(
	val label: String,
	val detail: String?,
	val selected: Boolean,
	internal val group: androidx.media3.common.TrackGroup,
	internal val index: Int,
)

private const val TICKS_PER_MS = 10_000L

/**
 * One video playing from the server: a movie, an episode or a live channel. Multi View runs
 * several at once. It asks the server how to play the item (directly, or repackaged / converted
 * over HLS), plays it with ExoPlayer and keeps the server informed so resume points, the
 * dashboard and live-stream connections stay right.
 */
@OptIn(UnstableApi::class)
class KhyloPlayer(
	context: Context,
	private val api: ApiClient,
	httpClient: OkHttpClient,
	private val deviceProfile: () -> DeviceProfile,
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private val _state = MutableStateFlow(PlayerUiState())
	val state: StateFlow<PlayerUiState> = _state.asStateFlow()

	val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
		.setRenderersFactory(
			// Lets the bundled FFmpeg decoder handle audio formats the TV can't (AC3, DTS, ...)
			DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
		)
		.setMediaSourceFactory(
			DefaultMediaSourceFactory(
				OkHttpDataSource.Factory(httpClient.withoutCallTimeout()).setDefaultRequestProperties(
					mapOf("Authorization" to "MediaBrowser Token=\"${api.accessToken}\"")
				)
			)
		)
		.build()

	private var item: BaseItemDto? = null
	private var source: MediaSourceInfo? = null
	private var playSessionId: String? = null
	private var playMethod = PlayMethod.DIRECT_PLAY
	private var progressJob: Job? = null
	private var isReleased = false
	private var triedServerStream = false
	private var requestedStartMs: Long? = null

	/** The version of the item to play (a DVD's extra title); the default one when null. */
	private var requestedMediaSourceId: String? = null

	/** A sound or subtitle track chosen before playing (a DVD's menu); the server's choice when null. */
	private var requestedAudioIndex: Int? = null
	private var requestedSubtitleIndex: Int? = null
	private val prefs = org.khylo.tv.data.KhyloPrefs(context)

	// Subtitles shown because the TV is muted (not chosen by the viewer)
	private var mutedSubtitles = false

	// Whether this stream has had the session's subtitle choice applied yet
	private var subtitlesApplied = false

	init {
		// Subtitles start off: tracks a file marks "default" or "forced" don't turn themselves on
		exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
			.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
			.setIgnoredTextSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT or androidx.media3.common.C.SELECTION_FLAG_FORCED)
			.setSelectUndeterminedTextLanguage(false)
			.build()
		// Audio trouble is logged so a dropout can be traced
		exoPlayer.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
			override fun onAudioUnderrun(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
				Timber.w("Khylo audio underrun (buffer ${bufferSizeMs}ms, ${elapsedSinceLastFeedMs}ms since last feed)")
			}
			override fun onAudioSinkError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, audioSinkError: Exception) {
				Timber.w(audioSinkError, "Khylo audio output error")
			}
			override fun onAudioCodecError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, audioCodecError: Exception) {
				Timber.w(audioCodecError, "Khylo audio decoder error")
			}
			override fun onAudioInputFormatChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, format: androidx.media3.common.Format, decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
				Timber.i("Khylo audio format: ${format.sampleMimeType} ${format.channelCount}ch ${format.sampleRate}Hz")
			}
		})
		exoPlayer.addListener(object : Player.Listener {
			override fun onIsPlayingChanged(isPlaying: Boolean) = updateState()
			override fun onPlaybackStateChanged(playbackState: Int) = updateState()
			override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
				// The session's subtitles carry over to this stream once its tracks are known
				if (!subtitlesApplied && tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }) {
					subtitlesApplied = true
					if (SubtitleSession.on) showSessionSubtitles()
				}
				updateTracks(tracks)
			}
			override fun onPlayerError(error: PlaybackException) {
				Timber.w(error, "Khylo playback failed")
				// Some files can't be read by the TV directly (e.g. MKVs with zlib-compressed
				// tracks); the server can repackage them, so try that once before giving up
				val current = item
				if (playMethod == PlayMethod.DIRECT_PLAY && !triedServerStream && current != null) {
					triedServerStream = true
					val resumeAt = exoPlayer.currentPosition.takeIf { it > 0 } ?: requestedStartMs
					Timber.i("Retrying ${current.name} through the server")
					play(current, resumeAt, keepFallback = true)
					return
				}
				_state.value = _state.value.copy(isLoading = false, isPlaying = false, error = "This couldn't play. Try again in a moment.")
			}
		})
	}

	val isLive: Boolean get() = item?.type == BaseItemKind.TV_CHANNEL

	/**
	 * Off for Live Channels: tuning in mid-show isn't "watching" it, so nothing lands in Continue
	 * watching or your watch history.
	 */
	var recordWatching: Boolean = true

	private val reportsPosition: Boolean get() = !isLive && recordWatching

	/** Streaming quality picked in the player for this video; null follows the TV's setting. */
	var qualityOverride: Int? = null
		private set

	val currentMaxBitrate: Int? get() = qualityOverride ?: prefs.maxBitrate

	/** Restarts the stream at a different quality, from where it is now. */
	fun setQuality(maxBitrate: Int?) {
		val current = item ?: return
		qualityOverride = maxBitrate
		play(current, exoPlayer.currentPosition.takeIf { it > 0 })
	}

	fun selectTrack(choice: TrackChoice) {
		exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
			.setTrackTypeDisabled(choice.group.type, false)
			.setOverrideForType(androidx.media3.common.TrackSelectionOverride(choice.group, choice.index))
			.build()
		// Choosing subtitles keeps them on for what's watched next
		if (choice.group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
			val format = choice.group.getFormat(choice.index)
			SubtitleSession.on = true
			SubtitleSession.language = format.language
			SubtitleSession.label = format.label
			mutedSubtitles = false
		}
	}

	fun subtitlesOff() {
		hideSubtitles()
		SubtitleSession.reset()
		mutedSubtitles = false
	}

	private fun hideSubtitles() {
		exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
			.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
			.clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
			.build()
	}

	/** Turns on the subtitle track closest to the session's choice (same language, then the first). */
	private fun showSessionSubtitles(): Boolean {
		val groups = exoPlayer.currentTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
		val candidates = groups.flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it } }
		if (candidates.isEmpty()) return false
		fun formatOf(c: Pair<androidx.media3.common.Tracks.Group, Int>) = c.first.getTrackFormat(c.second)
		val pick = candidates.firstOrNull { SubtitleSession.label != null && formatOf(it).label == SubtitleSession.label && formatOf(it).language == SubtitleSession.language }
			?: candidates.firstOrNull { SubtitleSession.language != null && formatOf(it).language == SubtitleSession.language && formatOf(it).selectionFlags and androidx.media3.common.C.SELECTION_FLAG_FORCED == 0 }
			?: candidates.firstOrNull { formatOf(it).selectionFlags and androidx.media3.common.C.SELECTION_FLAG_FORCED == 0 }
			?: candidates.first()
		exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
			.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
			.setOverrideForType(androidx.media3.common.TrackSelectionOverride(pick.first.mediaTrackGroup, pick.second))
			.build()
		return true
	}

	/**
	 * The TV went silent (muted or volume at zero): subtitles come on, and go off again when the sound
	 * comes back, unless the viewer had turned them on themselves.
	 */
	/**
	 * The one-press subtitle shortcut: off if they're showing, otherwise on (the session's language,
	 * else the first track) and kept on for what's watched next.
	 * @return the subtitles' name when they came on, "" when they went off, null when there are none.
	 */
	fun toggleSubtitles(): String? {
		val showing = exoPlayer.currentTracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT && it.isSelected }
		if (showing) {
			subtitlesOff()
			return ""
		}
		if (!showSessionSubtitles()) return null
		val chosen = exoPlayer.trackSelectionParameters.overrides.values.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
		val format = chosen?.let { it.mediaTrackGroup.getFormat(it.trackIndices.firstOrNull() ?: 0) }
		SubtitleSession.on = true
		SubtitleSession.language = format?.language
		SubtitleSession.label = format?.label
		mutedSubtitles = false
		return format?.label?.takeIf { it.isNotBlank() }
			?: format?.language?.takeIf { it != "und" }?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
			?: "On"
	}

	fun setMuted(muted: Boolean) {
		when {
			muted && !SubtitleSession.on && !mutedSubtitles -> mutedSubtitles = showSessionSubtitles()
			!muted && mutedSubtitles && !SubtitleSession.on -> {
				hideSubtitles()
				mutedSubtitles = false
			}
		}
	}

	private fun updateTracks(tracks: androidx.media3.common.Tracks) {
		fun choices(type: Int) = tracks.groups.filter { it.type == type }.flatMap { group ->
			(0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
				val format = group.getTrackFormat(i)
				val language = format.language
					?.takeIf { it != "und" }
					?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
				val details = when (type) {
					androidx.media3.common.C.TRACK_TYPE_AUDIO -> listOfNotNull(
						when (format.channelCount) {
							1 -> "Mono"
							2 -> "Stereo"
							6 -> "5.1"
							8 -> "7.1"
							else -> null
						},
						format.sampleMimeType?.substringAfter('/')?.uppercase()?.replace("MP4A-LATM", "AAC")?.replace("MPEG", "MP3"),
					)
					else -> listOfNotNull(if (format.selectionFlags and androidx.media3.common.C.SELECTION_FLAG_FORCED != 0) "Forced" else null)
				}
				val label = format.label?.takeIf { it.isNotBlank() } ?: language ?: if (type == androidx.media3.common.C.TRACK_TYPE_AUDIO) "Audio track ${i + 1}" else "Subtitles ${i + 1}"
				// Name the language too when the track's own label doesn't
				val withLanguage = listOfNotNull(language?.takeIf { !label.contains(it, ignoreCase = true) }) + details
				TrackChoice(
					label = label,
					detail = withLanguage.joinToString(" · ").ifEmpty { null },
					selected = group.isTrackSelected(i),
					group = group.mediaTrackGroup,
					index = i,
				)
			}
		}
		_state.value = _state.value.copy(
			audioTracks = choices(androidx.media3.common.C.TRACK_TYPE_AUDIO),
			textTracks = choices(androidx.media3.common.C.TRACK_TYPE_TEXT),
		)
	}

	var volume: Float
		get() = exoPlayer.volume
		set(value) { exoPlayer.volume = value }

	/** Starts playing [item]; for anything but live TV, from [startPositionMs] (its resume point by default). */
	fun play(item: BaseItemDto, startPositionMs: Long? = null, keepFallback: Boolean = false, mediaSourceId: String? = null, audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
		// Switching quality replays the same item: keep the version and tracks it was playing
		val sameItem = this.item?.id == item.id
		requestedMediaSourceId = mediaSourceId ?: requestedMediaSourceId.takeIf { sameItem }
		requestedAudioIndex = audioStreamIndex ?: requestedAudioIndex.takeIf { sameItem }
		requestedSubtitleIndex = subtitleStreamIndex ?: requestedSubtitleIndex.takeIf { sameItem }
		this.item = item
		if (!keepFallback) triedServerStream = false
		requestedStartMs = startPositionMs
		subtitlesApplied = false
		mutedSubtitles = false
		hideSubtitles()
		_state.value = PlayerUiState(isLoading = true, streamGeneration = _state.value.streamGeneration + 1)
		// Switching streams (quality, or the server fallback): stop fully so the next one gets fresh
		// decoders instead of reusing ones set up for a different picture size
		if (exoPlayer.mediaItemCount > 0) {
			exoPlayer.stop()
			exoPlayer.clearMediaItems()
		}
		scope.launch {
			try {
				val (url, start) = resolve(item, startPositionMs)
				if (isReleased) return@launch
				val mediaItem = MediaItem.Builder()
					.setUri(url)
					.setSubtitleConfigurations(sidecarSubtitles(item))
					.build()
				exoPlayer.setMediaItem(mediaItem, start)
				exoPlayer.prepare()
				_state.value = _state.value.copy(
					stream = StreamInfo(
						direct = playMethod == PlayMethod.DIRECT_PLAY,
						sourceHeight = source?.mediaStreams?.firstOrNull { it.type == org.khylo.sdk.model.api.MediaStreamType.VIDEO }?.height,
						sourceBitrate = source?.bitrate,
						maxBitrate = currentMaxBitrate,
					)
				)
				exoPlayer.playWhenReady = true
				// Live Channels aren't reported at all: even "started playing" makes the server count the
				// episode as begun, which puts the show in Up next
				if (recordWatching) reportStart(start)
				startProgressReports()
			} catch (err: Exception) {
				Timber.w(err, "Couldn't start ${item.name}")
				val message = if (isLive) "This channel couldn't start. Your TV service may be out of connections." else "This couldn't start. Check that the server is reachable."
				_state.value = PlayerUiState(isLoading = false, error = message)
			}
		}
	}

	fun togglePlayPause() {
		if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
	}

	fun seekTo(positionMs: Long) {
		if (isLive) return
		exoPlayer.seekTo(positionMs.coerceIn(0, exoPlayer.duration.coerceAtLeast(0)))
		updateState()
	}

	fun seekBy(deltaMs: Long) {
		if (isLive) return
		val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0, exoPlayer.duration.coerceAtLeast(0))
		exoPlayer.seekTo(target)
		updateState()
	}

	/** Stops playback and tells the server, which frees the stream (and a live tuner connection). */
	fun release() {
		if (isReleased) return
		isReleased = true
		progressJob?.cancel()
		val position = exoPlayer.currentPosition
		exoPlayer.release()
		val stoppedItem = item ?: return scope.cancel()
		val stoppedSource = source
		val session = playSessionId
		val serverStream = playMethod != PlayMethod.DIRECT_PLAY
		CoroutineScope(Dispatchers.IO).launch {
			if (!recordWatching) {
				// Not reported, so just tell the server to stop preparing the stream (if it was)
				if (serverStream && session != null) {
					runCatching { api.hlsSegmentApi.stopEncodingProcess(deviceId = api.deviceInfo.id, playSessionId = session) }
						.onFailure { Timber.w(it, "Couldn't stop the server stream") }
				}
				return@launch
			}
			runCatching {
				api.playStateApi.reportPlaybackStopped(
					PlaybackStopInfo(
						itemId = stoppedItem.id,
						mediaSourceId = stoppedSource?.id,
						liveStreamId = stoppedSource?.liveStreamId,
						playSessionId = session,
						positionTicks = if (reportsPosition) position * TICKS_PER_MS else null,
						// Without a position the server would assume it was watched to the end
						failed = !reportsPosition && !isLive,
					)
				)
			}.onFailure { Timber.w(it, "Couldn't report playback stop") }
		}
		scope.cancel()
	}

	/**
	 * Subtitles that aren't inside the stream itself, fetched from the server as WebVTT: separate
	 * subtitle files always, and (when the server repackages the video) the embedded text ones too.
	 */
	private fun sidecarSubtitles(item: BaseItemDto): List<MediaItem.SubtitleConfiguration> {
		val mediaSource = source ?: return emptyList()
		val direct = playMethod == PlayMethod.DIRECT_PLAY
		return mediaSource.mediaStreams.orEmpty()
			.filter { it.type == org.khylo.sdk.model.api.MediaStreamType.SUBTITLE && it.isTextSubtitleStream && (it.isExternal || !direct) }
			.map { stream ->
				MediaItem.SubtitleConfiguration.Builder(
					android.net.Uri.parse("${api.baseUrl}/Videos/${item.id}/${mediaSource.id}/Subtitles/${stream.index}/0/Stream.vtt?ApiKey=${api.accessToken}")
				)
					.setMimeType(androidx.media3.common.MimeTypes.TEXT_VTT)
					.setLanguage(stream.language)
					// The server's display title reads like "Undefined - SUBRIP - External"; name it plainly
					.setLabel(
						stream.title?.takeIf { it.isNotBlank() }
							?: stream.language?.takeIf { it.isNotBlank() && it != "und" }?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { null } }
							?: "Subtitles"
					)
					.setSelectionFlags(if (stream.isForced) androidx.media3.common.C.SELECTION_FLAG_FORCED else 0)
					.build()
			}
	}

	private suspend fun resolve(item: BaseItemDto, startPositionMs: Long?): Pair<String, Long> = withContext(Dispatchers.IO) {
		val live = item.type == BaseItemKind.TV_CHANNEL
		val response = api.mediaInfoApi.getPostedPlaybackInfo(
			itemId = item.id,
			data = PlaybackInfoDto(
				deviceProfile = deviceProfile(),
				maxStreamingBitrate = currentMaxBitrate,
				mediaSourceId = requestedMediaSourceId,
				// Live streams always go through the server (HLS), which also shares one
				// provider connection between viewers
				enableDirectPlay = !live && !triedServerStream,
				enableDirectStream = !live,
				enableTranscoding = true,
				allowVideoStreamCopy = true,
				// Subtitles are the viewer's choice; the server mustn't pick (or burn in) one itself
				subtitleStreamIndex = requestedSubtitleIndex ?: -1,
				audioStreamIndex = requestedAudioIndex,
				// Live feeds switch audio formats mid-stream (around ads), which can leave the TV silent;
				// having the server re-encode just the audio keeps it steady. Video is still passed through.
				allowAudioStreamCopy = !live,
				autoOpenLiveStream = live,
			)
		).content

		check(response.errorCode == null) { "Server refused playback: ${response.errorCode}" }
		val mediaSource = requireNotNull(response.mediaSources.firstOrNull { it.id == requestedMediaSourceId } ?: response.mediaSources.firstOrNull()) { "No playable source" }
		source = mediaSource
		playSessionId = response.playSessionId

		val start = when {
			live -> 0L
			startPositionMs != null -> startPositionMs
			else -> (item.userData?.playbackPositionTicks ?: 0L) / TICKS_PER_MS
		}

		val url = when {
			!live && !triedServerStream && mediaSource.supportsDirectPlay -> {
				playMethod = PlayMethod.DIRECT_PLAY
				api.videosApi.getVideoStreamUrl(
					itemId = item.id,
					container = mediaSource.container,
					mediaSourceId = mediaSource.id,
					static = true,
					tag = mediaSource.eTag,
					liveStreamId = mediaSource.liveStreamId,
				)
			}
			mediaSource.transcodingUrl != null -> {
				playMethod = if (mediaSource.supportsDirectStream) PlayMethod.DIRECT_STREAM else PlayMethod.TRANSCODE
				api.createUrl(mediaSource.transcodingUrl!!, ignorePathParameters = true)
					// The server only re-encodes live audio when the stream address says so
					.let { if (live) "$it&AllowAudioStreamCopy=false" else it }
			}
			else -> error("No compatible stream")
		}
		url to start
	}

	private fun reportStart(startMs: Long) {
		val current = item ?: return
		val mediaSource = source
		scope.launch(Dispatchers.IO) {
			current.seriesId?.let { seriesId -> runCatching { org.khylo.tv.data.UpNextHidden.show(api, seriesId) } }
			runCatching {
				api.playStateApi.reportPlaybackStart(
					PlaybackStartInfo(
						itemId = current.id,
						mediaSourceId = mediaSource?.id,
						liveStreamId = mediaSource?.liveStreamId,
						playSessionId = playSessionId,
						positionTicks = startMs * TICKS_PER_MS,
						playMethod = playMethod,
						canSeek = !isLive,
						isPaused = false,
						isMuted = false,
						repeatMode = org.khylo.sdk.model.api.RepeatMode.REPEAT_NONE,
						playbackOrder = org.khylo.sdk.model.api.PlaybackOrder.DEFAULT,
					)
				)
			}.onFailure { Timber.w(it, "Couldn't report playback start") }
		}
	}

	private fun startProgressReports() {
		progressJob?.cancel()
		progressJob = scope.launch {
			while (isActive) {
				updateState()
				val current = item
				val mediaSource = source
				if (current != null && recordWatching && tick % 10 == 0) {
					// Read the player here: it may only be touched from the main thread
					val position = exoPlayer.currentPosition
					val paused = !exoPlayer.isPlaying
					val muted = exoPlayer.volume == 0f
					launch(Dispatchers.IO) {
						runCatching {
							api.playStateApi.reportPlaybackProgress(
								PlaybackProgressInfo(
									itemId = current.id,
									mediaSourceId = mediaSource?.id,
									liveStreamId = mediaSource?.liveStreamId,
									playSessionId = playSessionId,
									positionTicks = if (reportsPosition) position * TICKS_PER_MS else null,
									playMethod = playMethod,
									canSeek = !isLive,
									isPaused = paused,
									isMuted = muted,
									repeatMode = org.khylo.sdk.model.api.RepeatMode.REPEAT_NONE,
									playbackOrder = org.khylo.sdk.model.api.PlaybackOrder.DEFAULT,
								)
							)
						}.onFailure { Timber.w(it, "Couldn't report playback progress") }
					}
				}
				tick++
				delay(1000)
			}
		}
	}

	private var tick = 0

	private fun updateState() {
		_state.value = _state.value.copy(
			isLoading = exoPlayer.playbackState == Player.STATE_BUFFERING || exoPlayer.playbackState == Player.STATE_IDLE && _state.value.error == null && _state.value.isLoading,
			isPlaying = exoPlayer.isPlaying,
			ended = exoPlayer.playbackState == Player.STATE_ENDED,
			positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
			durationMs = exoPlayer.duration.coerceAtLeast(0),
		)
	}
}
