package org.khylo.tv.core.ui.settings.screen.screensaver

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
fun SettingsScreensaverTimeoutScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var screensaverInAppTimeout by rememberPreference(userPreferences, UserPreferences.screensaverInAppTimeout)
	val options = getScreensaverTimeoutOptions()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_screensaver).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_screensaver_inapp_timeout)) },
			)
		}

		items(options) { (duration, heading) ->
			ListButton(
				headingContent = { Text(heading) },
				trailingContent = { RadioButton(checked = screensaverInAppTimeout == duration.inWholeMilliseconds) },
				onClick = {
					screensaverInAppTimeout = duration.inWholeMilliseconds
					router.back()
				},
				modifier = Modifier
					.focusKey("timeout_${duration.inWholeMilliseconds}", initialFocus = screensaverInAppTimeout == duration.inWholeMilliseconds)
			)
		}
	}
}
