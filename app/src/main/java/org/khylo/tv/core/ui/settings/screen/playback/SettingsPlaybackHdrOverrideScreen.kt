package org.khylo.tv.core.ui.settings.screen.playback

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.preference.UserPreferences
import org.khylo.tv.core.preference.constant.HdrFormat
import org.khylo.tv.core.preference.constant.HdrOverrideMode
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
fun SettingsPlaybackHdrOverrideScreen(
	format: HdrFormat,
) {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var mode by rememberPreference(userPreferences, format.preference)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.preference_hdr_overrides).uppercase()) },
				headingContent = { Text(stringResource(format.nameRes)) },
				captionContent = { Text(stringResource(format.descriptionRes)) },
			)
		}

		items(HdrOverrideMode.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = mode == entry) },
				onClick = {
					mode = entry
					router.back()
				},
				modifier = Modifier
					.focusKey("hdr_override_${format.name}_${entry.name}", initialFocus = mode == entry)
			)
		}
	}
}
