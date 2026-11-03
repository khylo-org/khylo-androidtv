package org.khylo.tv.playback.khylo.mediasegment

import org.khylo.tv.playback.core.element.ElementKey
import org.khylo.tv.playback.core.element.element
import org.khylo.tv.playback.core.element.elementFlow
import org.khylo.tv.playback.core.queue.QueueEntry
import org.khylo.sdk.model.api.MediaSegmentDto

private val mediaSegmentsKey = ElementKey<Collection<MediaSegmentDto>>("MediaSegments")

/**
 * Get or set the [Collection<MediaSegmentDto>] for this [QueueEntry].
 */
var QueueEntry.mediaSegments by element(mediaSegmentsKey)
val QueueEntry.mediaSegmentsFlow by elementFlow(mediaSegmentsKey)
