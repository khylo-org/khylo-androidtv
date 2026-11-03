package org.khylo.tv.core.util

import android.content.Context
import org.khylo.tv.core.util.apiclient.Response
import org.khylo.sdk.model.api.BaseItemDto
import java.util.UUID

interface PlaybackHelper {
	fun getItemsToPlay(
		context: Context,
		mainItem: BaseItemDto,
		allowIntros: Boolean,
		shuffle: Boolean,
		outerResponse: Response<List<BaseItemDto>>,
	)

	fun retrieveAndPlay(itemId: UUID, shuffle: Boolean, context: Context)

	fun retrieveAndPlay(itemIds: List<UUID>, shuffle: Boolean, position: Long?, index: Int?, context: Context)

	fun playInstantMix(context: Context, item: BaseItemDto)
}
