package org.khylo.tv.core.ui.itemhandling

import android.content.Context
import org.khylo.tv.core.util.apiclient.KhyloImage
import org.khylo.tv.core.util.apiclient.albumPrimaryImage
import org.khylo.tv.core.util.apiclient.itemImages
import org.khylo.tv.core.util.apiclient.parentImages
import org.khylo.tv.core.util.apiclient.seriesPrimaryImage
import org.khylo.tv.core.util.apiclient.seriesThumbImage
import org.khylo.tv.core.util.getTimeFormatter
import org.khylo.tv.core.util.locale
import org.khylo.tv.core.util.sdk.getFullName
import org.khylo.tv.core.util.sdk.getSubName
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.ImageType
import java.time.format.DateTimeFormatter
import org.khylo.tv.core.constant.ImageType as BaseRowImageType

open class BaseItemDtoBaseRowItem @JvmOverloads constructor(
	item: BaseItemDto,
	preferParentThumb: Boolean = false,
	staticHeight: Boolean = false,
	selectAction: BaseRowItemSelectAction = BaseRowItemSelectAction.ShowDetails,
	val preferSeriesPoster: Boolean = false,
) : BaseRowItem(
	baseRowType = when (item.type) {
		BaseItemKind.TV_CHANNEL,
		BaseItemKind.LIVE_TV_CHANNEL -> BaseRowType.LiveTvChannel

		BaseItemKind.PROGRAM,
		BaseItemKind.TV_PROGRAM,
		BaseItemKind.LIVE_TV_PROGRAM -> BaseRowType.LiveTvProgram

		BaseItemKind.RECORDING -> BaseRowType.LiveTvRecording
		else -> BaseRowType.BaseItem
	},
	staticHeight = staticHeight,
	preferParentThumb = preferParentThumb,
	selectAction = selectAction,
	baseItem = item,
) {
	override val showCardInfoOverlay
		get() = when (baseItem?.type) {
			BaseItemKind.FOLDER,
			BaseItemKind.PHOTO_ALBUM,
			BaseItemKind.USER_VIEW,
			BaseItemKind.COLLECTION_FOLDER,
			BaseItemKind.PHOTO,
			BaseItemKind.VIDEO,
			BaseItemKind.PERSON,
			BaseItemKind.PLAYLIST,
			BaseItemKind.MUSIC_ARTIST -> true

			else -> false
		}

	override val itemId get() = baseItem?.id

	override val isFavorite get() = baseItem?.userData?.isFavorite == true
	override val isPlayed get() = baseItem?.userData?.played == true

	override fun getCardName(context: Context) = when {
		baseItem?.type == BaseItemKind.AUDIO && baseItem.artists != null -> baseItem.artists?.joinToString(", ")
		baseItem?.type == BaseItemKind.AUDIO && baseItem.albumArtists != null -> baseItem.albumArtists?.joinToString(", ")
		baseItem?.type == BaseItemKind.AUDIO && baseItem.albumArtist != null -> baseItem.albumArtist
		baseItem?.type == BaseItemKind.AUDIO && baseItem.album != null -> baseItem.album
		else -> baseItem?.getFullName(context)
	}

	override fun getFullName(context: Context) = baseItem?.getFullName(context)
	override fun getName(context: Context) = when (baseItem?.type) {
		BaseItemKind.AUDIO -> baseItem.getFullName(context)
		else -> baseItem?.name
	}

	override fun getSummary(context: Context) = baseItem?.overview

	override fun getSubText(context: Context) = when (baseItem?.type) {
		BaseItemKind.TV_CHANNEL -> baseItem.number
		BaseItemKind.TV_PROGRAM,
		BaseItemKind.PROGRAM -> baseItem.episodeTitle ?: baseItem.channelName

		BaseItemKind.RECORDING -> {
			val title = listOfNotNull(
				baseItem.channelName,
				baseItem.episodeTitle
			).joinToString(" - ")

			val timestamp = buildString {
				append(DateTimeFormatter.ofPattern("d MMM", context.locale).format(baseItem.startDate))
				append(" ")
				append(context.getTimeFormatter().format(baseItem.startDate))
				append(" - ")
				append(context.getTimeFormatter().format(baseItem.endDate))
			}

			"$title $timestamp"
		}

		else -> baseItem?.getSubName(context)
	}

	override fun getImage(imageType: BaseRowImageType): KhyloImage? {
		val primaryImage = when {
			preferSeriesPoster && baseItem?.type == BaseItemKind.EPISODE -> baseItem.parentImages[ImageType.PRIMARY]
				?: baseItem.seriesPrimaryImage

			preferParentThumb && baseItem?.type == BaseItemKind.EPISODE -> baseItem.parentImages[ImageType.THUMB]
				?: baseItem.seriesThumbImage

			baseItem?.type == BaseItemKind.SEASON -> baseItem.itemImages[ImageType.PRIMARY]
				?: baseItem.seriesPrimaryImage
			baseItem?.type == BaseItemKind.PROGRAM -> baseItem.itemImages[ImageType.THUMB]
			baseItem?.type == BaseItemKind.AUDIO -> baseItem.albumPrimaryImage
			else -> null
		} ?: baseItem?.itemImages[ImageType.PRIMARY]

		return when (imageType) {
			BaseRowImageType.BANNER -> baseItem?.itemImages[ImageType.BANNER] ?: primaryImage
			BaseRowImageType.THUMB -> baseItem?.itemImages[ImageType.THUMB] ?: primaryImage
			else -> primaryImage
		}
	}

	override fun equals(other: Any?): Boolean {
		if (other is BaseItemDtoBaseRowItem) return other.baseItem == baseItem
		return super.equals(other)
	}
}
