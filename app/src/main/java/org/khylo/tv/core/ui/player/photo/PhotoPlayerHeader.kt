package org.khylo.tv.core.ui.player.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.khylo.tv.core.ui.base.LocalTextStyle
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.player.base.PlayerHeader
import org.khylo.sdk.model.api.BaseItemDto

@Composable
@Stable
fun PhotoPlayerHeader(
	item: BaseItemDto?,
) {
	PlayerHeader {
		if (item != null) {
			Text(
				text = item.name.orEmpty(),
				overflow = TextOverflow.Ellipsis,
				maxLines = 1,
				style = LocalTextStyle.current.copy(
					color = Color.White,
					fontSize = 22.sp
				)
			)

			item.album?.let { album ->
				Text(
					text = album,
					overflow = TextOverflow.Ellipsis,
					maxLines = 1,
					style = LocalTextStyle.current.copy(
						color = Color.White,
						fontSize = 18.sp
					)
				)
			}
		}
	}
}
