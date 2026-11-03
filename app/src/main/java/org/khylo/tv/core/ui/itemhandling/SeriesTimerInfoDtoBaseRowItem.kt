package org.khylo.tv.core.ui.itemhandling

import android.content.Context
import org.khylo.tv.core.R
import org.khylo.tv.core.constant.ImageType
import org.khylo.tv.core.util.apiclient.getSeriesOverview
import org.khylo.sdk.model.api.SeriesTimerInfoDto
import org.khylo.sdk.model.serializer.toUUIDOrNull

class SeriesTimerInfoDtoBaseRowItem(
	val seriesTimerInfo: SeriesTimerInfoDto,
) : BaseRowItem(
	baseRowType = BaseRowType.SeriesTimer,
) {
	override fun getImage(imageType: ImageType) = null
	override fun getFullName(context: Context) = seriesTimerInfo.name
	override fun getName(context: Context) = seriesTimerInfo.name
	override val itemId get() = seriesTimerInfo.id?.toUUIDOrNull()
	override fun getSubText(context: Context): String = listOfNotNull(
		if (seriesTimerInfo.recordAnyChannel == true) context.getString(R.string.all_channels)
		else seriesTimerInfo.channelName,
		seriesTimerInfo.dayPattern
	).joinToString(" ")

	override fun getSummary(context: Context) = seriesTimerInfo.getSeriesOverview(context)
}
