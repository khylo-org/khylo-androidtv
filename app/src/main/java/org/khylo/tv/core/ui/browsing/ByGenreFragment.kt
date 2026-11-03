package org.khylo.tv.core.ui.browsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khylo.tv.core.data.repository.ItemRepository
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.genresApi
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.ItemSortBy
import org.khylo.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject

class ByGenreFragment : BrowseFolderFragment() {
	private val apiClient by inject<ApiClient>()

	override suspend fun setupQueries(rowLoader: RowLoader) {
		val childCount = folder?.childCount ?: 0
		if (childCount <= 0) return

		// Get all genres for this folder
		val genresResponse = withContext(Dispatchers.IO) {
			apiClient.genresApi.getGenres(
				parentId = folder?.id,
				sortBy = setOf(ItemSortBy.SORT_NAME),
			).content
		}

		for (genre in genresResponse.items) {
			val itemsRequest = GetItemsRequest(
				parentId = folder?.id,
				sortBy = setOf(ItemSortBy.SORT_NAME),
				includeItemTypes = includeType?.let(BaseItemKind::fromNameOrNull)?.let(::setOf),
				genres = setOf(genre.name.orEmpty()),
				recursive = true,
				fields = ItemRepository.itemFields,
			)
			rows.add(BrowseRowDef(genre.name, itemsRequest, 40))
		}

		rowLoader.loadRows(rows)
	}
}
