package org.khylo.tv.core.ui.player.photo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import org.khylo.tv.core.ui.composable.AsyncImage
import org.khylo.tv.core.util.apiclient.getUrl
import org.khylo.tv.core.util.apiclient.itemImages
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.ImageType
import org.koin.compose.koinInject

@Composable
fun PhotoPlayerContent(
	item: BaseItemDto?,
) {
	val api = koinInject<ApiClient>()
	val resources = LocalResources.current

	AnimatedContent(
		targetState = item,
		transitionSpec = {
			fadeIn() togetherWith fadeOut()
		}
	) { item ->
		val image = item?.itemImages[ImageType.PRIMARY]

		AsyncImage(
			url = image?.getUrl(
				api = api,
				maxWidth = resources.displayMetrics.widthPixels,
				maxHeight = resources.displayMetrics.heightPixels,
			),
			blurHash = image?.blurHash,
			aspectRatio = image?.aspectRatio ?: 1f,
			modifier = Modifier
				.fillMaxSize()
		)
	}
}
