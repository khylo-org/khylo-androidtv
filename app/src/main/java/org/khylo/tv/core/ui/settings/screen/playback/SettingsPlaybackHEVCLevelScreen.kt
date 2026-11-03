package org.khylo.tv.core.ui.settings.screen.playback

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.preference.UserPreferences
import org.khylo.tv.core.preference.constant.HEVCLevel
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.form.RadioButton
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.LocalRouter
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.settings.compat.rememberPreference
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsPlaybackHEVCLevelScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()

	var userHEVCLevel by rememberPreference(userPreferences, UserPreferences.userHEVCLevel)
	val manualOptions = HEVCLevel.entries.filter { it != HEVCLevel.AUTO }

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.preference_codecs).uppercase()) },
				headingContent = { Text(stringResource(R.string.user_hevc_level)) },
				captionContent = { Text(stringResource(R.string.codec_level_warning)) },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(HEVCLevel.AUTO.nameRes)) },
				trailingContent = { RadioButton(checked = userHEVCLevel == HEVCLevel.AUTO) },
				onClick = {
					userHEVCLevel = HEVCLevel.AUTO
					router.back()
				},
				modifier = Modifier
					.focusKey("hevc_level_auto", initialFocus = userHEVCLevel == HEVCLevel.AUTO)
			)
		}

		item { ListSection(headingContent = { Text(stringResource(R.string.codec_level_manual)) }) }

		items(manualOptions) { level ->
			ListButton(
				headingContent = { Text(stringResource(level.nameRes)) },
				trailingContent = { RadioButton(checked = userHEVCLevel == level) },
				onClick = {
					userHEVCLevel = level
					router.back()
				},
				modifier = Modifier
					.focusKey("hevc_level_${level.name}", initialFocus = userHEVCLevel == level)
			)
		}
	}
}
