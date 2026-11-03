@file:JvmName("JavaCompat")

package org.khylo.tv.core.util.sdk.compat

import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.MediaSourceInfo
import org.khylo.sdk.model.api.UserItemDataDto
import java.time.LocalDateTime
import java.util.UUID

fun BaseItemDto.copyWithDisplayPreferencesId(
	displayPreferencesId: String?
) = copy(
	displayPreferencesId = displayPreferencesId,
)

fun BaseItemDto.copyWithDates(
	premiereDate: LocalDateTime?,
	endDate: LocalDateTime?,
	officialRating: String?,
	runTimeTicks: Long?,
) = copy(
	premiereDate = premiereDate,
	endDate = endDate,
	officialRating = officialRating,
	runTimeTicks = runTimeTicks,
)

fun BaseItemDto.copyWithTimerId(
	seriesTimerId: String?,
) = copy(
	seriesTimerId = seriesTimerId,
)

fun BaseItemDto.copyWithOverview(
	overview: String?,
) = copy(
	overview = overview,
)

fun BaseItemDto.copyWithParentId(
	parentId: UUID?,
) = copy(
	parentId = parentId,
)

fun BaseItemDto.copyWithUserData(
	userData: UserItemDataDto?,
) = copy(
	userData = userData,
)

fun MediaSourceInfo.getVideoStream() = mediaStreams?.firstOrNull {
	it.type == org.khylo.sdk.model.api.MediaStreamType.VIDEO
}

val BaseItemDto.canResume get() = (userData?.playbackPositionTicks ?: 0) > 0
