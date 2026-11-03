package org.khylo.tv.core.ui.settings.screen.license

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import org.khylo.tv.core.R
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.list.ListButton
import org.khylo.tv.core.ui.base.list.ListSection
import org.khylo.tv.core.ui.navigation.LocalRouter
import org.khylo.tv.core.ui.navigation.focus.focusKey
import org.khylo.tv.core.ui.settings.Routes
import org.khylo.tv.core.ui.settings.composable.SettingsColumn

@Composable
fun SettingsLicensesScreen() {
	val context = LocalContext.current
	val router = LocalRouter.current

	val libraries = remember(context) {
		val libs = Libs.Builder()
			.withJson(context, R.raw.aboutlibraries)
			.build()

		libs.libraries.sortedBy { it.name.lowercase() }
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.licenses_link)) },
			)
		}

		items(libraries) { library ->
			ListButton(
				headingContent = { Text("${library.name} ${library.artifactVersion}") },
				captionContent = { Text(library.licenses.joinToString(", ") { license -> license.name }) },
				onClick = { router.push(Routes.LICENSE, mapOf("artifactId" to library.artifactId)) },
				modifier = Modifier.focusKey("library_${library.artifactId}")
			)
		}
	}
}
