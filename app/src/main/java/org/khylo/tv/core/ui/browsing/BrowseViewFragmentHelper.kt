package org.khylo.tv.core.ui.browsing

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.khylo.tv.core.data.repository.ItemRepository
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.liveTvApi
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemDtoQueryResult
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.LocationType
import org.khylo.sdk.model.api.MediaType
import org.khylo.sdk.model.api.TimerInfoDto
import org.khylo.sdk.model.api.TimerInfoDtoQueryResult
import org.khylo.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject

fun EnhancedBrowseFragment.getLiveTvRecordingsAndTimers(
	callback: (recordings: BaseItemDtoQueryResult, timers: TimerInfoDtoQueryResult) -> Unit,
	errorCallback: (exception: Throwable) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			val recordings = async(Dispatchers.IO) {
				api.liveTvApi.getRecordings(
					fields = ItemRepository.itemFields,
					enableImages = true,
					limit = 40,
				).content
			}

			val timers = async(Dispatchers.IO) {
				api.liveTvApi.getTimers().content
			}

			recordings.await() to timers.await()
		}.fold(
			onSuccess = { (recordings, timers) -> callback(recordings, timers) },
			onFailure = { exception -> errorCallback(exception) }
		)
	}
}

fun EnhancedBrowseFragment.getLiveTvTimers(
	callback: (timers: TimerInfoDtoQueryResult) -> Unit,
	errorCallback: (exception: Throwable) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getTimers().content
			}
		}.fold(
			onSuccess = { timers -> callback(timers) },
			onFailure = { exception -> errorCallback(exception) }
		)
	}
}

fun getTimerProgramInfo(timer: TimerInfoDto): BaseItemDto {
	val programInfo = timer.programInfo ?: BaseItemDto(
		id = requireNotNull(timer.id?.toUUIDOrNull()),
		channelName = timer.channelName,
		name = timer.name.orEmpty(),
		type = BaseItemKind.PROGRAM,
		timerId = timer.id,
		seriesTimerId = timer.seriesTimerId,
		startDate = timer.startDate,
		endDate = timer.endDate,
		mediaType = MediaType.UNKNOWN,
	)
	return programInfo.copy(
		locationType = LocationType.VIRTUAL,
	)
}
