package org.khylo.tv.playback.khylo.queue

import org.khylo.tv.playback.core.element.ElementKey
import org.khylo.tv.playback.core.element.element
import org.khylo.tv.playback.core.element.elementFlow
import org.khylo.tv.playback.core.queue.QueueEntry
import org.khylo.sdk.model.api.BaseItemDto

private val baseItemKey = ElementKey<BaseItemDto>("BaseItemDto")

/**
 * Get or set the [BaseItemDto] for this [QueueEntry].
 */
var QueueEntry.baseItem by element(baseItemKey)

/**
 * Get the [BaseItemDto] flow for this [QueueEntry].
 */
val QueueEntry.baseItemFlow by elementFlow(baseItemKey)
