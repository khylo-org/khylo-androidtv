package org.khylo.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.MediaSegmentDto
import org.khylo.sdk.model.api.MediaSegmentType
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloPrefs
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KTab
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.home.subtitleFor
import org.khylo.tv.livechannels.LiveChannelsApi
import org.khylo.tv.livechannels.toItemId
import org.koin.compose.koinInject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val OVERLAY_TIMEOUT_MS = 5_000L

/** Intro matches come from the theme music, which can start a beat before the picture does. */
private const val AUTO_SKIP_LEAD_TICKS = 2 * 10_000_000L
private const val NEXT_UP_SECONDS = 10
private val clockFormat = DateTimeFormatter.ofPattern("h:mm a")

fun formatTime(ms: Long): String {
	val totalSeconds = ms / 1000
	val hours = totalSeconds / 3600
	val minutes = (totalSeconds % 3600) / 60
	val seconds = totalSeconds % 60
	return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** The viewer's other devices, and the ids of the ones already following this TV. */
private data class MirrorChoices(val devices: List<org.khylo.tv.handoff.HandoffDevice>, val following: Set<String>)

private enum class PanelTab(val label: String) {
	Subtitles("Subtitles"),
	Audio("Audio"),
	Quality("Quality"),
	Sleep("Sleep timer"),
	Handoff("Handoff"),
	Mirror("Mirror"),
}

private sealed interface SleepTimer {
	data class At(val wallClockMs: Long, val minutes: Int) : SleepTimer
	data object EndOfVideo : SleepTimer
}

/**
 * Full-screen playback. Any button shows the controls; they fade after a few seconds. Intros,
 * recaps and credits found by the server get Skip buttons (or are skipped automatically), and an
 * episode's credits bring up the next one.
 */
@Composable
fun PlayerScreen(item: BaseItemDto, startPositionMs: Long?, liveChannelId: String? = null, mediaSourceId: String? = null, audioStreamIndex: Int? = null, subtitleStreamIndex: Int? = null) {
	val navigator = navigator()
	val repository = koinInject<KhyloRepository>()
	val context = LocalContext.current
	val prefs = remember { KhyloPrefs(context) }
	val scope = rememberCoroutineScope()
	val player = rememberKhyloPlayer(item.id)
	// Subtitles chosen while watching last until the viewer leaves the player for the rest of the app
	androidx.compose.runtime.DisposableEffect(Unit) {
		onDispose { if (navigator.current !is Route.Player) SubtitleSession.reset() }
	}
	// A muted (or silent) TV gets subtitles while it stays that way
	val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
	val audioManager = remember { androidx.core.content.ContextCompat.getSystemService(appContext, android.media.AudioManager::class.java) }
	LaunchedEffect(player) {
		while (true) {
			val manager = audioManager
			if (manager != null) {
				val stream = android.media.AudioManager.STREAM_MUSIC
				player.setMuted(manager.isStreamMute(stream) || manager.getStreamVolume(stream) == 0)
			}
			delay(1_500)
		}
	}
	// Whatever was played may now count as watched; screens refresh it when you go back
	androidx.compose.runtime.DisposableEffect(item.id) { onDispose { org.khylo.tv.data.WatchStateChanges.changed(item) } }
	val state by player.state.collectAsState()
	val isLive = item.type == BaseItemKind.TV_CHANNEL
	val isLiveChannel = liveChannelId != null
	// Khylo TV acts like real TV: pause and go back, but never ahead of what's on air now
	val tunedAt = remember(item.id) { System.currentTimeMillis() }
	fun liveEdgeMs(): Long = (startPositionMs ?: 0L) + (System.currentTimeMillis() - tunedAt)
	fun forward(byMs: Long) {
		if (!isLiveChannel) {
			player.seekBy(byMs)
			return
		}
		// Catching up after going back is fine; skipping past live isn't
		val target = minOf(player.exoPlayer.currentPosition + byMs, liveEdgeMs())
		if (target > player.exoPlayer.currentPosition) player.seekTo(target)
	}
	val isEpisode = item.type == BaseItemKind.EPISODE

	// Handoff: another device took over what's playing here, so this player closes
	LaunchedEffect(Unit) {
		org.khylo.tv.handoff.HandoffInbox.stopRequests.collect {
			if (navigator.current is Route.Player) navigator.back()
		}
	}

	// Mirror: a device this TV follows paused, resumed or moved, so this one does too
	LaunchedEffect(player) {
		org.khylo.tv.handoff.HandoffInbox.commands.collect { command ->
			when (command) {
				org.khylo.tv.handoff.RemoteCommand.Pause -> player.exoPlayer.pause()
				org.khylo.tv.handoff.RemoteCommand.Unpause -> player.exoPlayer.play()
				is org.khylo.tv.handoff.RemoteCommand.SeekTo -> player.seekTo(command.positionMs)
			}
		}
	}

	LaunchedEffect(player) {
		player.recordWatching = !isLiveChannel
		player.play(item, startPositionMs, mediaSourceId = mediaSourceId, audioStreamIndex = audioStreamIndex, subtitleStreamIndex = subtitleStreamIndex)
	}

	// ---- What's around this video -------------------------------------------------------
	// Live Channels run on a schedule, so skipping inside them would make no sense
	val segments by produceState(emptyList<MediaSegmentDto>(), item.id) {
		if (!isLive && !isLiveChannel) value = repository.segments(item.id)
	}
	val nextEpisode by produceState<BaseItemDto?>(null, item.id) {
		if (isEpisode && !isLiveChannel) value = runCatching { repository.nextEpisode(item) }.getOrNull()
	}
	val favoriteId = if (isEpisode) item.seriesId else item.id.takeUnless { isLive }
	var isFavorite by remember { mutableStateOf(false) }
	LaunchedEffect(favoriteId) {
		isFavorite = favoriteId?.let { runCatching { repository.item(it).userData?.isFavorite }.getOrNull() } ?: false
	}

	var toast by remember { mutableStateOf<String?>(null) }
	// The id of the recording of what's on now, once there is one (one set up before also shows)
	var recordingTimerId by remember(item.id) { mutableStateOf(item.currentProgram?.timerId) }
	LaunchedEffect(toast) {
		if (toast != null) {
			delay(2_500)
			toast = null
		}
	}

	// Handoff: the viewer's other devices, looked up while the Handoff tab is open
	val apiClient = koinInject<org.khylo.sdk.api.client.ApiClient>()
	val httpClient = koinInject<okhttp3.OkHttpClient>()
	val handoffApi = remember { org.khylo.tv.handoff.HandoffApi(apiClient, httpClient) }
	val mirrorApi = remember { org.khylo.tv.handoff.MirrorApi(apiClient, httpClient) }
	// A Khylo TV channel is not a library item, so this TV tells the server which channel it is on; a mirror follows it there
	if (liveChannelId != null) {
		LaunchedEffect(liveChannelId) {
			while (true) {
				mirrorApi.channel(liveChannelId)
				delay(10_000)
			}
		}
		androidx.compose.runtime.DisposableEffect(liveChannelId) {
			onDispose { scope.launch(kotlinx.coroutines.NonCancellable) { mirrorApi.channel(null) } }
		}
	}

	// Mirror: when this TV follows another, ask where it is every second and close the gap by playing a little faster or
	// slower (a jump only when it is far out). The TV's own position is exact, so only the followed TV's has any error
	LaunchedEffect(player) {
		var nudged = false
		while (true) {
			delay(1_000)
			val before = android.os.SystemClock.elapsedRealtime()
			val reference = runCatching { mirrorApi.reference() }.getOrNull()
			val answered = android.os.SystemClock.elapsedRealtime()
			val playing = player.exoPlayer.playWhenReady && player.exoPlayer.playbackState == androidx.media3.common.Player.STATE_READY
			if (reference == null || reference.IsPaused || !playing || reference.ItemId.replace("-", "") != item.id.toString().replace("-", "")) {
				if (nudged) player.exoPlayer.setPlaybackSpeed(1f)
				nudged = false
				continue
			}
			// The answer was worked out about half the round trip ago
			val wanted = reference.PositionTicks / 10_000 + (answered - before) / 2
			val offset = player.exoPlayer.currentPosition - wanted
			when {
				kotlin.math.abs(offset) > 1_500 -> {
					player.seekTo(wanted + 300)
					player.exoPlayer.setPlaybackSpeed(1f)
					nudged = false
				}
				kotlin.math.abs(offset) > 120 -> {
					player.exoPlayer.setPlaybackSpeed(if (offset > 0) 0.94f else 1.06f)
					nudged = true
				}
				else -> {
					if (nudged) player.exoPlayer.setPlaybackSpeed(1f)
					nudged = false
				}
			}
		}
	}

	fun goNext() {
		val next = nextEpisode
		if (next != null) navigator.replace(Route.Player(next, 0)) else navigator.back()
	}

	// ---- Intro / recap / credits ----------------------------------------------------------
	val positionTicks = state.positionMs * 10_000
	val activeSegment = segments.firstOrNull {
		it.type in listOf(MediaSegmentType.INTRO, MediaSegmentType.RECAP, MediaSegmentType.OUTRO) &&
			positionTicks >= it.startTicks && positionTicks < it.endTicks - 10_000_000
	}
	var handledSegment by remember { mutableStateOf<UUID?>(null) }
	var dismissedSegment by remember { mutableStateOf<UUID?>(null) }

	fun skip(segment: MediaSegmentDto) {
		handledSegment = segment.id
		if (segment.type == MediaSegmentType.OUTRO) {
			if (isEpisode && nextEpisode != null) goNext() else navigator.back()
		} else {
			player.seekTo(segment.endTicks / 10_000)
		}
	}

	LaunchedEffect(activeSegment?.id, state.positionMs / 1000) {
		val segment = activeSegment ?: return@LaunchedEffect
		if (segment.id == handledSegment || !state.isPlaying) return@LaunchedEffect
		when (segment.type) {
			MediaSegmentType.INTRO, MediaSegmentType.RECAP -> if (prefs.autoSkipIntro && positionTicks >= segment.startTicks + AUTO_SKIP_LEAD_TICKS) {
				skip(segment)
				toast = if (segment.type == MediaSegmentType.INTRO) "Intro skipped" else "Recap skipped"
			}
			MediaSegmentType.OUTRO -> if (prefs.autoSkipCredits) skip(segment)
			else -> Unit
		}
	}

	// Credits of an episode with another after it: offer it, counting down when auto-play is on
	val showNextUp = activeSegment?.type == MediaSegmentType.OUTRO && nextEpisode != null && dismissedSegment != activeSegment.id
	var countdown by remember { mutableIntStateOf(-1) }
	LaunchedEffect(showNextUp, activeSegment?.id) {
		countdown = -1
		if (!showNextUp) return@LaunchedEffect
		val autoPlay = runCatching { repository.userConfiguration()?.enableNextEpisodeAutoPlay }.getOrNull() ?: true
		if (!autoPlay) return@LaunchedEffect
		countdown = NEXT_UP_SECONDS
		while (countdown > 0) {
			delay(1_000)
			if (player.exoPlayer.isPlaying) countdown--
		}
		goNext()
	}
	val skipLabel = when {
		showNextUp -> null
		activeSegment == null || activeSegment.id == dismissedSegment -> null
		activeSegment.type == MediaSegmentType.INTRO -> "Skip intro"
		activeSegment.type == MediaSegmentType.RECAP -> "Skip recap"
		else -> "Skip credits"
	}

	// ---- Sleep timer ----------------------------------------------------------------------
	var sleep by remember { mutableStateOf<SleepTimer?>(null) }
	// When it ends: the sound fades out, then a Goodnight screen, then Khylo closes so the TV can sleep
	var goodnight by remember { mutableStateOf(false) }
	val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
	LaunchedEffect(sleep) {
		val timer = sleep as? SleepTimer.At ?: return@LaunchedEffect
		delay((timer.wallClockMs - SLEEP_FADE_MS - System.currentTimeMillis()).coerceAtLeast(0))
		fadeOutAndPause(player.exoPlayer, (timer.wallClockMs - System.currentTimeMillis()).coerceIn(1_000, SLEEP_FADE_MS))
		sleep = null
		goodnight = true
	}

	// ---- When the video ends ----------------------------------------------------------------
	val channelsApi = koinInject<LiveChannelsApi>()
	LaunchedEffect(state.ended) {
		if (!state.ended) return@LaunchedEffect
		when {
			sleep == SleepTimer.EndOfVideo -> {
				sleep = null
				goodnight = true
			}
			// A Live Channel keeps going: tune to what's on next
			liveChannelId != null -> runCatching {
				val tune = channelsApi.tune(liveChannelId)
				val next = repository.item(requireNotNull(tune.current).itemId.toItemId())
				navigator.replace(Route.Player(next, tune.startPositionTicks / 10_000, liveChannelId))
			}
			isEpisode -> {
				val autoPlay = runCatching { repository.userConfiguration()?.enableNextEpisodeAutoPlay }.getOrNull() ?: true
				if (autoPlay && nextEpisode != null) goNext() else navigator.back()
			}
			else -> navigator.back()
		}
	}

	// ---- Controls -------------------------------------------------------------------------
	var overlayVisible by remember { mutableStateOf(true) }
	var panel by remember { mutableStateOf<PanelTab?>(null) }
	val handoffDevices by produceState<List<org.khylo.tv.handoff.HandoffDevice>?>(null, panel == PanelTab.Handoff) {
		value = null
		while (panel == PanelTab.Handoff) {
			value = runCatching { handoffApi.devices() }.getOrDefault(emptyList())
			delay(4_000)
		}
	}
	// Mirror: the viewer's other devices and which of them already follow this TV
	var mirrorTick by remember { mutableIntStateOf(0) }
	val mirrorChoices by produceState<MirrorChoices?>(null, panel == PanelTab.Mirror, mirrorTick) {
		if (panel != PanelTab.Mirror) {
			value = null
			return@produceState
		}
		while (panel == PanelTab.Mirror) {
			val devices = runCatching { handoffApi.devices() }.getOrDefault(emptyList())
			val following = runCatching { mirrorApi.mirrors() }.getOrDefault(emptyList()).flatMap { it.Targets }.map { it.SessionId }.toSet()
			value = MirrorChoices(devices, following)
			delay(4_000)
		}
	}
	var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
	val rootFocus = remember { FocusRequester() }
	val controlsFocus = remember { FocusRequester() }
	val seekFocus = remember { FocusRequester() }
	// Up while the controls are hidden goes straight to the seek bar
	var focusSeekOnShow by remember { mutableStateOf(false) }

	fun poke() {
		lastInput = System.currentTimeMillis()
		overlayVisible = true
	}

	LaunchedEffect(lastInput, state.isPlaying, panel) {
		delay(OVERLAY_TIMEOUT_MS)
		if (state.isPlaying && panel == null) overlayVisible = false
	}
	LaunchedEffect(overlayVisible) {
		runCatching {
			when {
				!overlayVisible -> rootFocus.requestFocus()
				focusSeekOnShow && !isLive && state.durationMs > 0 -> seekFocus.requestFocus()
				else -> controlsFocus.requestFocus()
			}
		}
		focusSeekOnShow = false
	}

	// When playback fails, the first button on the error screen takes focus so the remote works at once
	val errorFocus = remember { FocusRequester() }
	LaunchedEffect(state.error != null) {
		if (state.error != null) {
			delay(150)
			runCatching { errorFocus.requestFocus() }
		}
	}

	BackHandler {
		when {
			panel != null -> {
				panel = null
				runCatching { controlsFocus.requestFocus() }
			}
			(showNextUp || skipLabel != null) && !overlayVisible -> dismissedSegment = activeSegment?.id
			overlayVisible && state.isPlaying -> overlayVisible = false
			else -> navigator.back()
		}
	}

	Box(
		Modifier
			.fillMaxSize()
			.background(Color.Black)
			.focusRequester(rootFocus)
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				// On the error screen every key belongs to its buttons (Try again, Back); the player's own key handling would swallow them
				if (state.error != null) return@onPreviewKeyEvent false
				if (!overlayVisible) {
					when (event.key) {
						// OK takes the offer on screen (Skip intro, Next episode), like Netflix
						Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
							val segment = activeSegment
							when {
								showNextUp -> goNext()
								skipLabel != null && segment != null -> skip(segment)
								else -> poke()
							}
							true
						}
						Key.DirectionUp -> { focusSeekOnShow = true; poke(); true }
						// Down (or a remote's CC button) flips subtitles on and off without opening a menu
						Key.DirectionDown, Key(android.view.KeyEvent.KEYCODE_CAPTIONS) -> {
							toast = when (val result = player.toggleSubtitles()) {
								null -> "No subtitles for this"
								"" -> "Subtitles off"
								else -> "Subtitles on · $result"
							}
							true
						}
						Key.DirectionLeft -> { player.seekBy(-10_000); poke(); true }
						Key.DirectionRight -> { forward(30_000); poke(); true }
						Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { player.togglePlayPause(); poke(); true }
						Key.Back -> false
						else -> { poke(); true }
					}
				} else {
					when (event.key) {
						Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { player.togglePlayPause(); poke(); true }
						Key.MediaFastForward -> { forward(30_000); poke(); true }
						Key.MediaRewind -> { player.seekBy(-10_000); poke(); true }
						else -> { lastInput = System.currentTimeMillis(); false }
					}
				}
			}
			.focusable(),
	) {
		VideoSurface(player, Modifier.fillMaxSize())

		if (state.isLoading && state.error == null) {
			Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner(size = 64.dp) }
		}
		state.error?.let { message ->
			Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
				KText(message, style = KhyloType.Heading)
				Spacer(Modifier.height(20.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
					PlayerPill("Try again", KIcons.Retry, Modifier.focusRequester(errorFocus), onClick = { player.play(item, startPositionMs, mediaSourceId = mediaSourceId, audioStreamIndex = audioStreamIndex, subtitleStreamIndex = subtitleStreamIndex) })
					PlayerPill("Back", null, Modifier, onClick = { navigator.back() })
				}
			}
		}

		AnimatedVisibility(visible = overlayVisible && state.error == null, enter = fadeIn(), exit = fadeOut()) {
			Controls(
				item = item,
				state = state,
				isLive = isLive,
				isLiveChannel = isLiveChannel,
				player = player,
				logoUrl = remember(item.id) { repository.logoUrl(item) ?: repository.showLogoUrl(item) },
				liveEdgeMs = if (isLiveChannel) ({ liveEdgeMs() }) else null,
				nextEpisode = nextEpisode,
				isFavorite = isFavorite.takeIf { favoriteId != null },
				sleep = sleep,
				controlsFocus = controlsFocus,
				seekFocus = seekFocus,
				onPoke = ::poke,
				onPanel = { panel = it },
				onFavorite = {
					val id = favoriteId ?: return@Controls
					val target = !isFavorite
					isFavorite = target
					toast = if (target) (if (isEpisode) "Show added to favorites" else "Added to favorites") else "Removed from favorites"
					scope.launch { runCatching { repository.setFavorite(id, target) }.onFailure { isFavorite = !target } }
				},
				onNext = ::goNext,
				onMultiView = { navigator.replace(Route.MultiView(listOf(item))) },
				onGuide = { navigator.back() },
				recording = recordingTimerId != null,
				onRecord = {
					val timerId = recordingTimerId
					val programId = item.currentProgram?.id
					when {
						timerId != null -> scope.launch {
							runCatching { repository.cancelRecording(timerId) }
								.onSuccess { recordingTimerId = null; toast = "Recording cancelled" }
								.onFailure { toast = "Couldn't cancel the recording" }
						}
						programId == null -> toast = "There's no guide information for this channel to record"
						else -> scope.launch {
							runCatching { repository.recordProgram(programId.toString()) }
								.onSuccess { recordingTimerId = it ?: "scheduled"; toast = "Recording " + item.currentProgram?.name.orEmpty() }
								.onFailure { toast = "Couldn't schedule the recording" }
						}
					}
				},
			)
		}

		// Skip intro / recap / credits, and Up next. They rise above the controls when those show.
		val bottom by animateDpAsState(if (overlayVisible) 236.dp else 56.dp, label = "offerLift")
		Box(Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = bottom)) {
			val segment = activeSegment
			when {
				showNextUp && nextEpisode != null -> NextUpCard(nextEpisode!!, countdown, repository, highlighted = !overlayVisible, onPlay = ::goNext)
				skipLabel != null && segment != null -> PlayerPill(
					skipLabel,
					KIcons.NextEpisode,
					Modifier,
					highlighted = !overlayVisible,
					onClick = { skip(segment) },
				)
			}
		}

		// Quick confirmations ("Intro skipped", "Added to favorites")
		AnimatedVisibility(visible = toast != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)) {
			KText(
				toast.orEmpty(),
				style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold).merge(KhyloType.Centered),
				modifier = Modifier
					.clip(RoundedCornerShape(50))
					.background(Color(0xE6151618))
					.border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(50))
					.padding(horizontal = 22.dp, vertical = 12.dp),
			)
		}

		AnimatedVisibility(
			visible = panel != null,
			enter = fadeIn() + slideInHorizontally { it / 3 },
			exit = fadeOut() + slideOutHorizontally { it / 3 },
		) {
			SettingsPanel(
				tab = panel ?: PanelTab.Subtitles,
				onTab = { panel = it },
				player = player,
				state = state,
				sleep = sleep,
				isEpisode = isEpisode,
				isLiveTv = isLive,
				onSleep = { timer ->
					sleep = timer
					toast = when (timer) {
						null -> "Sleep timer off"
						SleepTimer.EndOfVideo -> if (isEpisode) "Stopping after this episode" else "Stopping after this movie"
						is SleepTimer.At -> "Sleeping in ${timer.minutes} minutes"
					}
				},
				onQuality = { label, bitrate ->
					player.setQuality(bitrate)
					panel = null
					toast = "Quality: $label"
				},
				mirrorChoices = mirrorChoices,
				onMirror = { device, following ->
					scope.launch {
						val problem = if (following) {
							if (runCatching { mirrorApi.stop(device.SessionId) }.getOrDefault(false)) null else "Couldn’t stop mirroring ${device.title}"
						} else {
							mirrorApi.mirrorTo(device.SessionId)
						}
						toast = when {
							problem != null -> problem
							following -> "${device.title} is no longer mirroring"
							else -> "${device.title} will follow this TV"
						}
						mirrorTick++
					}
				},
				handoffDevices = handoffDevices,
				onHandoff = { device ->
					panel = null
					toast = "Sending to ${device.title}…"
					scope.launch {
						val sent = runCatching { handoffApi.sendTo(device.SessionId, player.exoPlayer.currentPosition) }.getOrDefault(false)
						if (sent) {
							toast = "Sent to ${device.title}"
							// The server tells this TV to stop; if that message is lost, leave anyway
							delay(3_000)
							if (navigator.current is Route.Player) navigator.back()
						} else {
							toast = "Couldn’t reach ${device.title}"
						}
					}
				},
			)
		}
	}

	if (goodnight) {
		GoodnightScreen(
			// Still awake: stay right here, paused, with the controls up
			onStay = {
				goodnight = false
				overlayVisible = true
			},
			// Saves your place as the player closes, then hands the TV back to its own sleep
			onLeave = { activity?.finish() ?: navigator.back() },
		)
	}
}

/**
 * The controls, laid out like a cinema app: the title's logo top left with the clock opposite,
 * then a full-width progress bar, and under it the transport in the middle (back 10, play, forward
 * 30) with picture and sound options on the left and the extras on the right.
 */
@Composable
private fun Controls(
	item: BaseItemDto,
	state: PlayerUiState,
	isLive: Boolean,
	isLiveChannel: Boolean,
	player: KhyloPlayer,
	logoUrl: String?,
	/** Khylo TV: how far it's possible to go (what's on air now); null when there's no limit. */
	liveEdgeMs: (() -> Long)?,
	nextEpisode: BaseItemDto?,
	isFavorite: Boolean?,
	sleep: SleepTimer?,
	controlsFocus: FocusRequester,
	seekFocus: FocusRequester,
	onPoke: () -> Unit,
	onPanel: (PanelTab) -> Unit,
	onFavorite: () -> Unit,
	onNext: () -> Unit,
	onMultiView: () -> Unit,
	onGuide: () -> Unit,
	recording: Boolean,
	onRecord: () -> Unit,
) {
	val clock by produceState(LocalTime.now()) {
		while (true) {
			delay(15_000)
			value = LocalTime.now()
		}
	}

	Box(Modifier.fillMaxSize()) {
		// Deep, soft shading at the top and bottom so everything reads over any picture
		Box(Modifier.fillMaxWidth().height(230.dp).background(Brush.verticalGradient(0f to Color(0xD9000000), 0.55f to Color(0x66000000), 1f to Color.Transparent)))
		Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(340.dp).background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color(0x99000000), 1f to Color(0xF2000000))))

		// ---- Top: what's playing, and the time -------------------------------------------------
		Row(Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, top = 40.dp), verticalAlignment = Alignment.Top) {
			Column(Modifier.weight(1f)) {
				if (isLive || isLiveChannel) {
					KText(
						if (isLive) "LIVE" else "KHYLO TV",
						style = KhyloType.Caption.copy(fontWeight = FontWeight.Bold).merge(KhyloType.Centered),
						color = Color.White,
						modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(KhyloColors.Live).padding(horizontal = 8.dp, vertical = 4.dp),
					)
					Spacer(Modifier.height(12.dp))
				}
				val title = if (isLive) item.currentProgram?.name ?: item.name.orEmpty() else item.seriesName ?: item.name.orEmpty()
				if (logoUrl != null && !isLive) {
					KImage(logoUrl, Modifier.height(64.dp).widthIn(max = 340.dp), ContentScale.Fit, Alignment.CenterStart)
				} else {
					KText(title, style = KhyloType.Title.copy(fontWeight = FontWeight.Bold), maxLines = 1)
				}
				val detail = when {
					isLive -> listOfNotNull(item.channelNumber, item.name).joinToString("  ·  ")
					item.type == BaseItemKind.EPISODE -> listOfNotNull(subtitleFor(item), item.name).joinToString("  ·  ")
					else -> listOfNotNull(item.productionYear?.toString(), item.officialRating).joinToString("  ·  ")
				}
				if (detail.isNotBlank()) {
					Spacer(Modifier.height(10.dp))
					KText(detail, style = KhyloType.Label, color = Color(0xD9FFFFFF), maxLines = 1)
				}
			}
			Column(horizontalAlignment = Alignment.End) {
				KText(clock.format(clockFormat), style = KhyloType.Heading.copy(fontWeight = FontWeight.Medium), color = Color.White)
				if (!isLive && state.durationMs > 0) {
					val endsAt = LocalTime.now().plusSeconds((state.durationMs - state.positionMs).coerceAtLeast(0) / 1000)
					Spacer(Modifier.height(2.dp))
					KText("Ends at ${endsAt.format(clockFormat)}", style = KhyloType.Caption, color = Color(0xB3FFFFFF))
				}
			}
		}

		// ---- Bottom: progress, then the controls -----------------------------------------------
		Column(
			Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth()
				.padding(start = 64.dp, end = 64.dp, bottom = 34.dp),
		) {
			if (!isLive && state.durationMs > 0) {
				SeekBar(player, state.positionMs, state.durationMs, Modifier.focusRequester(seekFocus), maxSeekMs = liveEdgeMs)
				Spacer(Modifier.height(20.dp))
			}

			Box(Modifier.fillMaxWidth()) {
				// Picture and sound
				Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					val subtitlesOn = state.textTracks.any { it.selected }
					PlayerAction(KIcons.Subtitles, if (subtitlesOn) "Subtitles on" else "Subtitles", active = subtitlesOn, onClick = { onPanel(PanelTab.Subtitles) })
					PlayerAction(KIcons.PlayerSettings, if (isLive) "Audio" else "Audio & quality", onClick = { onPanel(PanelTab.Audio) })
				}

				// Transport, in the middle
				Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
					if (!isLive) PlayerAction(KIcons.Back10, "Back 10s", size = 56.dp, onClick = { player.seekBy(-10_000); onPoke() })
					PlayPauseButton(state.isPlaying, Modifier.focusRequester(controlsFocus)) { player.togglePlayPause(); onPoke() }
					if (!isLive && !isLiveChannel) PlayerAction(KIcons.Forward30, "Forward 30s", size = 56.dp, onClick = { player.seekBy(30_000); onPoke() })
				}

				// The extras
				Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					PlayerAction(
						KIcons.Sleep,
						when (sleep) {
							null -> "Sleep timer"
							SleepTimer.EndOfVideo -> "Stops at end"
							is SleepTimer.At -> "Sleep in ${((sleep.wallClockMs - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)}m"
						},
						active = sleep != null,
						onClick = { onPanel(PanelTab.Sleep) },
					)
					if (isFavorite != null) {
						PlayerAction(
							if (isFavorite) KIcons.Favorited else KIcons.Favorite,
							if (isFavorite) "Favorited" else "Favorite",
							active = isFavorite,
							onClick = onFavorite,
						)
					}
					if (nextEpisode != null) PlayerAction(KIcons.NextEpisode, "Next episode", onClick = onNext)
					if (!isLive && !isLiveChannel) PlayerAction(KIcons.Tv, "Handoff", onClick = { onPanel(PanelTab.Handoff) })
					PlayerAction(KIcons.Mirror, "Mirror", onClick = { onPanel(PanelTab.Mirror) })
					if (isLive) {
						PlayerAction(KIcons.Record, if (recording) "Recording" else "Record", active = recording, onClick = onRecord)
						PlayerAction(KIcons.Grid, "Multi View", onClick = onMultiView)
						PlayerAction(KIcons.LiveTv, "Guide", onClick = onGuide)
					}
				}
			}
		}
	}
}

/**
 * A soft orange glow spreading evenly all round a round control. A normal drop shadow always falls downward, which put the
 * glow underneath the button instead of around it.
 */
private fun Modifier.orangeGlow(visible: Boolean, reach: Dp): Modifier = drawBehind {
	if (visible) {
		val radius = size.minDimension / 2 + reach.toPx()
		drawCircle(Brush.radialGradient(listOf(KhyloColors.AccentGlow, Color.Transparent), center, radius), radius)
	}
}

/** The main play / pause button: a large white disc that glows orange when focused. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, modifier: Modifier, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "playScale")
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		Box(
			modifier
				.scale(scale)
				.size(76.dp)
				.orangeGlow(focused, 30.dp)
				.clip(CircleShape)
				.background(if (focused) Color.White else Color(0xEBFFFFFF))
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			KIcon(if (isPlaying) KIcons.Pause else KIcons.Play, KhyloColors.OnFocus, Modifier.size(38.dp), if (isPlaying) "Pause" else "Play")
		}
	}
}

/**
 * A round control: a soft frosted circle at rest, a white disc with a gentle glow when focused,
 * with its name appearing underneath. [active] tints the icon (subtitles on, favorited, sleep set).
 */
@Composable
private fun PlayerAction(icon: ImageVector, label: String, active: Boolean = false, size: Dp = 50.dp, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "actionScale")
	Column(Modifier.width(size + 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		Box(
			Modifier
				.scale(scale)
				.size(size)
				.orangeGlow(focused, 20.dp)
				.clip(CircleShape)
				.background(if (focused) Color.White else Color(0x24FFFFFF))
				.border(1.dp, if (focused) Color.Transparent else Color(0x2EFFFFFF), CircleShape)
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			KIcon(
				icon,
				when {
					focused -> KhyloColors.OnFocus
					active -> KhyloColors.Accent
					else -> Color.White
				},
				Modifier.size(size * 0.5f),
				label,
			)
		}
		Spacer(Modifier.height(8.dp))
		KText(
			label,
			style = KhyloType.Caption.copy(fontWeight = FontWeight.SemiBold),
			color = if (focused) Color.White else Color.Transparent,
			maxLines = 1,
			textAlign = TextAlign.Center,
			modifier = Modifier.requiredWidth(140.dp),
		)
	}
}

/** A pill offer such as "Skip intro". Bright and pressable with OK while the controls are hidden. */
@Composable
private fun PlayerPill(label: String, icon: ImageVector?, modifier: Modifier, highlighted: Boolean = false, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	val bright = focused || highlighted
	Row(
		modifier
			.shadow(if (bright) 16.dp else 0.dp, RoundedCornerShape(50))
			.clip(RoundedCornerShape(50))
			.background(if (bright) Color.White else Color(0xB3151618))
			.border(1.dp, if (bright) Color.Transparent else Color(0x66FFFFFF), RoundedCornerShape(50))
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(start = if (icon != null) 18.dp else 24.dp, end = 24.dp)
			.height(48.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		val content = if (bright) KhyloColors.OnFocus else Color.White
		if (icon != null) KIcon(icon, content, Modifier.size(24.dp))
		KText(label, style = KhyloType.Label.copy(fontWeight = FontWeight.Bold).merge(KhyloType.Centered), color = content, maxLines = 1)
	}
}

/** During an episode's credits: the next one, with a countdown when auto-play is on. */
@Composable
private fun NextUpCard(next: BaseItemDto, countdown: Int, repository: KhyloRepository, highlighted: Boolean, onPlay: () -> Unit) {
	Row(
		Modifier
			.width(460.dp)
			.shadow(24.dp, RoundedCornerShape(16.dp))
			.clip(RoundedCornerShape(16.dp))
			.background(Color(0xF0111214))
			.border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
			.padding(14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Box(Modifier.width(170.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(KhyloColors.SurfaceRaised)) {
			repository.thumbUrl(next)?.let { KImage(it, Modifier.fillMaxSize()) }
		}
		Column(Modifier.weight(1f)) {
			KText("UP NEXT", style = KhyloType.Eyebrow, color = KhyloColors.Accent)
			Spacer(Modifier.height(4.dp))
			KText(next.name.orEmpty(), style = KhyloType.Heading, maxLines = 1)
			subtitleFor(next)?.let { KText(it, style = KhyloType.Caption, maxLines = 1) }
			Spacer(Modifier.height(10.dp))
			PlayerPill(
				if (countdown > 0) "Playing in $countdown" else "Play next",
				KIcons.Play,
				Modifier,
				highlighted = highlighted,
				onClick = onPlay,
			)
		}
	}
}

/** Subtitles, audio, quality and sleep timer, in a panel that slides in from the right. */
@Composable
private fun SettingsPanel(
	tab: PanelTab,
	onTab: (PanelTab) -> Unit,
	player: KhyloPlayer,
	state: PlayerUiState,
	sleep: SleepTimer?,
	isEpisode: Boolean,
	isLiveTv: Boolean,
	onSleep: (SleepTimer?) -> Unit,
	onQuality: (String, Int?) -> Unit,
	mirrorChoices: MirrorChoices?,
	onMirror: (org.khylo.tv.handoff.HandoffDevice, Boolean) -> Unit,
	handoffDevices: List<org.khylo.tv.handoff.HandoffDevice>?,
	onHandoff: (org.khylo.tv.handoff.HandoffDevice) -> Unit,
) {
	data class Option(val label: String, val detail: String?, val selected: Boolean, val onClick: () -> Unit)

	val options: List<Option> = when (tab) {
		PanelTab.Subtitles -> listOf(Option("Off", null, state.textTracks.none { it.selected }) { player.subtitlesOff() }) +
			state.textTracks.map { track -> Option(track.label, track.detail, track.selected) { player.selectTrack(track) } }
		PanelTab.Audio -> state.audioTracks.map { track -> Option(track.label, track.detail, track.selected) { player.selectTrack(track) } }
		PanelTab.Quality -> {
			// Only choices below the file's own bitrate change anything; higher ones are the original
			val source = state.stream?.sourceBitrate
			val original = listOfNotNull(state.stream?.resolution, source?.let { "%.1f Mbps".format(it / 1_000_000.0) }).joinToString(" · ")
			listOf(Option("Original quality", original.ifEmpty { null }, player.currentMaxBitrate == null || (source != null && player.currentMaxBitrate!! >= source)) { onQuality("Original", null) }) +
				KhyloPrefs.qualityOptions
					.filter { (_, bitrate) -> bitrate != null && (source == null || bitrate < source) }
					.map { (label, bitrate) ->
						Option(label, "The server converts it to fit", player.currentMaxBitrate == bitrate) { onQuality(label, bitrate) }
					}
		}
		PanelTab.Handoff -> when {
			handoffDevices == null -> listOf(Option("Looking for your devices…", null, false) {})
			handoffDevices.isEmpty() -> listOf(Option("No other devices found", "Open Khylo on your phone or tablet, signed in as you", false) {})
			else -> handoffDevices.map { device ->
				Option(device.title, device.playing?.let { "Playing: $it — this will replace it" } ?: "Continue here from this moment", false) { onHandoff(device) }
			}
		}
		PanelTab.Mirror -> when {
			mirrorChoices == null -> listOf(Option("Looking for your devices…", null, false) {})
			mirrorChoices.devices.isEmpty() -> listOf(Option("No other devices found", "Open Khylo on your other TVs, signed in as you", false) {})
			else -> mirrorChoices.devices.map { device ->
				val following = device.SessionId in mirrorChoices.following
				Option(
					device.title,
					if (following) "Following this TV · press to stop" else "Press to show what this TV is playing",
					following,
				) { onMirror(device, following) }
			}
		}
		PanelTab.Sleep -> listOf(Option("Off", null, sleep == null) { onSleep(null) }) +
			listOf(15, 30, 45, 60, 90).map { minutes ->
				val selected = (sleep as? SleepTimer.At)?.minutes == minutes
				val left = (sleep as? SleepTimer.At)?.let { ((it.wallClockMs - System.currentTimeMillis()) / 60_000).coerceAtLeast(1) }
				Option("$minutes minutes", if (selected) "$left min left" else null, selected) {
					onSleep(SleepTimer.At(System.currentTimeMillis() + minutes * 60_000L, minutes))
				}
			} +
			Option(if (isEpisode) "End of this episode" else "End of this movie", null, sleep == SleepTimer.EndOfVideo) { onSleep(SleepTimer.EndOfVideo) }
	}

	val selectedFocus = remember { FocusRequester() }
	// Whether the remote's focus is anywhere in this panel (the tabs or the list)
	var panelFocused by remember { mutableStateOf(false) }
	// Focus starts on the chosen option when the panel opens
	LaunchedEffect(Unit) {
		delay(60)
		runCatching { selectedFocus.requestFocus() }
	}
	val focusIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
	// The list can change under the remote: "Looking for your devices" is replaced by the real devices a moment after the panel
	// opens, and that removes the row that had focus. Nothing was then selectable. When the list changes and the panel has lost
	// focus altogether, put it back on the chosen row (but never take it from the tabs while someone is moving along them)
	val optionsKey = options.joinToString("|") { it.label }
	LaunchedEffect(tab, optionsKey) {
		repeat(8) {
			delay(80)
			if (panelFocused) return@LaunchedEffect
			runCatching { selectedFocus.requestFocus() }
		}
	}

	Box(Modifier.fillMaxSize()) {
		Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.4f to Color.Transparent, 1f to Color(0x99000000))))
		Column(
			Modifier
				.align(Alignment.CenterEnd)
				.fillMaxHeight()
				.width(440.dp)
				.background(Color(0xF20C0D0F))
				.onFocusChanged { panelFocused = it.hasFocus }
				.padding(start = 32.dp, end = 32.dp, top = 40.dp, bottom = 24.dp),
		) {
			KText("Playback", style = KhyloType.Title)
			Spacer(Modifier.height(14.dp))
			Row {
				PanelTab.entries
					// Live TV arrives at the provider's quality; converting it would only cost the server
					.filter { (it != PanelTab.Quality && it != PanelTab.Handoff) || !isLiveTv }
					.forEach { entry ->
						// Moving along the tabs shows each one straight away
						KTab(entry.label, entry == tab, { onTab(entry) }, Modifier.onFocusChanged { if (it.hasFocus && entry != tab) onTab(entry) })
					}
			}
			Spacer(Modifier.height(12.dp))
			if (tab == PanelTab.Quality) {
				state.stream?.let { stream ->
					KText(
						if (stream.direct) "Now playing the original file" else if (stream.maxBitrate != null) "Now converted by the server to ${"%.1f".format(stream.maxBitrate / 1_000_000.0)} Mbps or less" else "Now repackaged by the server",
						style = KhyloType.Caption,
						modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
					)
				}
			}
			if (options.isEmpty()) {
				KText("This video has only one audio track.", style = KhyloType.Body, modifier = Modifier.padding(top = 8.dp))
			}
			LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				itemsIndexed(options, key = { index, option -> "$tab-$index-${option.label}" }) { index, option ->
					OptionRow(
						option.label,
						option.detail,
						option.selected,
						if (index == focusIndex) Modifier.focusRequester(selectedFocus) else Modifier,
						option.onClick,
					)
				}
			}
		}
	}
}

@Composable
private fun OptionRow(label: String, detail: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(10.dp))
			.background(if (focused) Color.White else Color.Transparent)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(Modifier.weight(1f)) {
			KText(
				label,
				style = KhyloType.Label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
				color = if (focused) KhyloColors.OnFocus else KhyloColors.Text,
				maxLines = 1,
			)
			detail?.let { KText(it, style = KhyloType.Caption, color = if (focused) Color(0xFF555555) else KhyloColors.TextMuted, maxLines = 1) }
		}
		if (selected) KIcon(KIcons.Check, if (focused) KhyloColors.OnFocus else KhyloColors.Accent, Modifier.size(22.dp))
	}
}

/**
 * The progress bar, which you can also drive: highlight it (Up from the buttons), then Left/Right
 * moves through the video (faster the longer you hold) and the jump happens when you stop.
 */
@Composable
private fun SeekBar(player: KhyloPlayer, positionMs: Long, durationMs: Long, modifier: Modifier = Modifier, maxSeekMs: (() -> Long)? = null) {
	val (source, focused) = rememberFocusState()
	var scrubMs by remember { mutableStateOf<Long?>(null) }
	var lastPress by remember { mutableLongStateOf(0L) }
	// Jump once the presses stop
	LaunchedEffect(scrubMs, lastPress) {
		val target = scrubMs ?: return@LaunchedEffect
		delay(700)
		player.seekTo(target)
		scrubMs = null
	}
	LaunchedEffect(focused) { if (!focused) scrubMs = null }

	val shown = scrubMs ?: positionMs
	val fraction = (shown.toFloat() / durationMs).coerceIn(0f, 1f)
	Column(
		modifier
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				val direction = when (event.key) {
					Key.DirectionLeft -> -1
					Key.DirectionRight -> 1
					Key.DirectionCenter, Key.Enter -> {
						scrubMs?.let { player.seekTo(it) }
						scrubMs = null
						return@onPreviewKeyEvent true
					}
					else -> return@onPreviewKeyEvent false
				}
				// 10 seconds a press; holding speeds up to 30 seconds, a minute, then two
				val repeat = event.nativeKeyEvent.repeatCount
				val step = when {
					repeat > 30 -> 120_000L
					repeat > 10 -> 60_000L
					repeat > 3 -> 30_000L
					else -> 10_000L
				}
				// On Khylo TV the bar stops at what's on air now
				val limit = maxSeekMs?.invoke()?.coerceAtMost(durationMs) ?: durationMs
				scrubMs = ((scrubMs ?: positionMs) + direction * step).coerceIn(0, limit.coerceAtLeast(positionMs))
				lastPress = System.currentTimeMillis()
				true
			}
			.focusable(interactionSource = source),
	) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
			KText(formatTime(shown), style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), color = if (scrubMs != null) KhyloColors.Accent else Color.White)
			BoxWithConstraints(Modifier.weight(1f).height(22.dp), contentAlignment = Alignment.CenterStart) {
				val barHeight = if (focused) 7.dp else 4.dp
				Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(4.dp)).background(Color(0x2EFFFFFF))) {
					// Where playback is, and (while scrubbing) where it will jump to
					Box(
						Modifier
							.fillMaxWidth((positionMs.toFloat() / durationMs).coerceIn(0f, 1f))
							.height(barHeight)
							.background(if (scrubMs != null) Color(0x66FFFFFF) else Color.White)
					)
					if (scrubMs != null) Box(Modifier.fillMaxWidth(fraction).height(barHeight).background(KhyloColors.Accent))
				}
				if (focused) {
					Box(
						Modifier
							.offset(x = (maxWidth * fraction - 9.dp).coerceAtLeast(0.dp))
							.size(18.dp)
							.shadow(6.dp, CircleShape)
							.clip(CircleShape)
							.background(Color.White)
					)
				}
			}
			KText("-" + formatTime((durationMs - shown).coerceAtLeast(0)), style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), color = Color(0xB3FFFFFF))
		}
	}
}
