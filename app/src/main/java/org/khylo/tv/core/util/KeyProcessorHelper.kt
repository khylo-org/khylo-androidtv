package org.khylo.tv.core.util

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.khylo.tv.core.R
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.itemsApi
import org.khylo.sdk.model.api.ItemFilter
import org.khylo.sdk.model.api.ItemSortBy
import org.khylo.sdk.model.api.SortOrder
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.UUID

fun FragmentActivity.playFirstUnwatchedItem(parentId: UUID) {
	val api by inject<ApiClient>()
	val playbackHelper by inject<PlaybackHelper>()

	lifecycleScope.launch(Dispatchers.IO) {
		runCatching {
			api.itemsApi.getItems(
				parentId = parentId,
				recursive = true,
				isMissing = false,
				sortBy = setOf(ItemSortBy.SORT_NAME),
				sortOrder = setOf(SortOrder.ASCENDING),
				limit = 1,
				excludeItemTypes = setOf(),
				filters = setOf(ItemFilter.IS_UNPLAYED),
			)
		}.fold(
			onSuccess = { response ->
				val item = response.content.items?.firstOrNull()
				withContext(Dispatchers.Main) {
					if (item == null) {
						Toast.makeText(
							this@playFirstUnwatchedItem,
							R.string.msg_no_items,
							Toast.LENGTH_LONG
						).show()
					} else {
						playbackHelper.retrieveAndPlay(item.id, false, this@playFirstUnwatchedItem)
					}
				}
			},
			onFailure = { error ->
				Timber.e(error, "Error trying to play first unwatched")
				Toast.makeText(
					this@playFirstUnwatchedItem,
					R.string.msg_video_playback_error,
					Toast.LENGTH_LONG
				).show()
			},
		)
	}
}
