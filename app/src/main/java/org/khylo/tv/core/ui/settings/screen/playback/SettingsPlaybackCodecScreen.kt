package org.khylo.tv.core.ui.settings.screen.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.preference.UserPreferences
import org.khylo.tv.core.preference.constant.BitstreamAudioFormat
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.form.Checkbox
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.LocalRouter
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.settings.Routes
import org.khylo.tv.core.ui.settings.compat.rememberPreference
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.khylo.tv.core.util.AndroidVersion
import org.koin.compose.koinInject

@Composable
fun SettingsPlaybackCodecScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback_advanced).uppercase()) },
				headingContent = { Text(stringResource(R.string.preference_codecs)) },
				captionContent = { Text(stringResource(R.string.preference_codecs_summary)) },
			)
		}

		item {
			var userAVCLevel by rememberPreference(userPreferences, UserPreferences.userAVCLevel)

			ListButton(
				headingContent = { Text(stringResource(R.string.user_avc_level)) },
				captionContent = { Text(stringResource(userAVCLevel.nameRes)) },
				onClick = { router.push(Routes.PLAYBACK_AVC_LEVEL) },
				modifier = Modifier.focusKey(Routes.PLAYBACK_AVC_LEVEL)
			)
		}

		item {
			var userHEVCLevel by rememberPreference(userPreferences, UserPreferences.userHEVCLevel)

			ListButton(
				headingContent = { Text(stringResource(R.string.user_hevc_level)) },
				captionContent = { Text(stringResource(userHEVCLevel.nameRes)) },
				onClick = { router.push(Routes.PLAYBACK_HEVC_LEVEL) },
				modifier = Modifier.focusKey(Routes.PLAYBACK_HEVC_LEVEL)
			)
		}

		items(BitstreamAudioFormat.entries.size) { index ->
			val format = BitstreamAudioFormat.entries[index]
			val mode = userPreferences[format.preference]

			ListButton(
				headingContent = { Text(stringResource(format.nameRes)) },
				captionContent = { Text(stringResource(mode.nameRes)) },
				onClick = {
					router.push(
						route = Routes.PLAYBACK_BITSTREAM_AUDIO,
						parameters = mapOf("format" to format.name),
					)
				},
				modifier = Modifier.focusKey("bitstream_audio_${format.name}"),
			)
		}

		if (AndroidVersion.isAtLeastQ) {
			item {
				var softwareCodecsEnabled by rememberPreference(userPreferences, UserPreferences.softwareCodecsEnabled)

				ListButton(
					headingContent = { Text(stringResource(R.string.pref_software_codecs_enabled)) },
					captionContent = { Text(stringResource(R.string.pref_software_codecs_enabled_description)) },
					trailingContent = { Checkbox(checked = softwareCodecsEnabled) },
					onClick = { softwareCodecsEnabled = !softwareCodecsEnabled },
					modifier = Modifier.focusKey("software_codecs_enabled")
				)
			}
		}
	}
}
