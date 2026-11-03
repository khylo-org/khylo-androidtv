package org.khylo.tv.core.ui.settings.screen.playback.mediasegment

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.form.RadioButton
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.LocalRouter
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.playback.segment.MediaSegmentAction
import org.khylo.tv.core.ui.playback.segment.MediaSegmentRepository
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.khylo.sdk.model.api.MediaSegmentType
import org.koin.compose.koinInject

@Composable
fun SettingsPlaybackMediaSegmentScreen(
	segmentType: MediaSegmentType
) {
	val router = LocalRouter.current
	val mediaSegmentRepository = koinInject<MediaSegmentRepository>()
	val action = mediaSegmentRepository.getDefaultSegmentTypeAction(segmentType)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback_media_segments).uppercase()) },
				headingContent = { Text(stringResource(segmentType.nameRes)) },
			)
		}

		items(MediaSegmentAction.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = action == entry) },
				onClick = {
					mediaSegmentRepository.setDefaultSegmentTypeAction(segmentType, entry)
					router.back()
				},
				modifier = Modifier
					.focusKey("media_segment_action_${entry.name}", initialFocus = action == entry)
			)
		}
	}
}
