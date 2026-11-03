package org.khylo.tv.core.ui.settings.screen.authentication

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.khylo.tv.core.R
import org.khylo.tv.core.auth.repository.AuthenticationRepository
import org.khylo.tv.core.auth.repository.ServerRepository
import org.khylo.tv.core.auth.repository.ServerUserRepository
import org.khylo.tv.core.auth.store.AuthenticationPreferences
import org.khylo.tv.core.preference.constant.UserSelectBehavior
import org.khylo.tv.core.ui.base.ProfilePicture
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.button.IconButtonDefaults
import org.khylo.tv.core.ui.base.form.RadioButton
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.LocalRouter
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.settings.compat.rememberPreference
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsAuthenticationAutoSignInScreen() {
	val router = LocalRouter.current
	val serverRepository = koinInject<ServerRepository>()
	val serverUserRepository = koinInject<ServerUserRepository>()
	val authenticationRepository = koinInject<AuthenticationRepository>()
	val authenticationPreferences = koinInject<AuthenticationPreferences>()
	var autoLoginUserBehavior by rememberPreference(authenticationPreferences, AuthenticationPreferences.autoLoginUserBehavior)
	var autoLoginServerId by rememberPreference(authenticationPreferences, AuthenticationPreferences.autoLoginServerId)
	var autoLoginUserId by rememberPreference(authenticationPreferences, AuthenticationPreferences.autoLoginUserId)

	LaunchedEffect(serverRepository) {
		serverRepository.loadStoredServers()
	}

	val storedServers by serverRepository.storedServers.collectAsState()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_login).uppercase()) },
				headingContent = { Text(stringResource(R.string.auto_sign_in)) },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.user_picker_disable_title)) },
				captionContent = { Text(stringResource(R.string.user_picker_disable_summary)) },
				trailingContent = { RadioButton(checked = autoLoginUserBehavior == UserSelectBehavior.DISABLED) },
				onClick = {
					autoLoginUserBehavior = UserSelectBehavior.DISABLED
					router.back()
				},
				modifier = Modifier
					.focusKey("disable", initialFocus = autoLoginUserBehavior == UserSelectBehavior.DISABLED)
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.user_picker_last_user_title)) },
				captionContent = { Text(stringResource(R.string.user_picker_last_user_summary)) },
				trailingContent = { RadioButton(checked = autoLoginUserBehavior == UserSelectBehavior.LAST_USER) },
				onClick = {
					autoLoginUserBehavior = UserSelectBehavior.LAST_USER
					router.back()
				},
				modifier = Modifier
					.focusKey("last_user", initialFocus = autoLoginUserBehavior == UserSelectBehavior.LAST_USER)
			)
		}

		for (server in storedServers) {
			item { ListSection(headingContent = { Text(server.name) }) }

			val users = serverUserRepository.getStoredServerUsers(server)
			val serverId = server.id.toString()
			items(users) { user ->
				val userId = user.id.toString()

				ListButton(
					leadingContent = {
						ProfilePicture(
							url = authenticationRepository.getUserImageUrl(server, user),
							modifier = Modifier
								.size(24.dp)
								.clip(IconButtonDefaults.Shape)
						)
					},
					headingContent = { Text(user.name) },
					trailingContent = { RadioButton(checked = autoLoginUserBehavior == UserSelectBehavior.SPECIFIC_USER && autoLoginServerId == serverId && autoLoginUserId == userId) },
					onClick = {
						autoLoginUserBehavior = UserSelectBehavior.SPECIFIC_USER
						autoLoginServerId = serverId
						autoLoginUserId = userId

						router.back()
					},
					modifier = Modifier
						.focusKey(
							key = "user_$userId",
							initialFocus = autoLoginUserBehavior == UserSelectBehavior.SPECIFIC_USER &&
								autoLoginServerId == serverId &&
								autoLoginUserId == userId,
						)
				)
			}
		}
	}
}
