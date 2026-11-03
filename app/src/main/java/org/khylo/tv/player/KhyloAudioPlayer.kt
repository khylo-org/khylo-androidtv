package org.khylo.tv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import okhttp3.OkHttpClient
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.playStateApi
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.PlayMethod
import org.khylo.sdk.model.api.PlaybackOrder
import org.khylo.sdk.model.api.PlaybackProgressInfo
import org.khylo.sdk.model.api.PlaybackStartInfo
import org.khylo.sdk.model.api.PlaybackStopInfo
import org.khylo.sdk.model.api.RepeatMode
import org.koin.compose.koinInject
import timber.log.Timber

/** What the music and audiobook screens show. */
data class AudioUiState(
	val index: Int = 0,
	val isLoading: Boolean = true,
	val isPlaying: Boolean = false,
	val positionMs: Long = 0,
	val durationMs: Long = 0,
	val speed: Float = 1f,
	val shuffle: Boolean = false,
	val repeat: Int = Player.REPEAT_MODE_OFF,
	val error: String? = null,
)

private const val TICKS_PER_MS = 10_000L

/**
 * Plays a queue of audio from the server: an album, a few songs, or the parts of an audiobook.
 * Files the TV can decode stream as they are (FLAC included); anything else the server converts.
 * Each item is reported to the server, so play counts and audiobook positions are saved.
 */
@OptIn(UnstableApi::class)
class KhyloAudioPlayer(context: Context, private val api: ApiClient, httpClient: OkHttpClient) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private val _state = MutableStateFlow(AudioUiState())
	val state: StateFlow<AudioUiState> = _state.asStateFlow()

	var queue: List<BaseItemDto> = emptyList()
		private set

	val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
		.setRenderersFactory(DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON))
		.setMediaSourceFactory(
			DefaultMediaSourceFactory(
				OkHttpDataSource.Factory(httpClient.withoutCallTimeout()).setDefaultRequestProperties(
					mapOf("Authorization" to "MediaBrowser Token=\"${api.accessToken}\"")
				)
			)
		)
		.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
		.build()

	private var reportedIndex = -1
	private var lastPositionMs = 0L
	private var progressJob: Job? = null
	private var isReleased = false

	init {
		exoPlayer.addListener(object : Player.Listener {
			override fun onIsPlayingChanged(isPlaying: Boolean) = update()
			override fun onPlaybackStateChanged(playbackState: Int) = update()
			override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
				update()
				reportChange()
			}
			override fun onPlayerError(error: PlaybackException) {
				Timber.w(error, "Khylo audio playback failed")
				_state.value = _state.value.copy(isLoading = false, isPlaying = false, error = "This couldn't play. Try again in a moment.")
			}
		})
	}

	/** Plays [items] starting at [startIndex], from [startPositionMs] into that item. */
	fun play(items: List<BaseItemDto>, startIndex: Int, startPositionMs: Long = 0, speech: Boolean = false) {
		queue = items
		if (speech) {
			exoPlayer.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
		}
		exoPlayer.setMediaItems(items.map(::mediaItem), startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), startPositionMs)
		exoPlayer.prepare()
		exoPlayer.playWhenReady = true
		_state.value = AudioUiState(index = startIndex)
		startProgress()
	}

	fun togglePlayPause() = if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
	fun next() = exoPlayer.seekToNextMediaItem()

	/** Back to the start of this one, or the one before when it has only just started. */
	fun previous() {
		if (exoPlayer.currentPosition > 4_000 || !exoPlayer.hasPreviousMediaItem()) exoPlayer.seekTo(0) else exoPlayer.seekToPreviousMediaItem()
	}

	fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs.coerceIn(0, exoPlayer.duration.coerceAtLeast(0)))
	fun seekBy(deltaMs: Long) = seekTo(exoPlayer.currentPosition + deltaMs)
	fun playIndex(index: Int) = exoPlayer.seekTo(index, 0)

	fun setSpeed(speed: Float) {
		exoPlayer.setPlaybackSpeed(speed)
		update()
	}

	fun toggleShuffle() {
		exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
		update()
	}

	fun cycleRepeat() {
		exoPlayer.repeatMode = when (exoPlayer.repeatMode) {
			Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
			Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
			else -> Player.REPEAT_MODE_OFF
		}
		update()
	}

	fun release() {
		if (isReleased) return
		isReleased = true
		progressJob?.cancel()
		reportStop(reportedIndex, exoPlayer.currentPosition)
		exoPlayer.release()
		scope.cancel()
	}

	private fun mediaItem(item: BaseItemDto): MediaItem {
		// The server sends the file itself when the TV can play it, and converts it otherwise
		val url = "${api.baseUrl}/Audio/${item.id}/universal" +
			"?DeviceId=${api.deviceInfo.id}" +
			"&Container=mp3,aac,m4a,m4b,flac,ogg,opus,wav,webma" +
			"&TranscodingContainer=mp3&TranscodingProtocol=http&AudioCodec=mp3" +
			"&MaxStreamingBitrate=140000000"
		return MediaItem.Builder()
			.setUri(url)
			.setMediaId(item.id.toString())
			.setMediaMetadata(
				MediaMetadata.Builder()
					.setTitle(item.name)
					.setArtist(item.albumArtist ?: item.artists?.firstOrNull())
					.setAlbumTitle(item.album)
					.build()
			)
			.build()
	}

	private fun update() {
		_state.value = _state.value.copy(
			index = exoPlayer.currentMediaItemIndex,
			isLoading = exoPlayer.playbackState == Player.STATE_BUFFERING,
			isPlaying = exoPlayer.isPlaying,
			positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
			durationMs = exoPlayer.duration.coerceAtLeast(0),
			speed = exoPlayer.playbackParameters.speed,
			shuffle = exoPlayer.shuffleModeEnabled,
			repeat = exoPlayer.repeatMode,
			error = if (exoPlayer.isPlaying) null else _state.value.error,
		)
	}

	private fun startProgress() {
		progressJob?.cancel()
		var tick = 0
		progressJob = scope.launch {
			while (isActive) {
				update()
				lastPositionMs = exoPlayer.currentPosition
				if (tick % 10 == 0 && reportedIndex >= 0) reportProgress()
				tick++
				delay(1000)
			}
		}
	}

	// ---- Telling the server ---------------------------------------------------------------

	private fun reportChange() {
		val newIndex = exoPlayer.currentMediaItemIndex
		if (newIndex == reportedIndex) return
		reportStop(reportedIndex, lastPositionMs)
		reportedIndex = newIndex
		val item = queue.getOrNull(newIndex) ?: return
		scope.launch(Dispatchers.IO) {
			runCatching {
				api.playStateApi.reportPlaybackStart(
					PlaybackStartInfo(
						itemId = item.id,
						canSeek = true,
						isPaused = false,
						isMuted = false,
						playMethod = PlayMethod.DIRECT_STREAM,
						repeatMode = RepeatMode.REPEAT_NONE,
						playbackOrder = PlaybackOrder.DEFAULT,
					)
				)
			}.onFailure { Timber.w(it, "Couldn't report audio start") }
		}
	}

	private fun reportProgress() {
		val item = queue.getOrNull(reportedIndex) ?: return
		val position = exoPlayer.currentPosition
		val paused = !exoPlayer.isPlaying
		scope.launch(Dispatchers.IO) {
			runCatching {
				api.playStateApi.reportPlaybackProgress(
					PlaybackProgressInfo(
						itemId = item.id,
						positionTicks = position * TICKS_PER_MS,
						canSeek = true,
						isPaused = paused,
						isMuted = false,
						playMethod = PlayMethod.DIRECT_STREAM,
						repeatMode = RepeatMode.REPEAT_NONE,
						playbackOrder = PlaybackOrder.DEFAULT,
					)
				)
			}.onFailure { Timber.w(it, "Couldn't report audio progress") }
		}
	}

	private fun reportStop(index: Int, positionMs: Long) {
		val item = queue.getOrNull(index) ?: return
		CoroutineScope(Dispatchers.IO).launch {
			runCatching {
				api.playStateApi.reportPlaybackStopped(
					PlaybackStopInfo(itemId = item.id, positionTicks = positionMs * TICKS_PER_MS, failed = false)
				)
			}.onFailure { Timber.w(it, "Couldn't report audio stop") }
		}
	}
}

/** An audio player tied to the screen showing it. */
@Composable
fun rememberAudioPlayer(context: Context): KhyloAudioPlayer {
	val api = koinInject<ApiClient>()
	val http = koinInject<OkHttpClient>()
	val player = remember { KhyloAudioPlayer(context, api, http) }
	DisposableEffect(player) { onDispose { player.release() } }
	PauseWhenAppLeaves(player.exoPlayer)
	return player
}
