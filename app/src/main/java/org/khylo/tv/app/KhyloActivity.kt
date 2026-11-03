package org.khylo.tv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import okhttp3.OkHttpClient
import org.khylo.tv.core.R
import org.khylo.tv.core.auth.repository.ServerRepository
import org.khylo.tv.core.auth.repository.SessionRepository
import org.khylo.tv.core.auth.repository.SessionRepositoryState
import org.khylo.tv.core.auth.repository.UserRepository
import org.khylo.tv.core.data.eventhandling.SocketHandler
import org.khylo.tv.core.preference.UserPreferences
import org.khylo.tv.core.util.profile.createDeviceProfile
import org.khylo.sdk.api.client.HttpClientOptions
import org.khylo.sdk.api.okhttp.OkHttpFactory
import org.khylo.sdk.model.ServerVersion
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.player.KhyloPlayerFactory
import org.khylo.tv.startup.StartupFlow
import org.koin.android.ext.koin.androidContext
import org.koin.compose.koinInject
import org.koin.dsl.module

/** Khylo's own services, alongside the shared server/session plumbing. */
val khyloModule = module {
	single { KhyloRepository(get()) }
	single { org.khylo.tv.livechannels.LiveChannelsApi(get(), get()) }
	single<OkHttpClient> { get<OkHttpFactory>().createClient(get<HttpClientOptions>()) }
	single {
		val context = androidContext()
		val preferences = get<UserPreferences>()
		val servers = get<ServerRepository>()
		KhyloPlayerFactory(context, get(), get()) {
			val version = servers.currentServer.value?.serverVersion ?: ServerVersion(10, 11, 0)
			createDeviceProfile(context, preferences, version)
		}
	}
}

/** The one screen of Khylo TV: sign-in until there's a session, then the app. */
class KhyloActivity : ComponentActivity() {
	/** The Khylo intro plays once each time the app starts; any button skips it. */
	private var introPlaying by androidx.compose.runtime.mutableStateOf(false)

	override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
		// A keyboard's Esc works like the remote's Back (keyboards on the TV, scrcpy, the emulator)
		if (event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE && !introPlaying) {
			if (event.action == android.view.KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
			return true
		}
		if (introPlaying) {
			if (event.action == android.view.KeyEvent.ACTION_UP) introPlaying = false
			return true
		}
		return super.dispatchKeyEvent(event)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		introPlaying = savedInstanceState == null
		setContent {
			// Khylo TV is laid out on a 1280 x 720 canvas, whatever the TV's resolution
			val base = androidx.compose.ui.platform.LocalDensity.current
			val config = androidx.compose.ui.platform.LocalConfiguration.current
			val scale = config.screenWidthDp / 1280f
			androidx.compose.runtime.CompositionLocalProvider(
				androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * scale, base.fontScale)
			) {
				Box(Modifier.fillMaxSize()) {
					// The app loads underneath while the intro plays
					KhyloRoot(onExit = ::finish)
					androidx.compose.animation.AnimatedVisibility(
						visible = introPlaying,
						enter = androidx.compose.animation.EnterTransition.None,
						exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500)),
					) {
						IntroVideo(onDone = { introPlaying = false })
					}
				}
			}
		}
	}
}

@Composable
private fun KhyloRoot(onExit: () -> Unit) {
	val sessionRepository = koinInject<SessionRepository>()
	val userRepository = koinInject<UserRepository>()
	val socketHandler = koinInject<SocketHandler>()

	val state by sessionRepository.state.collectAsState()
	val session by sessionRepository.currentSession.collectAsState()
	val user by userRepository.currentUser.collectAsState()

	Box(Modifier.fillMaxSize().background(KhyloColors.Background)) {
		when {
			state != SessionRepositoryState.READY -> Splash()
			session != null && user == null -> Splash()
			session != null -> {
				// Live updates from the server (remote control, refreshed libraries)
				LaunchedEffect(session) { socketHandler.updateSession() }
				// A new profile starts the app fresh, on its own Home
				androidx.compose.runtime.key(session?.userId) { KhyloShell(onExit = onExit) }
			}
			else -> StartupFlow(onExit = onExit)
		}
	}
}

@Composable
fun Splash() {
	Box(Modifier.fillMaxSize().background(KhyloColors.Background), contentAlignment = Alignment.Center) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Image(painterResource(R.drawable.app_logo), contentDescription = "Khylo", modifier = Modifier.width(320.dp))
			Spacer(Modifier.height(32.dp))
			org.khylo.tv.design.KSpinner()
		}
	}
}

/** Plays the Khylo intro full screen, then calls [onDone]. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun IntroVideo(onDone: () -> Unit) {
	val context = androidx.compose.ui.platform.LocalContext.current
	val player = androidx.compose.runtime.remember {
		androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
			setMediaItem(androidx.media3.common.MediaItem.fromUri(androidx.media3.datasource.RawResourceDataSource.buildRawResourceUri(R.raw.khylo_intro)))
			prepare()
			playWhenReady = true
		}
	}
	androidx.compose.runtime.DisposableEffect(player) {
		val listener = object : androidx.media3.common.Player.Listener {
			override fun onPlaybackStateChanged(playbackState: Int) {
				if (playbackState == androidx.media3.common.Player.STATE_ENDED) onDone()
			}
			override fun onPlayerError(error: androidx.media3.common.PlaybackException) = onDone()
		}
		player.addListener(listener)
		onDispose {
			player.removeListener(listener)
			player.release()
		}
	}
	Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
		androidx.compose.ui.viewinterop.AndroidView(
			factory = { ctx -> android.view.SurfaceView(ctx).also { player.setVideoSurfaceView(it) } },
			modifier = Modifier.fillMaxSize(),
		)
	}
}
