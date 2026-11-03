package org.khylo.tv.core.ui.playback.nextup

import org.khylo.tv.core.util.apiclient.KhyloImage
import org.khylo.sdk.model.UUID
import org.khylo.sdk.model.api.BaseItemDto

data class NextUpItemData(
	val baseItem: BaseItemDto,
	val id: UUID,
	val title: String,
	val thumbnail: KhyloImage?,
	val logo: KhyloImage?,
)
