package org.khylo.tv.player

import android.content.Context
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.model.api.DeviceProfile
import org.koin.compose.koinInject

/** Makes players that share the app's server connection and playback capabilities. */
class KhyloPlayerFactory(
	private val context: Context,
	private val api: ApiClient,
	private val httpClient: OkHttpClient,
	private val deviceProfile: () -> DeviceProfile,
) {
	fun create() = KhyloPlayer(context, api, httpClient, deviceProfile)
}

/** A player tied to the screen showing it: released (and the server told) when the screen goes. */
@Composable
fun rememberKhyloPlayer(key: Any? = null): KhyloPlayer {
	val factory = koinInject<KhyloPlayerFactory>()
	val player = remember(key) { factory.create() }
	DisposableEffect(player) {
		onDispose { player.release() }
	}
	PauseWhenAppLeaves(player.exoPlayer)
	return player
}

/**
 * Pauses playback when the app leaves the screen (Home button, another app, the TV sleeping), so
 * nothing keeps playing in the background. Coming back finds it paused where it stopped.
 */
@Composable
fun PauseWhenAppLeaves(player: androidx.media3.common.Player) {
	val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
	DisposableEffect(lifecycle, player) {
		val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
			if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause()
		}
		lifecycle.addObserver(observer)
		onDispose { lifecycle.removeObserver(observer) }
	}
}

/** The picture. Khylo draws its own controls on top, so the stock ones are off. */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: KhyloPlayer, modifier: Modifier = Modifier) {
	// Keeps the TV from dimming or starting its screensaver while video plays
	val state = player.state.collectAsState().value
	val playing = state.isPlaying
	// A fresh view per stream: switching to a stream of another size could leave the old frame
	// showing around a wrongly sized new one
	androidx.compose.runtime.key(state.streamGeneration) {
		AndroidView(
			modifier = modifier,
			factory = { context ->
				PlayerView(context).apply {
					layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
					useController = false
					resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
					setShutterBackgroundColor(android.graphics.Color.BLACK)
					isFocusable = false
					this.player = player.exoPlayer
				}
			},
			update = { view ->
				view.player = player.exoPlayer
				view.keepScreenOn = playing
			},
			onRelease = { view ->
				view.keepScreenOn = false
				view.player = null
			},
		)
	}
}
