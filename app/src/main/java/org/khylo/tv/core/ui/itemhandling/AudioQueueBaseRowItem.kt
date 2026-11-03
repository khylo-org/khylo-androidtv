package org.khylo.tv.core.ui.itemhandling

import org.khylo.tv.playback.core.queue.QueueEntry
import org.khylo.tv.playback.khylo.queue.baseItem

class AudioQueueBaseRowItem(
	val queueEntry: QueueEntry,
) : BaseItemDtoBaseRowItem(
	item = requireNotNull(queueEntry.baseItem) { "AudioQueueBaseRowItem requires the BaseItem to be set on QueueEntry" },
	staticHeight = true,
) {
	var playing: Boolean = false
}
