package org.khylo.tv.listen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.player.formatTime

/** A round control for the listening screens; quiet until focused, then a filled disc. */
@Composable
fun ListenButton(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	size: Dp = 52.dp,
	primary: Boolean = false,
	active: Boolean = false,
	accent: Color = Color.White,
	discColor: Color = Color.White,
) {
	val (source, focused) = rememberFocusState()
	val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "listenScale")
	Column(modifier.width(size + 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		Box(
			Modifier
				.scale(scale)
				.size(size)
				.shadow(if (primary || focused) 16.dp else 0.dp, CircleShape)
				.clip(CircleShape)
				.background(
					when {
						primary -> discColor
						focused -> Color.White
						else -> Color.Transparent
					}
				)
				// (No border at all unless focused: a zero-width border still draws a hairline)
				.then(if (primary && focused) Modifier.border(3.dp, accent, CircleShape) else Modifier)
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			KIcon(
				icon,
				when {
					primary || focused -> Color(0xFF0B0B0C)
					active -> accent
					else -> Color(0xE6FFFFFF)
				},
				Modifier.size(size * 0.5f),
				label,
			)
		}
		Spacer(Modifier.height(6.dp))
		KText(
			label,
			style = KhyloType.Caption.copy(fontWeight = FontWeight.SemiBold),
			color = if (focused) Color.White else Color.Transparent,
			maxLines = 1,
			textAlign = TextAlign.Center,
			modifier = Modifier.width(140.dp),
		)
	}
}

/**
 * The progress bar for audio. Focus it, then Left/Right moves through (faster when held) and it
 * jumps when you stop pressing.
 */
@Composable
fun ListenSeekBar(
	positionMs: Long,
	durationMs: Long,
	accent: Color,
	onSeek: (Long) -> Unit,
	modifier: Modifier = Modifier,
	trackColor: Color = Color(0x33FFFFFF),
) {
	val (source, focused) = rememberFocusState()
	var scrubMs by remember { mutableStateOf<Long?>(null) }
	var lastPress by remember { mutableLongStateOf(0L) }
	LaunchedEffect(scrubMs, lastPress) {
		val target = scrubMs ?: return@LaunchedEffect
		delay(600)
		onSeek(target)
		scrubMs = null
	}
	val shown = scrubMs ?: positionMs
	val fraction = if (durationMs > 0) (shown.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
	Column(
		modifier
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown || durationMs <= 0) return@onPreviewKeyEvent false
				val direction = when (event.key) {
					Key.DirectionLeft -> -1
					Key.DirectionRight -> 1
					else -> return@onPreviewKeyEvent false
				}
				val repeat = event.nativeKeyEvent.repeatCount
				val step = when {
					repeat > 20 -> 60_000L
					repeat > 6 -> 30_000L
					else -> 10_000L
				}
				scrubMs = ((scrubMs ?: positionMs) + direction * step).coerceIn(0, durationMs)
				lastPress = System.currentTimeMillis()
				true
			}
			.focusable(interactionSource = source),
	) {
		BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
			val bar = if (focused) 7.dp else 4.dp
			Box(Modifier.fillMaxWidth().height(bar).clip(RoundedCornerShape(4.dp)).background(trackColor)) {
				Box(Modifier.fillMaxWidth(fraction).height(bar).background(accent))
			}
			if (focused) {
				Box(
					Modifier
						.offset(x = (maxWidth * fraction - 8.dp).coerceAtLeast(0.dp))
						.size(16.dp)
						.shadow(6.dp, CircleShape)
						.clip(CircleShape)
						.background(Color.White)
				)
			}
		}
		Spacer(Modifier.height(6.dp))
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
			KText(formatTime(shown), style = KhyloType.Caption, color = if (scrubMs != null) accent else Color(0xB3FFFFFF))
			KText("-" + formatTime((durationMs - shown).coerceAtLeast(0)), style = KhyloType.Caption, color = Color(0xB3FFFFFF))
		}
	}
}

/** Hours and minutes, for "3h 35m left". */
fun formatLong(ms: Long): String {
	val minutes = ms / 60_000
	return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/** A small text pill used for speed / sleep settings on the audiobook screen. */
@Composable
fun ListenPill(text: String, icon: ImageVector?, onClick: () -> Unit, accent: Color, active: Boolean = false) {
	val (source, focused) = rememberFocusState()
	Row(
		Modifier
			.clip(RoundedCornerShape(50))
			.background(if (focused) Color.White else Color(0x1FFFFFFF))
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 9.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		val tint = if (focused) Color(0xFF0B0B0C) else if (active) accent else Color.White
		if (icon != null) KIcon(icon, tint, Modifier.size(18.dp))
		KText(text, style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold).merge(KhyloType.Centered), color = tint, maxLines = 1)
	}
}
