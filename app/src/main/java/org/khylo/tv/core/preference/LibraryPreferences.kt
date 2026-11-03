package org.khylo.tv.core.preference

import org.khylo.tv.core.constant.GridDirection
import org.khylo.tv.core.constant.ImageType
import org.khylo.tv.core.constant.PosterSize
import org.khylo.tv.core.preference.store.DisplayPreferencesStore
import org.khylo.tv.preference.booleanPreference
import org.khylo.tv.preference.enumPreference
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.model.api.ItemSortBy
import org.khylo.sdk.model.api.SortOrder

class LibraryPreferences(
	displayPreferencesId: String,
	api: ApiClient,
) : DisplayPreferencesStore(
	displayPreferencesId = displayPreferencesId,
	api = api,
) {
	companion object {
		val posterSize = enumPreference("PosterSize", PosterSize.MED)
		val imageType = enumPreference("ImageType", ImageType.POSTER)
		val gridDirection = enumPreference("GridDirection", GridDirection.HORIZONTAL)
		val enableSmartScreen = booleanPreference("SmartScreen", false)

		// Filters
		val filterFavoritesOnly = booleanPreference("FilterFavoritesOnly", false)
		val filterUnwatchedOnly = booleanPreference("FilterUnwatchedOnly", false)

		// Item sorting
		val sortBy = enumPreference("SortBy", ItemSortBy.SORT_NAME)
		val sortOrder = enumPreference("SortOrder", SortOrder.ASCENDING)
	}
}
