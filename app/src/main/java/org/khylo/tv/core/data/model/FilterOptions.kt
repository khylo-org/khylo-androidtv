package org.khylo.tv.core.data.model

import org.khylo.sdk.model.api.ItemFilter


class FilterOptions {
	var isFavoriteOnly = false
	var isUnwatchedOnly = false

	val filters: Set<ItemFilter>
		get() = buildSet {
			if (isFavoriteOnly) add(ItemFilter.IS_FAVORITE)
			if (isUnwatchedOnly) add(ItemFilter.IS_UNPLAYED)
		}
}
