package org.khylo.tv.core.ui.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.khylo.tv.core.R
import org.khylo.tv.core.preference.TelemetryPreferences
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.form.Checkbox
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.settings.compat.rememberPreference
import org.khylo.tv.core.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsTelemetryScreen() {
	val telemetryPreferences = koinInject<TelemetryPreferences>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_telemetry_category)) },
			)
		}

		item {
			var crashReportEnabled by rememberPreference(telemetryPreferences, TelemetryPreferences.crashReportEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_crash_reports)) },
				trailingContent = { Checkbox(checked = crashReportEnabled) },
				captionContent = {
					if (crashReportEnabled) Text(stringResource(R.string.pref_crash_reports_enabled))
					else Text(stringResource(R.string.pref_crash_reports_disabled))
				},
				onClick = { crashReportEnabled = !crashReportEnabled },
				modifier = Modifier.focusKey("crash_report_enabled")
			)
		}

		item {
			var crashReportIncludeLogs by rememberPreference(telemetryPreferences, TelemetryPreferences.crashReportIncludeLogs)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_crash_report_logs)) },
				trailingContent = { Checkbox(checked = crashReportIncludeLogs) },
				captionContent = {
					if (crashReportIncludeLogs) Text(stringResource(R.string.pref_crash_report_logs_enabled))
					else Text(stringResource(R.string.pref_crash_report_logs_disabled))
				},
				onClick = { crashReportIncludeLogs = !crashReportIncludeLogs },
				modifier = Modifier.focusKey("crash_report_logs")
			)
		}
	}
}
