package org.khylo.tv.core.integration.dream.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.khylo.tv.core.ui.composable.modifier.overscan
import org.khylo.tv.core.ui.shared.toolbar.ToolbarClock

@Composable
fun DreamHeader(
	showClock: Boolean,
) = Box(
	modifier = Modifier
		.fillMaxWidth()
		.overscan(),
) {
	// Clock
	AnimatedVisibility(
		visible = showClock,
		enter = fadeIn(),
		exit = fadeOut(),
		modifier = Modifier
			.align(Alignment.TopEnd),
	) {
		ToolbarClock()
	}
}
