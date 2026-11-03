package org.khylo.tv.core.ui.shared.toolbar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.khylo.tv.core.R
import org.khylo.tv.core.ui.base.Icon
import org.khylo.tv.core.ui.base.button.IconButton
import org.khylo.tv.core.ui.navigation.ProvideRouter
import org.khylo.tv.core.ui.settings.Routes
import org.khylo.tv.core.ui.settings.composable.SettingsDialog
import org.khylo.tv.core.ui.settings.composable.SettingsRouterContent
import org.khylo.tv.core.ui.settings.routes

@Composable
fun StartupToolbar(
	openHelp: () -> Unit
) {
	var settingsVisible by remember { mutableStateOf(false) }

	Toolbar(
		end = {
			ToolbarButtons {
				IconButton(onClick = openHelp) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_help),
						contentDescription = stringResource(R.string.help),
					)
				}

				IconButton(onClick = { settingsVisible = true }) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
						contentDescription = stringResource(R.string.lbl_settings),
					)
				}

				Spacer(Modifier.width(8.dp))

				ToolbarClock()
			}
		}
	)


	ProvideRouter(routes, Routes.AUTHENTICATION_FROM_LOGIN) {
		SettingsDialog(
			visible = settingsVisible,
			onDismissRequest = { settingsVisible = false }
		) {
			SettingsRouterContent()
		}
	}
}
