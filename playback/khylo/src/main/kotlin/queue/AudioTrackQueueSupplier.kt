package org.khylo.tv.playback.khylo.queue

import org.khylo.tv.playback.core.queue.QueueEntry
import org.khylo.tv.playback.core.queue.supplier.PagedQueueSupplier
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.userLibraryApi
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind

class AudioTrackQueueSupplier(
	private val item: BaseItemDto,
	private val api: ApiClient,
) : PagedQueueSupplier() {
	init {
		require(item.type == BaseItemKind.AUDIO)
	}

	override var size: Int = 1

	override suspend fun loadPage(offset: Int, size: Int): Collection<QueueEntry> {
		// We only have a single item
		if (offset > 0) return emptyList()

		val item by api.userLibraryApi.getItem(itemId = item.id)
		return listOf(createBaseItemQueueEntry(api, item))
	}
}
