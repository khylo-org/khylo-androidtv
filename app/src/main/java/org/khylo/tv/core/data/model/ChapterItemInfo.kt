package org.khylo.tv.core.data.model

import org.khylo.tv.core.util.apiclient.KhyloImage
import java.util.UUID

data class ChapterItemInfo(
	val itemId: UUID,
	val name: String?,
	val startPositionTicks: Long,
	val image: KhyloImage?,
)
