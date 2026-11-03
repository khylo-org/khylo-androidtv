package org.khylo.tv.core.ui.itemhandling

import android.content.Context
import org.khylo.tv.core.constant.ImageType
import org.khylo.tv.core.data.model.ChapterItemInfo
import org.khylo.tv.core.util.TimeUtils
import org.khylo.sdk.model.extensions.ticks

class ChapterItemInfoBaseRowItem(
	val chapterInfo: ChapterItemInfo,
) : BaseRowItem(
	baseRowType = BaseRowType.Chapter,
	staticHeight = true,
) {
	override fun getImage(imageType: ImageType) = chapterInfo.image
	override val itemId get() = chapterInfo.itemId
	override fun getFullName(context: Context) = chapterInfo.name
	override fun getName(context: Context) = chapterInfo.name

	override fun getSubText(context: Context) =
		chapterInfo.startPositionTicks.ticks.inWholeMilliseconds.let(TimeUtils::formatMillis)
}
