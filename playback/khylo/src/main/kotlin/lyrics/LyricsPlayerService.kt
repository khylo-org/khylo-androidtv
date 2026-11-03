package org.khylo.tv.playback.khylo.lyrics

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.khylo.tv.playback.core.plugin.PlayerService
import org.khylo.tv.playback.core.queue.QueueEntry
import org.khylo.tv.playback.core.queue.queue
import org.khylo.tv.playback.khylo.queue.baseItem
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.lyricsApi

class LyricsPlayerService(
	private val api: ApiClient,
) : PlayerService() {
	override suspend fun onInitialize() {
		// Load lyrics for an item as soon as it becomes the currently playing item
		manager.queue.entry
			.filterNotNull()
			.onEach { entry -> fetchLyrics(entry) }
			.launchIn(coroutineScope)
	}

	private suspend fun fetchLyrics(entry: QueueEntry) {
		// Already has lyrics!
		if (entry.lyrics != null) return

		// BaseItem doesn't exist or doesn't have lyrics
		val baseItem = entry.baseItem ?: return
		if (baseItem.hasLyrics != true) return

		// Get via API
		val lyrics by api.lyricsApi.getLyrics(baseItem.id)
		entry.lyrics = lyrics
	}
}
