package org.khylo.tv.core.ui.itemhandling

import android.content.Context
import org.khylo.tv.core.constant.ImageType
import org.khylo.tv.core.ui.GridButton

class GridButtonBaseRowItem(
	val gridButton: GridButton,
) : BaseRowItem(
	baseRowType = BaseRowType.GridButton,
	staticHeight = true,
) {
	override fun getImage(imageType: ImageType) = null
	override fun getFullName(context: Context) = gridButton.text
	override fun getName(context: Context) = gridButton.text
}
