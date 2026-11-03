package org.khylo.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloType

/** How long the sound fades before a sleep timer ends. */
const val SLEEP_FADE_MS = 30_000L

/** How long the Goodnight screen waits for a button before Khylo closes. */
private const val GOODNIGHT_SECONDS = 10

/**
 * Fades the player's sound out over [durationMs], then pauses it and puts the volume back, so the
 * next play isn't silent.
 */
suspend fun fadeOutAndPause(player: Player, durationMs: Long = SLEEP_FADE_MS) {
	val steps = 60
	val start = player.volume
	try {
		for (step in 1..steps) {
			player.volume = start * (1f - step.toFloat() / steps)
			delay(durationMs / steps)
		}
		player.pause()
	} finally {
		// Also when the timer is turned off mid-fade
		player.volume = start
	}
}

/**
 * Shown when a sleep timer ends: a near-black screen that says goodnight. Any button keeps
 * watching ([onStay]); otherwise after a few seconds Khylo closes ([onLeave]) and hands the TV
 * back to its own sleep settings. Apps can't switch a TV off themselves, so this is as close as
 * it gets.
 */
@Composable
fun GoodnightScreen(onStay: () -> Unit, onLeave: () -> Unit) {
	var secondsLeft by remember { mutableIntStateOf(GOODNIGHT_SECONDS) }
	var visible by remember { mutableStateOf(false) }
	val focus = remember { FocusRequester() }
	LaunchedEffect(Unit) {
		visible = true
		runCatching { focus.requestFocus() }
		while (secondsLeft > 0) {
			delay(1_000)
			secondsLeft--
		}
		onLeave()
	}

	Dialog(onDismissRequest = onStay, properties = DialogProperties(usePlatformDefaultWidth = false)) {
		Box(
			Modifier
				.fillMaxSize()
				.background(Color(0xF5000000))
				.focusRequester(focus)
				.focusable()
				.onPreviewKeyEvent { event ->
					if (event.type == KeyEventType.KeyUp) onStay()
					true
				},
			contentAlignment = Alignment.Center,
		) {
			AnimatedVisibility(visible = visible, enter = fadeIn(tween(1_200))) {
				Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
					KText("Goodnight", style = KhyloType.Display, color = Color(0xCCFFFFFF))
					KText("Sleep timer ended. Press any button to keep watching.", style = KhyloType.Body, color = KhyloColors.TextSecondary)
					KText("Closing in $secondsLeft", style = KhyloType.Caption, color = KhyloColors.TextMuted)
				}
			}
			KText(
				"Your TV turns its screen off on its own after a while. To make that sooner, go to Settings → System → Energy → Turn off display.",
				style = KhyloType.Caption,
				color = Color(0x66FFFFFF),
				textAlign = TextAlign.Center,
				modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 160.dp, vertical = 40.dp),
			)
		}
	}
}
