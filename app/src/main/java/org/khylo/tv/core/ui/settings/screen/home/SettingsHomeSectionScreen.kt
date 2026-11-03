package org.khylo.tv.core.ui.settings.screen.home

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.constant.HomeSectionType
import org.khylo.tv.core.preference.UserSettingPreferences
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.form.RadioButton
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListMessage
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.LocalRouter
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.settings.compat.rememberPreference
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsHomeSectionScreen(index: Int) {
	val router = LocalRouter.current
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val sectionPreference = userSettingPreferences.homesections.getOrNull(index)

	if (sectionPreference == null) {
		ListMessage {
			Text("Unknown section $index")
		}

		return
	}

	var sectionType by rememberPreference(userSettingPreferences, sectionPreference)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.home_prefs).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_section_i, index + 1)) },
			)
		}

		items(HomeSectionType.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = sectionType == entry) },
				onClick = {
					sectionType = entry
					router.back()
				},
				modifier = Modifier
					.focusKey("section_type_${entry.name}", initialFocus = sectionType == entry)
			)
		}
	}
}
