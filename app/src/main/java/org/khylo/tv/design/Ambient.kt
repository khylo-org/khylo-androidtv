package org.khylo.tv.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Artwork behind the page: a backdrop is shown as it is, anything else (a poster, a cover) softened. */
data class AmbientArt(val url: String, val isBackdrop: Boolean)

/**
 * The page's backdrop, shared app-wide. Whatever you're on (a card, a category, a row) reports its
 * artwork here, and the shell draws it large and dimmed behind the screen, so no page sits on flat
 * black.
 */
class AmbientState {
	var art by mutableStateOf<AmbientArt?>(null)
		private set

	fun show(url: String?, isBackdrop: Boolean) {
		if (url != null) art = AmbientArt(url, isBackdrop)
	}

	fun clear() {
		art = null
	}
}

val LocalAmbient = staticCompositionLocalOf<AmbientState?> { null }

/**
 * Draws the shared backdrop. With nothing to show it rests on a deep charcoal with a soft light
 * from the top left; artwork fades in over it, slowly, and only once you settle on something.
 */
@Composable
fun AmbientBackground(state: AmbientState) {
	// Wait for focus to settle, so moving quickly across a row doesn't flicker artwork
	var shown by remember { mutableStateOf<AmbientArt?>(null) }
	LaunchedEffect(state.art) {
		if (state.art != null) delay(280)
		shown = state.art
	}

	Box(Modifier.fillMaxSize()) {
		// The resting look: never flat black
		Box(
			Modifier.fillMaxSize().background(
				Brush.radialGradient(
					0f to Color(0xFF23262D),
					0.55f to Color(0xFF111316),
					1f to KhyloColors.Background,
					center = Offset(0f, 0f),
					radius = 2200f,
				),
			),
		)
		Box(
			Modifier.fillMaxSize().background(
				Brush.radialGradient(
					0f to KhyloColors.Accent.copy(alpha = 0.07f),
					1f to Color.Transparent,
					center = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
					radius = 1400f,
				),
			),
		)

		AnimatedContent(
			targetState = shown,
			transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
			label = "ambient",
		) { art ->
			Box(Modifier.fillMaxSize()) {
				when {
					art == null -> Unit
					art.isBackdrop -> KImage(art.url, Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }, ContentScale.Crop, Alignment.TopCenter)
					// Posters and covers become a soft wash of their colors
					else -> KImage(
						art.url,
						Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f; scaleX = 1.25f; scaleY = 1.25f }.blur(48.dp),
						ContentScale.Crop,
					)
				}
			}
		}

		// Shade the top (for the bar), the left and the bottom, so rows and text always read
		Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xCC000000), 0.25f to Color(0x80090A0B), 0.7f to Color(0xD9090A0B), 1f to Color(0xF2090A0B))))
		Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xA6090A0B), 0.6f to Color.Transparent)))
	}
}
