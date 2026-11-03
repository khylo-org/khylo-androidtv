package org.khylo.tv.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Khylo TV's palette. Mostly black and white like the best TV apps; Khylo orange is kept for the
 * few things that should stand out: progress, live and the brand mark.
 */
object KhyloColors {
	val Background = Color(0xFF08090A)
	val Surface = Color(0xFF141517)
	val SurfaceRaised = Color(0xFF1E1F22)
	val SurfaceHover = Color(0xFF2A2B2F)
	val Border = Color(0x1AFFFFFF)
	val BorderStrong = Color(0x33FFFFFF)
	val Text = Color(0xFFFFFFFF)
	val TextSecondary = Color(0xFFB3B3B3)
	val TextMuted = Color(0xFF808080)
	val Accent = Color(0xFFD97732)
	val AccentHover = Color(0xFFE8853E)
	val AccentGlow = Color(0x66D97732)
	val Live = Color(0xFFE5484D)
	val Success = Color(0xFF34D399)

	/** Focus: white, like Netflix, Plex and Google TV. */
	val Focus = Color(0xFFFFFFFF)
	val OnFocus = Color(0xFF0B0B0C)
	val Glass = Color(0x29FFFFFF)
}

/** Type scale for a TV seen from the couch (1280 x 720 canvas). */
object KhyloType {
	private val family = FontFamily.SansSerif

	val Display = TextStyle(fontFamily = family, fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, lineHeight = 46.sp, color = KhyloColors.Text)
	val Title = TextStyle(fontFamily = family, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = KhyloColors.Text)
	val Heading = TextStyle(fontFamily = family, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = KhyloColors.Text)
	val Body = TextStyle(fontFamily = family, fontSize = 15.sp, fontWeight = FontWeight.Normal, color = KhyloColors.TextSecondary, lineHeight = 22.sp)
	val Label = TextStyle(fontFamily = family, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = KhyloColors.Text)
	val Meta = TextStyle(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = KhyloColors.TextSecondary)
	val Caption = TextStyle(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = KhyloColors.TextMuted)
	/**
	 * Add to text inside buttons and tabs: trims the font's extra space above and below so the
	 * words sit exactly in the middle of the control.
	 */
	val Centered = TextStyle(
		lineHeight = 1.2.em,
		lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
			alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
			trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
		),
		platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
	)
	val Eyebrow = TextStyle(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = KhyloColors.TextSecondary)
}

object KhyloSpacing {
	/** Distance from the screen edge; TVs crop the outer few percent. */
	val Gutter = 64.dp

	/** Height of the top navigation bar; screens below it start this far down. */
	val NavHeight = 92.dp
}

@Composable
fun KText(
	text: String,
	modifier: Modifier = Modifier,
	style: TextStyle = KhyloType.Body,
	color: Color = Color.Unspecified,
	fontSize: TextUnit = TextUnit.Unspecified,
	maxLines: Int = Int.MAX_VALUE,
	textAlign: TextAlign? = null,
) {
	BasicText(
		text = text,
		modifier = modifier,
		style = style.merge(
			TextStyle(
				color = if (color != Color.Unspecified) color else style.color,
				fontSize = fontSize,
				textAlign = textAlign ?: TextAlign.Unspecified,
			)
		),
		maxLines = maxLines,
		overflow = TextOverflow.Ellipsis,
	)
}
