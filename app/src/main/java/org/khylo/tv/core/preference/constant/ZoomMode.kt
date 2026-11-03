package org.khylo.tv.core.preference.constant

import org.khylo.tv.core.R
import org.khylo.tv.preference.PreferenceEnum

enum class ZoomMode(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * Sets the zoom mode to normal (fit).
	 */
	FIT(R.string.lbl_fit),

	/**
	 * Sets the zoom mode to auto crop.
	 */
	AUTO_CROP(R.string.lbl_auto_crop),

	/**
	 * Sets the zoom mode to stretch.
	 */
	STRETCH(R.string.lbl_stretch),
}

