package org.khylo.tv.core.preference.constant

import org.khylo.tv.core.R
import org.khylo.tv.preference.PreferenceEnum

enum class AppTheme(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * The default dark theme
	 */
	DARK(R.string.pref_theme_dark),

	/**
	 * The "classic" emerald theme
	 */
	EMERALD(R.string.pref_theme_emerald),

	/**
	 * A theme with a more muted accent color, inspired by CTalvio's Monochromic CSS theme for Khylo Web
	 */
	MUTED_PURPLE(R.string.pref_theme_muted_purple),
}
