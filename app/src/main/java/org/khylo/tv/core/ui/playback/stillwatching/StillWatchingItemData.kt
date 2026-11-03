package org.khylo.tv.core.ui.playback.stillwatching

import org.khylo.tv.core.util.apiclient.KhyloImage
import org.khylo.sdk.model.UUID
import org.khylo.sdk.model.api.BaseItemDto

data class StillWatchingItemData(
	val baseItem: BaseItemDto,
	val id: UUID,
	val title: String,
	val thumbnail: KhyloImage?,
	val logo: KhyloImage?,
)
