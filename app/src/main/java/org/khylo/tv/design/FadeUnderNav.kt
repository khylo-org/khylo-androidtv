package org.khylo.tv.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp

/**
 * For pages that scroll beneath the top bar: whatever scrolls up under the bar fades away softly,
 * so titles and buttons never sit on top of the bar's tabs.
 */
fun Modifier.fadeUnderTopNav(): Modifier = this
	.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
	.drawWithContent {
		drawContent()
		val clear = KhyloSpacing.NavHeight.toPx() * 0.6f
		val solid = (KhyloSpacing.NavHeight + 28.dp).toPx()
		drawRect(
			brush = Brush.verticalGradient(0f to Color.Transparent, clear / size.height to Color.Transparent, solid / size.height to Color.Black),
			blendMode = BlendMode.DstIn,
		)
	}
