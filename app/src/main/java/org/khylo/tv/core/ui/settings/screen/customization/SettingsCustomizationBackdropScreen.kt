package org.khylo.tv.core.ui.settings.screen.customization

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.preference.UserPreferences
import org.khylo.tv.core.preference.constant.BackdropBehavior
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
fun SettingsCustomizationBackdropScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var backdropBehavior by rememberPreference(userPreferences, UserPreferences.backdropBehavior)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.lbl_show_backdrop)) },
			)
		}

		items(BackdropBehavior.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = backdropBehavior == entry) },
				onClick = {
					backdropBehavior = entry
					router.back()
				},
				modifier = Modifier
					.focusKey("backdrop_behavior_${entry.name}", initialFocus = backdropBehavior == entry)
			)
		}
	}
}
