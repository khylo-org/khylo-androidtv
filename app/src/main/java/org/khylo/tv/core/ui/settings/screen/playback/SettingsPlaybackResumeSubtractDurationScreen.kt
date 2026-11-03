package org.khylo.tv.core.ui.settings.screen.playback

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.preference.UserPreferences
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
fun SettingsPlaybackResumeSubtractDurationScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var resumeSubtractDuration by rememberPreference(userPreferences, UserPreferences.resumeSubtractDuration)
	val options = getResumeSubtractDurationOptions()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback_advanced).uppercase()) },
				headingContent = { Text(stringResource(R.string.lbl_resume_preroll)) },
			)
		}

		items(options.toList()) { (value, label) ->
			ListButton(
				headingContent = { Text(label) },
				trailingContent = { RadioButton(checked = resumeSubtractDuration == value) },
				onClick = {
					resumeSubtractDuration = value
					router.back()
				},
				modifier = Modifier
					.focusKey("resume_subtract_duration_$value", initialFocus = resumeSubtractDuration == value)
			)
		}
	}
}
