package org.khylo.tv.core.ui.settings.screen.playback.mediasegment

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.LocalRouter
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.playback.segment.MediaSegmentRepository
import org.khylo.tv.core.ui.settings.Routes
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsPlaybackMediaSegmentsScreen() {
	val router = LocalRouter.current
	val mediaSegmentRepository = koinInject<MediaSegmentRepository>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_playback_media_segments)) },
			)
		}

		items(MediaSegmentRepository.SupportedTypes) { segmentType ->
			val action = mediaSegmentRepository.getDefaultSegmentTypeAction(segmentType)

			ListButton(
				headingContent = { Text(stringResource(segmentType.nameRes)) },
				captionContent = { Text(stringResource(action.nameRes)) },
				onClick = {
					router.push(
						route = Routes.PLAYBACK_MEDIA_SEGMENT,
						parameters = mapOf(
							"segmentType" to segmentType.toString(),
						),
					)
				},
				modifier = Modifier.focusKey("media_segment_type_$segmentType")
			)
		}
	}
}
