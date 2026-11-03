package org.khylo.tv.core.ui.settings.screen.livetv

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.preference.LiveTvPreferences
import org.khylo.tv.core.preference.constant.LiveTvChannelOrder
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
fun SettingsLiveTvGuideChannelOrderScreen() {
	val router = LocalRouter.current
	val liveTvPreferences = koinInject<LiveTvPreferences>()
	var channelOrder by rememberPreference(liveTvPreferences, LiveTvPreferences.channelOrder)
	val channelOrderEnum = LiveTvChannelOrder.fromString(channelOrder)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.lbl_sort_by)) },
			)
		}

		items(LiveTvChannelOrder.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = channelOrderEnum == entry) },
				onClick = {
					channelOrder = entry.stringValue
					router.back()
				},
				modifier = Modifier
					.focusKey("channel_order_${entry.name}", initialFocus = channelOrderEnum == entry)
			)
		}
	}
}
