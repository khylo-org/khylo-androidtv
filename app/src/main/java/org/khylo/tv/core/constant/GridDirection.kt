package org.khylo.tv.core.constant

import org.khylo.tv.core.R
import org.khylo.tv.preference.PreferenceEnum

enum class GridDirection(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * Horizontal.
	 */
	HORIZONTAL(R.string.grid_direction_horizontal),

	/**
	 * Vertical.
	 */
	VERTICAL(R.string.grid_direction_vertical),
}
