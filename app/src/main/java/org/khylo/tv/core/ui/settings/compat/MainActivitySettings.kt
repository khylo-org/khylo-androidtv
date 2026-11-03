package org.khylo.tv.core.ui.settings.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.khylo.tv.core.ui.navigation.ProvideRouter
import org.khylo.tv.core.ui.settings.Routes
import org.khylo.tv.core.ui.settings.composable.SettingsDialog
import org.khylo.tv.core.ui.settings.composable.SettingsRouterContent
import org.khylo.tv.core.ui.settings.routes
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun MainActivitySettings() {
	val viewModel = koinActivityViewModel<SettingsViewModel>()
	val visible by viewModel.visible.collectAsState()

	ProvideRouter(routes, Routes.MAIN) {
		SettingsDialog(
			visible = visible,
			onDismissRequest = { viewModel.hide() }
		) {
			SettingsRouterContent()
		}
	}
}
