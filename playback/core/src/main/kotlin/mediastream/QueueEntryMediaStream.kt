package org.khylo.tv.playback.core.mediastream

import org.khylo.tv.playback.core.element.ElementKey
import org.khylo.tv.playback.core.element.element
import org.khylo.tv.playback.core.element.elementFlow
import org.khylo.tv.playback.core.queue.QueueEntry

private val mediaStreamKey = ElementKey<PlayableMediaStream>("MediaStream")

/**
 * Get or set the [MediaStream] for this [QueueEntry].
 */
var QueueEntry.mediaStream by element(mediaStreamKey)

/**
 * Get the [MediaStream] flow for this [QueueEntry].
 */
val QueueEntry.mediaStreamFlow by elementFlow(mediaStreamKey)
