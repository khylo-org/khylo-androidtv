package org.khylo.tv.core.ui.settings.screen.authentication

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.auth.model.AuthenticationSortBy
import org.khylo.tv.core.auth.store.AuthenticationPreferences
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
fun SettingsAuthenticationSortByScreen() {
	val router = LocalRouter.current
	val authenticationPreferences = koinInject<AuthenticationPreferences>()
	var sortBy by rememberPreference(authenticationPreferences, AuthenticationPreferences.sortBy)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_login).uppercase()) },
				headingContent = { Text(stringResource(R.string.sort_accounts_by)) },
			)
		}

		items(AuthenticationSortBy.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = sortBy == entry) },
				onClick = {
					sortBy = entry
					router.back()
				},
				modifier = Modifier
					.focusKey("sort_by_${entry.name}", initialFocus = sortBy == entry)
			)
		}
	}
}
