package org.khylo.tv.core.integration.dream

import org.khylo.tv.playback.core.element.ElementKey
import org.khylo.tv.playback.core.element.requiredElement
import org.khylo.tv.playback.core.queue.QueueEntry

private val visibleInScreensaverKey = ElementKey<Boolean>("visibleInScreensaver")

/**
 * Get or set whether this [QueueEntry] is shown in the screensaver.
 */
var QueueEntry.visibleInScreensaver by requiredElement(visibleInScreensaverKey) { false }
