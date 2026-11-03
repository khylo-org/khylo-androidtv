package org.khylo.tv.core.ui.playback

import android.content.Context
import org.khylo.tv.core.preference.UserPreferences
import org.khylo.tv.core.ui.navigation.ActivityDestinations
import org.khylo.tv.core.ui.navigation.Destinations
import org.khylo.tv.core.ui.navigation.NavigationRepository
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.MediaType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Utility class to launch the playback UI for an item.
 */
class PlaybackLauncher(
	private val mediaManager: MediaManager,
	private val videoQueueManager: VideoQueueManager,
	private val navigationRepository: NavigationRepository,
	private val userPreferences: UserPreferences,
) {
	private val BaseItemDto.supportsExternalPlayer
		get() = when (type) {
			BaseItemKind.MOVIE,
			BaseItemKind.EPISODE,
			BaseItemKind.VIDEO,
			BaseItemKind.SERIES,
			BaseItemKind.SEASON,
			BaseItemKind.RECORDING,
			BaseItemKind.TV_CHANNEL,
			BaseItemKind.PROGRAM,
				-> true

			else -> false
		}

	@JvmOverloads
	fun launch(
		context: Context,
		items: List<BaseItemDto>,
		position: Int? = null,
		replace: Boolean = false,
		itemsPosition: Int = 0,
		shuffle: Boolean = false,
	) {
		val isAudio = items.any { it.mediaType == MediaType.AUDIO }

		if (isAudio) {
			mediaManager.playNow(context, items, itemsPosition, shuffle)
			navigationRepository.navigate(Destinations.nowPlaying)
		} else {
			val items = if (shuffle) items.shuffled() else items

			videoQueueManager.setCurrentVideoQueue(items.toList())
			videoQueueManager.setCurrentMediaPosition(itemsPosition)

			if (items.isEmpty()) return

			if (userPreferences[UserPreferences.useExternalPlayer] && items.all { it.supportsExternalPlayer }) {
				context.startActivity(ActivityDestinations.externalPlayer(context, position?.milliseconds ?: Duration.ZERO))
			} else if (userPreferences[UserPreferences.playbackRewriteVideoEnabled]) {
				val destination = Destinations.videoPlayerNew(position)
				navigationRepository.navigate(destination, replace)
			} else {
				val destination = Destinations.videoPlayer(position)
				navigationRepository.navigate(destination, replace)
			}
		}
	}
}
