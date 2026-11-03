package org.khylo.tv.core.ui.navigation

import kotlinx.serialization.json.Json
import org.khylo.tv.core.constant.Extras
import org.khylo.tv.core.ui.browsing.BrowseGridFragment
import org.khylo.tv.core.ui.browsing.BrowseRecordingsFragment
import org.khylo.tv.core.ui.browsing.BrowseScheduleFragment
import org.khylo.tv.core.ui.browsing.BrowseViewFragment
import org.khylo.tv.core.ui.browsing.ByGenreFragment
import org.khylo.tv.core.ui.browsing.ByLetterFragment
import org.khylo.tv.core.ui.browsing.CollectionFragment
import org.khylo.tv.core.ui.browsing.GenericFolderFragment
import org.khylo.tv.core.ui.browsing.SuggestedMoviesFragment
import org.khylo.tv.core.ui.home.HomeFragment
import org.khylo.tv.core.ui.itemdetail.FullDetailsFragment
import org.khylo.tv.core.ui.itemdetail.ItemListFragment
import org.khylo.tv.core.ui.itemdetail.MusicFavoritesListFragment
import org.khylo.tv.core.ui.livetv.LiveTvGuideFragment
import org.khylo.tv.core.ui.playback.AudioNowPlayingFragment
import org.khylo.tv.core.ui.playback.CustomPlaybackOverlayFragment
import org.khylo.tv.core.ui.playback.nextup.NextUpFragment
import org.khylo.tv.core.ui.playback.stillwatching.StillWatchingFragment
import org.khylo.tv.core.ui.player.photo.PhotoPlayerFragment
import org.khylo.tv.core.ui.player.video.VideoPlayerFragment
import org.khylo.tv.core.ui.search.SearchFragment
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.ItemSortBy
import org.khylo.sdk.model.api.SeriesTimerInfoDto
import org.khylo.sdk.model.api.SortOrder
import java.util.UUID

@Suppress("TooManyFunctions")
object Destinations {
	// General
	val home = fragmentDestination<HomeFragment>()
	fun search(query: String? = null) = fragmentDestination<SearchFragment> {
		putString(SearchFragment.EXTRA_QUERY, query)
	}

	// Browsing
	// TODO only pass item id instead of complete JSON to browsing destinations
	fun libraryBrowser(item: BaseItemDto, includeType: String? = null) =
		fragmentDestination<BrowseGridFragment> {
			putString(Extras.Folder, Json.encodeToString(item))
			putString(Extras.IncludeType, includeType)
		}

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun librarySmartScreen(item: BaseItemDto) = fragmentDestination<BrowseViewFragment> {
		putString(Extras.Folder, Json.encodeToString(item))
	}

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun collectionBrowser(item: BaseItemDto) = fragmentDestination<CollectionFragment> {
		putString(Extras.Folder, Json.encodeToString(item))
	}

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun folderBrowser(item: BaseItemDto) = fragmentDestination<GenericFolderFragment> {
		putString(Extras.Folder, Json.encodeToString(item))
	}

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun libraryByGenres(item: BaseItemDto, includeType: String) =
		fragmentDestination<ByGenreFragment> {
			putString(Extras.Folder, Json.encodeToString(item))
			putString(Extras.IncludeType, includeType)
		}

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun libraryByLetter(item: BaseItemDto, includeType: String) =
		fragmentDestination<ByLetterFragment> {
			putString(Extras.Folder, Json.encodeToString(item))
			putString(Extras.IncludeType, includeType)
		}

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun librarySuggestions(item: BaseItemDto) =
		fragmentDestination<SuggestedMoviesFragment> {
			putString(Extras.Folder, Json.encodeToString(item))
		}

	// Item details
	fun itemDetails(item: UUID) = fragmentDestination<FullDetailsFragment> {
		putString("ItemId", item.toString())
	}

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun channelDetails(item: UUID, channel: UUID, programInfo: BaseItemDto) =
		fragmentDestination<FullDetailsFragment> {
			putString("ItemId", item.toString())
			putString("ChannelId", channel.toString())
			putString("ProgramInfo", Json.encodeToString(programInfo))
		}

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun seriesTimerDetails(item: UUID, seriesTimer: SeriesTimerInfoDto) =
		fragmentDestination<FullDetailsFragment> {
			putString("ItemId", item.toString())
			putString("SeriesTimer", Json.encodeToString(seriesTimer))
		}

	fun itemList(item: UUID) = fragmentDestination<ItemListFragment> {
		putString("ItemId", item.toString())
	}

	fun musicFavorites(parent: UUID) = fragmentDestination<MusicFavoritesListFragment> {
		putString("ParentId", parent.toString())
	}

	// Live TV
	val liveTvGuide = fragmentDestination<LiveTvGuideFragment>()
	val liveTvSchedule = fragmentDestination<BrowseScheduleFragment>()
	val liveTvRecordings = fragmentDestination<BrowseRecordingsFragment>()
	val liveTvSeriesRecordings = fragmentDestination<BrowseViewFragment> {
		putBoolean(Extras.IsLiveTvSeriesRecordings, true)
	}

	// Playback
	val nowPlaying = fragmentDestination<AudioNowPlayingFragment>()

	fun photoPlayer(
		item: UUID,
		autoPlay: Boolean,
		albumSortBy: ItemSortBy?,
		albumSortOrder: SortOrder?,
	) = fragmentDestination<PhotoPlayerFragment> {
		putString(PhotoPlayerFragment.ARGUMENT_ITEM_ID, item.toString())
		putString(PhotoPlayerFragment.ARGUMENT_ALBUM_SORT_BY, albumSortBy?.serialName)
		putString(PhotoPlayerFragment.ARGUMENT_ALBUM_SORT_ORDER, albumSortOrder?.serialName)
		putBoolean(PhotoPlayerFragment.ARGUMENT_AUTO_PLAY, autoPlay)
	}

	fun videoPlayer(position: Int?) = fragmentDestination<CustomPlaybackOverlayFragment> {
		putInt("Position", position ?: 0)
	}

	fun videoPlayerNew(position: Int?) = fragmentDestination<VideoPlayerFragment> {
		putInt(VideoPlayerFragment.EXTRA_POSITION, position ?: 0)
	}

	fun nextUp(item: UUID) = fragmentDestination<NextUpFragment> {
		putString(NextUpFragment.ARGUMENT_ITEM_ID, item.toString())
	}

	fun stillWatching(item: UUID) = fragmentDestination<StillWatchingFragment> {
		putString(NextUpFragment.ARGUMENT_ITEM_ID, item.toString())
	}
}
