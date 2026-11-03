package org.khylo.tv.core.ui.base.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.khylo.tv.core.ui.base.CoreTheme

@Composable
fun ListSection(
	modifier: Modifier = Modifier,
	headingContent: @Composable () -> Unit,
	overlineContent: (@Composable () -> Unit)? = null,
	captionContent: (@Composable () -> Unit)? = null,
	leadingContent: (@Composable () -> Unit)? = null,
	trailingContent: (@Composable () -> Unit)? = null,
	footerContent: (@Composable () -> Unit)? = null,
) {
	ListItemContent(
		headingContent = headingContent,
		overlineContent = overlineContent,
		captionContent = captionContent,
		leadingContent = leadingContent,
		trailingContent = trailingContent,
		footerContent = footerContent,
		headingStyle = CoreTheme.typography.listHeader
			.copy(color = CoreTheme.colorScheme.listHeader),
		modifier = modifier,
	)
}
