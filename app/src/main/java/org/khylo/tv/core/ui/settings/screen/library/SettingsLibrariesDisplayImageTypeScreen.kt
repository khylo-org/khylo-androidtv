package org.khylo.tv.core.ui.settings.screen.library

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.constant.ImageType
import org.khylo.tv.core.preference.LibraryPreferences
import org.khylo.tv.core.preference.PreferencesRepository
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.form.RadioButton
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.LocalRouter
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.settings.compat.rememberPreference
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject
import java.util.UUID

@Composable
fun SettingsLibrariesDisplayImageTypeScreen(itemId: UUID, displayPreferencesId: String) {
	val router = LocalRouter.current
	val preferencesRepository = koinInject<PreferencesRepository>()
	val userView = rememberUserView(itemId)
	val libraryPreferences = remember(displayPreferencesId) { preferencesRepository.getLibraryPreferences(displayPreferencesId) }
	var imageType by rememberPreference(libraryPreferences, LibraryPreferences.imageType)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(userView?.name.orEmpty().uppercase()) },
				headingContent = { Text(stringResource(R.string.lbl_image_type)) },
			)
		}

		items(ImageType.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = imageType == entry) },
				onClick = {
					imageType = entry
					router.back()
				},
				modifier = Modifier
					.focusKey("image_type_${entry.name}", initialFocus = imageType == entry)
			)
		}
	}
}
