package org.khylo.tv.core.ui.settings.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import org.khylo.tv.core.ui.base.CoreTheme
import org.khylo.tv.core.ui.base.LocalShapes
import org.khylo.tv.designsystem.Tokens

@Composable
fun SettingsLayout(
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	Box(
		modifier = modifier
			.padding(Tokens.Space.spaceMd)
			.clip(LocalShapes.current.large)
			.background(CoreTheme.colorScheme.surface)
			.width(350.dp)
			.fillMaxHeight()
			.focusRestorer(),
		content = content
	)
}
