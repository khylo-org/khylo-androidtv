package org.khylo.tv.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.khylo.tv.core.R
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloType

private const val TAPS_TO_UNLOCK = 7

/**
 * Counts presses on the build number, like Android's "tap 7 times to become a developer". After a
 * few presses it counts down; on the seventh, Klyrp turns up (a nod to Rick and Morty).
 */
class KlyrpCounter {
	var taps by mutableIntStateOf(0)
		private set
	var showing by mutableStateOf(false)
	var hint by mutableStateOf<String?>(null)
		private set

	fun tap() {
		taps++
		val left = TAPS_TO_UNLOCK - taps
		when {
			left <= 0 -> {
				taps = 0
				hint = null
				showing = true
			}
			taps >= 4 -> hint = if (left == 1) "You are 1 step away from being a developer…" else "You are $left steps away from being a developer…"
		}
	}

	fun clearHint() {
		hint = null
	}
}

/** Klyrp, our alien, rising from the corner to ask "DO YOU WANT TO DEVELOP AN APP?". A second press sends him off. */
@Composable
fun KlyrpVisit(counter: KlyrpCounter) {
	if (!counter.showing) return
	var visible by remember { mutableStateOf(false) }
	var insisted by remember { mutableStateOf(false) }
	val focus = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		visible = true
		runCatching { focus.requestFocus() }
	}
	// He leaves on his own after a while
	LaunchedEffect(insisted) {
		delay(if (insisted) 4000 else 9000)
		visible = false
	}
	LaunchedEffect(visible) {
		if (!visible) {
			delay(450)
			counter.showing = false
		}
	}

	Dialog(onDismissRequest = { visible = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
		Box(
			Modifier
				.fillMaxSize()
				.focusRequester(focus)
				.focusable()
				.onPreviewKeyEvent { event ->
					if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
					val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
					if (isOk && !insisted) insisted = true else visible = false
					true
				},
		) {
			AnimatedVisibility(
				visible = visible,
				enter = fadeIn(tween(300)),
				exit = fadeOut(tween(300)),
			) {
				Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xB3000000))))
			}
			AnimatedVisibility(
				visible = visible,
				enter = slideInVertically(tween(420)) { it } + fadeIn(tween(200)),
				exit = slideOutVertically(tween(380)) { it } + fadeOut(tween(300)),
				modifier = Modifier.align(Alignment.BottomEnd),
			) {
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 40.dp)) {
					// His speech bubble
					KText(
						"DO YOU WANT TO DEVELOP AN APP?",
						style = KhyloType.Heading.copy(fontWeight = FontWeight.Bold),
						color = Color(0xFF141414),
						modifier = Modifier
							.widthIn(max = 360.dp)
							.background(Color(0xFFF7F1E8), RoundedCornerShape(18.dp))
							.padding(horizontal = 22.dp, vertical = 16.dp),
					)
					Image(
						painterResource(R.drawable.klyrp),
						contentDescription = "Klyrp",
						modifier = Modifier.height(340.dp).offset(y = 24.dp),
					)
				}
			}
		}
	}
}
