package org.khylo.tv.core.ui.browsing

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.khylo.tv.core.R
import org.khylo.tv.core.constant.QueryType
import org.khylo.tv.core.data.repository.ItemRepository
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.itemsApi
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.ItemSortBy
import org.khylo.sdk.model.api.SortOrder
import org.khylo.sdk.model.api.request.GetSimilarItemsRequest
import org.koin.android.ext.android.inject

class SuggestedMoviesFragment : EnhancedBrowseFragment() {
	private val api by inject<ApiClient>()

	init {
		showViews = false
	}

	override fun setupQueries(rowLoader: RowLoader) {
		lifecycleScope.launch {
			val response = withContext(Dispatchers.IO) {
				api.itemsApi.getItems(
					parentId = mFolder.id,
					includeItemTypes = setOf(BaseItemKind.MOVIE),
					sortOrder = setOf(SortOrder.DESCENDING),
					sortBy = setOf(ItemSortBy.DATE_PLAYED),
					limit = 8,
					recursive = true,
				).content
			}

			for (item in response.items) {
				val similar = GetSimilarItemsRequest(
					itemId = item.id,
					fields = ItemRepository.itemFields,
					limit = 7,
				)
				mRows.add(BrowseRowDef(getString(R.string.because_you_watched, item.name), similar, QueryType.SimilarMovies))
			}

			rowLoader.loadRows(mRows)
		}
	}
}
