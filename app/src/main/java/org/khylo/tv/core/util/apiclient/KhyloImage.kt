package org.khylo.tv.core.util.apiclient

import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.imageApi
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemPerson
import org.khylo.sdk.model.api.ImageType
import org.khylo.sdk.model.api.UserDto
import java.util.UUID

/**
 * Utility class used to collect information about images in Khylo API responses to easily pass around the app.
 */
data class KhyloImage(
	val item: UUID,
	val source: KhyloImageSource,
	val type: ImageType,
	val tag: String,
	val blurHash: String?,
	val aspectRatio: Float?,
	val index: Int?,
)

fun KhyloImage.getUrl(
	api: ApiClient,
	maxWidth: Int? = null,
	maxHeight: Int? = null,
	fillWidth: Int? = null,
	fillHeight: Int? = null,
): String = when (source) {
	KhyloImageSource.USER -> api.imageApi.getUserImageUrl(
		userId = item,
		tag = tag,
	)

	else -> api.imageApi.getItemImageUrl(
		itemId = item,
		imageType = type,
		tag = tag,
		imageIndex = index,
		maxWidth = maxWidth,
		maxHeight = maxHeight,
		fillWidth = fillWidth,
		fillHeight = fillHeight,
	)
}

enum class KhyloImageSource {
	ITEM,
	PARENT,
	ALBUM,
	SERIES,
	CHANNEL,
	USER,
	CHAPTER,
}

// UserDto
val UserDto.primaryImage
	get() = primaryImageTag?.let { primaryImageTag ->
		KhyloImage(
			item = id,
			source = KhyloImageSource.USER,
			type = ImageType.PRIMARY,
			tag = primaryImageTag,
			blurHash = null,
			aspectRatio = primaryImageAspectRatio?.toFloat(),
			index = null,
		)
	}

// BaseItemDto

val BaseItemDto.itemImages
	get() = imageTags?.mapValues { (type, tag) ->
		KhyloImage(
			item = id,
			source = KhyloImageSource.ITEM,
			type = type,
			tag = tag,
			blurHash = imageBlurHashes?.get(type)?.get(tag),
			aspectRatio = if (type == ImageType.PRIMARY) primaryImageAspectRatio?.toFloat() else null,
			index = null,
		)
	}.orEmpty()

val BaseItemDto.itemBackdropImages
	get() = backdropImageTags?.mapIndexed { index, tag ->
		KhyloImage(
			item = id,
			source = KhyloImageSource.ITEM,
			type = ImageType.BACKDROP,
			tag = tag,
			blurHash = imageBlurHashes?.get(ImageType.BACKDROP)?.get(tag),
			aspectRatio = null,
			index = index,
		)
	}.orEmpty()

val BaseItemDto.parentImages
	get() = mapOf(
		ImageType.PRIMARY to (parentPrimaryImageItemId to parentPrimaryImageTag),
		ImageType.LOGO to (parentLogoItemId to parentLogoImageTag),
		ImageType.ART to (parentArtItemId to parentArtImageTag),
		ImageType.THUMB to (parentThumbItemId to parentThumbImageTag),
	).mapNotNull { (type, itemAndTag) ->
		itemAndTag.first?.let { item ->
			itemAndTag.second?.let { tag ->
				KhyloImage(
					item = item,
					source = KhyloImageSource.PARENT,
					type = type,
					tag = tag,
					blurHash = imageBlurHashes?.get(type)?.get(tag),
					aspectRatio = null,
					index = null,
				)
			}
		}
	}.associateBy { it.type }

val BaseItemDto.parentBackdropImages
	get() = parentBackdropItemId?.let { parentBackdropItemId ->
		parentBackdropImageTags?.mapIndexed { index, tag ->
			KhyloImage(
				item = parentBackdropItemId,
				source = KhyloImageSource.PARENT,
				type = ImageType.BACKDROP,
				tag = tag,
				blurHash = imageBlurHashes?.get(ImageType.BACKDROP)?.get(tag),
				aspectRatio = null,
				index = index,
			)
		}
	}.orEmpty()

val BaseItemDto.albumPrimaryImage
	get() = albumPrimaryImageTag?.let { albumPrimaryImageTag ->
		albumId?.let { albumId ->
			KhyloImage(
				item = albumId,
				source = KhyloImageSource.ALBUM,
				type = ImageType.PRIMARY,
				tag = albumPrimaryImageTag,
				blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.get(albumPrimaryImageTag),
				aspectRatio = null,
				index = null,
			)
		}
	}

val BaseItemDto.channelPrimaryImage
	get() = channelPrimaryImageTag?.let { channelPrimaryImageTag ->
		channelId?.let { channelId ->
			KhyloImage(
				item = channelId,
				source = KhyloImageSource.CHANNEL,
				type = ImageType.PRIMARY,
				tag = channelPrimaryImageTag,
				blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.get(channelPrimaryImageTag),
				aspectRatio = null,
				index = null,
			)
		}
	}

val BaseItemDto.seriesPrimaryImage
	get() = seriesPrimaryImageTag?.let { seriesPrimaryImageTag ->
		seriesId?.let { seriesId ->
			KhyloImage(
				item = seriesId,
				source = KhyloImageSource.SERIES,
				type = ImageType.PRIMARY,
				tag = seriesPrimaryImageTag,
				blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.get(seriesPrimaryImageTag),
				aspectRatio = null,
				index = null,
			)
		}
	}

val BaseItemDto.seriesThumbImage
	get() = seriesThumbImageTag?.let { seriesThumbImageTag ->
		seriesId?.let { seriesId ->
			KhyloImage(
				item = seriesId,
				source = KhyloImageSource.SERIES,
				type = ImageType.THUMB,
				tag = seriesThumbImageTag,
				blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.get(seriesThumbImageTag),
				aspectRatio = null,
				index = null,
			)
		}
	}

val BaseItemDto.chapterImages
	get() = chapters?.mapIndexed { index, chapter ->
		KhyloImage(
			item = id,
			source = KhyloImageSource.CHAPTER,
			type = ImageType.CHAPTER,
			tag = chapter.imageTag.orEmpty(),
			blurHash = null,
			aspectRatio = null,
			index = index,
		)
	}.orEmpty()

val BaseItemDto.images
	get() = listOfNotNull(
		itemImages.values,
		itemBackdropImages,
		parentImages.values,
		parentBackdropImages,
		listOfNotNull(albumPrimaryImage),
		listOfNotNull(channelPrimaryImage),
		listOfNotNull(seriesPrimaryImage),
		listOfNotNull(seriesThumbImage),
		chapterImages,
	).flatten()

// BaseItemPerson

val BaseItemPerson.primaryImage
	get() = primaryImageTag?.let { primaryImageTag ->
		KhyloImage(
			item = id,
			source = KhyloImageSource.ITEM,
			type = ImageType.PRIMARY,
			tag = primaryImageTag,
			blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.get(primaryImageTag),
			aspectRatio = null,
			index = null,
		)
	}

val BaseItemPerson.images
	get() = listOfNotNull(primaryImage)

// TODO Add SeriesTimerInfoDto once API types are fixed (correct nullability, UUID types) and blurhashes are added
