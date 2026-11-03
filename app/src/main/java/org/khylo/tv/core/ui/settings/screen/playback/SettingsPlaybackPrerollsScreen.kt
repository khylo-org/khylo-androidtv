package org.khylo.tv.core.ui.settings.screen.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.preference.UserPreferences
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.form.Checkbox
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.settings.compat.rememberPreference
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsPlaybackPrerollsScreen() {
	val userPreferences = koinInject<UserPreferences>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_playback_prerolls)) },
			)
		}

		item {
			var cinemaModeEnabled by rememberPreference(userPreferences, UserPreferences.cinemaModeEnabled)

			ListButton(
				headingContent = { Text(stringResource(R.string.pref_prerolls_enabled)) },
				trailingContent = { Checkbox(checked = cinemaModeEnabled) },
				captionContent = { Text(stringResource(R.string.pref_prerolls_enabled_description)) },
				onClick = { cinemaModeEnabled = !cinemaModeEnabled },
				modifier = Modifier.focusKey("cinema_mode_enabled")
			)
		}
	}
}
