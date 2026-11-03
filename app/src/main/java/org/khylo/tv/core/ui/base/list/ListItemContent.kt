package org.khylo.tv.core.ui.base.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.khylo.tv.core.ui.base.CoreTheme
import org.khylo.tv.core.ui.base.LocalTextStyle
import org.khylo.tv.core.ui.base.ProvideTextStyle
import org.khylo.tv.designsystem.Tokens

@Composable
fun ListItemContent(
	modifier: Modifier = Modifier,
	headingContent: @Composable () -> Unit,
	overlineContent: (@Composable () -> Unit)? = null,
	captionContent: (@Composable () -> Unit)? = null,
	leadingContent: (@Composable () -> Unit)? = null,
	trailingContent: (@Composable () -> Unit)? = null,
	footerContent: (@Composable () -> Unit)? = null,
	headingStyle: TextStyle,
) {
	Column(
		modifier = modifier
			// TODO: Add suitable space token for this padding
			.padding(12.dp),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
		) {
			leadingContent?.let { content ->
				Box(
					modifier = Modifier
						.sizeIn(minWidth = 24.dp),
					contentAlignment = Alignment.Center,
					content = {
						ProvideTextStyle(LocalTextStyle.current.copy(color = CoreTheme.colorScheme.listCaption)) {
							content()
						}
					}
				)
				Spacer(Modifier.width(Tokens.Space.spaceMd))
			}

			Column(
				modifier = Modifier
					.weight(1f),
			) {
				overlineContent?.let { content ->
					ProvideTextStyle(CoreTheme.typography.listOverline.copy(color = CoreTheme.colorScheme.listOverline)) {
						content()
					}
					Spacer(Modifier.height(Tokens.Space.space2xs))
				}

				ProvideTextStyle(headingStyle) {
					headingContent()
				}

				captionContent?.let { content ->
					Spacer(Modifier.height(Tokens.Space.spaceXs))
					ProvideTextStyle(CoreTheme.typography.listCaption.copy(color = CoreTheme.colorScheme.listCaption)) {
						content()
					}
				}
			}

			trailingContent?.let { content ->
				Spacer(Modifier.width(Tokens.Space.spaceMd))

				Box(
					modifier = Modifier
						.sizeIn(minWidth = 24.dp),
					contentAlignment = Alignment.Center,
					content = { content() }
				)
			}
		}

		footerContent?.let { content ->
			Spacer(Modifier.height(Tokens.Space.spaceXs))

			ProvideTextStyle(LocalTextStyle.current.copy(color = CoreTheme.colorScheme.listCaption)) {
				content()
			}
		}
	}
}
