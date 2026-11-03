package org.khylo.tv.playback.khylo.queue

import org.khylo.tv.playback.core.mediastream.mediatype.mediaType
import org.khylo.tv.playback.core.mediastream.normalizationGain
import org.khylo.tv.playback.core.queue.QueueEntry
import org.khylo.tv.playback.core.queue.QueueEntryMetadata
import org.khylo.tv.playback.core.queue.metadata
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.imageApi
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.ImageType
import org.khylo.sdk.model.extensions.ticks
import java.util.UUID
import org.khylo.tv.playback.core.mediastream.mediatype.MediaType as PlayerMediaType
import org.khylo.sdk.model.api.MediaType as SdkMediaType

/**
 * Create a [QueueEntry] from a [BaseItemDto].
 */
fun createBaseItemQueueEntry(api: ApiClient, baseItem: BaseItemDto): QueueEntry {
	val entry = QueueEntry()
	entry.metadata = QueueEntryMetadata(
		mediaId = baseItem.id.toString(),
		duration = baseItem.runTimeTicks?.ticks,
		title = baseItem.name,
		artist = baseItem.albumArtist,
		albumTitle = baseItem.album,
		albumArtist = baseItem.albumArtists
			?.mapNotNull { it.name }
			?.joinToString(", "),
		displayTitle = baseItem.name,
		description = baseItem.overview,
		artworkUri = when {
			baseItem.imageTags?.containsKey(ImageType.PRIMARY) == true -> api.getImageUri(
				itemId = baseItem.id,
				tag = baseItem.imageTags!![ImageType.PRIMARY]
			)

			baseItem.albumPrimaryImageTag != null -> api.getImageUri(
				itemId = baseItem.albumId ?: baseItem.id,
				tag = baseItem.albumPrimaryImageTag,
			)

			else -> null
		},
		trackNumber = baseItem.indexNumber,
		releaseDate = baseItem.premiereDate?.toLocalDate(),
		genre = baseItem.genres?.joinToString(", "),
	)
	entry.baseItem = baseItem
	entry.normalizationGain = baseItem.normalizationGain
	entry.mediaType = when (baseItem.mediaType) {
		SdkMediaType.VIDEO -> PlayerMediaType.Video
		SdkMediaType.AUDIO -> PlayerMediaType.Audio
		SdkMediaType.PHOTO,
		SdkMediaType.BOOK,
		SdkMediaType.UNKNOWN -> PlayerMediaType.Unknown
	}
	return entry
}

private fun ApiClient.getImageUri(itemId: UUID?, tag: String?): String? = when {
	// Invalid item id / tag
	itemId == null || tag == null -> null
	// Valid item id & tag
	else -> imageApi.getItemImageUrl(
		itemId = itemId,
		imageType = ImageType.PRIMARY,
		tag = tag,
	)
}
