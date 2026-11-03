package org.khylo.tv.playback.khylo.lyrics

import org.khylo.tv.playback.core.element.ElementKey
import org.khylo.tv.playback.core.element.element
import org.khylo.tv.playback.core.element.elementFlow
import org.khylo.tv.playback.core.queue.QueueEntry
import org.khylo.sdk.model.api.LyricDto

private val lyricsKey = ElementKey<LyricDto>("LyricDto")

/**
 * Get or set the [LyricDto] for this [QueueEntry].
 */
var QueueEntry.lyrics by element(lyricsKey)
val QueueEntry.lyricsFlow by elementFlow(lyricsKey)
