package org.khylo.tv.core.ui.player.base.toast

import androidx.annotation.DrawableRes

data class MediaToastData(
	@DrawableRes val icon: Int,
	val progress: Float? = null,
)
