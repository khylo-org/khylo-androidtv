package org.khylo.tv.playback.khylo.queue

import org.khylo.tv.playback.core.queue.QueueEntry
import org.khylo.tv.playback.core.queue.supplier.PagedQueueSupplier
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.itemsApi
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.ItemFields
import org.khylo.sdk.model.api.ItemSortBy
import org.khylo.sdk.model.api.MediaType

class AudioAlbumQueueSupplier(
	private val album: BaseItemDto,
	private val api: ApiClient,
) : PagedQueueSupplier() {
	init {
		require(album.type == BaseItemKind.MUSIC_ALBUM)
	}

	override var size: Int = 0
		private set

	override suspend fun loadPage(offset: Int, size: Int): Collection<QueueEntry> {
		val result by api.itemsApi.getItems(
			parentId = album.id,
			recursive = true,
			mediaTypes = listOf(MediaType.AUDIO),
			includeItemTypes = listOf(BaseItemKind.AUDIO),
			sortBy = listOf(ItemSortBy.SORT_NAME),
			fields = listOf(ItemFields.MEDIA_SOURCES),
			// Pagination
			startIndex = offset,
			limit = size,
			enableTotalRecordCount = true,
		)
		this.size = result.totalRecordCount
		return result.items.map { createBaseItemQueueEntry(api, it) }
	}
}
