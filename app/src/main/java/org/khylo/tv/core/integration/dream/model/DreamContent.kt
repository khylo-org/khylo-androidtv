package org.khylo.tv.core.integration.dream.model

import android.graphics.Bitmap
import org.khylo.tv.playback.core.queue.QueueEntry
import org.khylo.sdk.model.api.BaseItemDto

sealed interface DreamContent {
	data object Logo : DreamContent
	data class LibraryShowcase(val item: BaseItemDto, val backdrop: Bitmap, val logo: Bitmap?) : DreamContent
	data class NowPlaying(val entry: QueueEntry, val item: BaseItemDto) : DreamContent
}
