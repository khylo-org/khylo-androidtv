package org.khylo.tv.core.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.khylo.tv.core.R
import org.khylo.tv.core.auth.repository.UserRepository
import org.khylo.tv.core.constant.ChangeTriggerType
import org.khylo.tv.core.data.repository.ItemRepository
import org.khylo.tv.core.ui.browsing.BrowseRowDef
import org.khylo.tv.core.ui.presentation.CardPresenter
import org.khylo.tv.core.ui.presentation.MutableObjectAdapter
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.CollectionType
import org.khylo.sdk.model.api.request.GetLatestMediaRequest

class HomeFragmentLatestRow(
	private val userRepository: UserRepository,
	private val userViews: Collection<BaseItemDto>,
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		// Get configuration (to find excluded items)
		val configuration = userRepository.currentUser.value?.configuration

		// Create a list of views to include
		val latestItemsExcludes = configuration?.latestItemsExcludes.orEmpty()
		userViews
			.filterNot { item -> item.collectionType in EXCLUDED_COLLECTION_TYPES || item.id in latestItemsExcludes }
			.map { item ->
				// Create query and add it to a new row
				val request = GetLatestMediaRequest(
					fields = ItemRepository.browseFields,
					imageTypeLimit = 1,
					parentId = item.id,
					groupItems = true,
					limit = ITEM_LIMIT,
				)

				val title = context.getString(R.string.lbl_latest_in, item.name)
				HomeFragmentBrowseRowDefRow(BrowseRowDef(title, request, arrayOf(ChangeTriggerType.LibraryUpdated)))
			}.forEach { row ->
				// Add row to adapter
				row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
			}
	}

	companion object {
		// Collections excluded from latest row based on app support and common sense
		private val EXCLUDED_COLLECTION_TYPES = arrayOf(
			CollectionType.PLAYLISTS,
			CollectionType.LIVETV,
			CollectionType.BOXSETS,
			CollectionType.BOOKS,
		)

		// Maximum amount of items loaded for a row
		private const val ITEM_LIMIT = 50
	}
}
