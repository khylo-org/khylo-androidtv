package org.khylo.tv.core.ui.search

import androidx.annotation.StringRes
import org.khylo.sdk.model.api.BaseItemDto

data class SearchResultGroup(
	@StringRes val labelRes: Int,
	val items: Collection<BaseItemDto>,
)
