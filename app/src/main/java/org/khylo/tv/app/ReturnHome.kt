package org.khylo.tv.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Away this long (with the TV still on), Khylo also starts again on Home. */
private const val AWAY_LIMIT_MS = 30 * 60 * 1000L

/**
 * Turning the TV off with Khylo open only puts it to sleep, so it used to wake straight back into the
 * paused player or the page it was on. Coming back after the screen went off, or after a long while
 * away, now starts on Home instead. Where you stopped is still saved, so Continue Watching picks it up.
 */
@Composable
fun ReturnHomeAfterSleep(navigator: Navigator) {
	val context = LocalContext.current
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	DisposableEffect(lifecycle, navigator) {
		var leftAt = 0L
		var screenWentOff = false

		val screenOff = object : BroadcastReceiver() {
			override fun onReceive(context: Context?, intent: Intent?) {
				screenWentOff = true
			}
		}
		ContextCompat.registerReceiver(context, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)

		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_STOP -> {
					leftAt = SystemClock.elapsedRealtime()
					screenWentOff = false
				}
				Lifecycle.Event.ON_START -> {
					val away = leftAt > 0 && SystemClock.elapsedRealtime() - leftAt >= AWAY_LIMIT_MS
					if ((screenWentOff || away) && navigator.current != Route.Home) navigator.openSection(Route.Home)
					leftAt = 0L
					screenWentOff = false
				}
				else -> Unit
			}
		}
		lifecycle.addObserver(observer)

		onDispose {
			lifecycle.removeObserver(observer)
			context.unregisterReceiver(screenOff)
		}
	}
}
