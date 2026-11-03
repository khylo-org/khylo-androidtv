package org.khylo.tv.core.ui.settings.screen.playback

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.preference.UserPreferences
import org.khylo.tv.core.preference.constant.HdrFormat
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.LocalRouter
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.settings.Routes
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsPlaybackHdrOverridesScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback_advanced).uppercase()) },
				headingContent = { Text(stringResource(R.string.preference_hdr_overrides)) },
				captionContent = { Text(stringResource(R.string.preference_hdr_overrides_summary)) },
			)
		}

		items(HdrFormat.entries) { format ->
			val mode = userPreferences[format.preference]

			ListButton(
				headingContent = { Text(stringResource(format.nameRes)) },
				captionContent = { Text(stringResource(mode.nameRes)) },
				onClick = {
					router.push(
						route = Routes.PLAYBACK_HDR_OVERRIDE,
						parameters = mapOf("format" to format.name),
					)
				},
				modifier = Modifier.focusKey("hdr_overrides_${format.name}")
			)
		}
	}
}
