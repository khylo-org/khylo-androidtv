package org.khylo.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.genresApi
import org.khylo.sdk.api.client.extensions.imageApi
import org.khylo.sdk.api.client.extensions.itemsApi
import org.khylo.sdk.api.client.extensions.libraryApi
import org.khylo.sdk.api.client.extensions.mediaSegmentsApi
import org.khylo.sdk.api.client.extensions.musicGenresApi
import org.khylo.sdk.api.client.extensions.liveTvApi
import org.khylo.sdk.model.api.TimerInfoDto
import org.khylo.sdk.api.client.extensions.personsApi
import org.khylo.sdk.api.client.extensions.playStateApi
import org.khylo.sdk.api.client.extensions.systemApi
import org.khylo.sdk.api.client.extensions.tvShowsApi
import org.khylo.sdk.api.client.extensions.userApi
import org.khylo.sdk.api.client.extensions.userLibraryApi
import org.khylo.sdk.api.client.extensions.userViewsApi
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.CollectionType
import org.khylo.sdk.model.api.ImageType
import org.khylo.sdk.model.api.ItemFields
import org.khylo.sdk.model.api.ItemSortBy
import org.khylo.sdk.model.api.MediaType
import org.khylo.sdk.model.api.SortOrder
import org.khylo.sdk.model.api.BaseItemPerson
import org.khylo.sdk.model.api.UpdateUserItemDataDto
import org.khylo.sdk.model.api.UserConfiguration
import java.time.LocalDateTime
import java.util.UUID

/** A titled row of items on a browse screen. */
data class KhyloRow(val title: String, val items: List<BaseItemDto>, val wide: Boolean = false)

data class ChannelPage(val channels: List<BaseItemDto>, val total: Int)

/**
 * Continue watching lists every started episode; a show should appear once, with the episode
 * you watched most recently (the server lists those first).
 */
internal fun List<BaseItemDto>.oneEpisodePerShow(): List<BaseItemDto> = distinctBy { it.seriesId ?: it.id }

/** Results of a search inside one library (music also has artists and songs). */
data class LibrarySearchResults(
	val main: List<BaseItemDto> = emptyList(),
	val artists: List<BaseItemDto> = emptyList(),
	val songs: List<BaseItemDto> = emptyList(),
) {
	val isEmpty: Boolean get() = main.isEmpty() && artists.isEmpty() && songs.isEmpty()
}

/** Search results, by kind. */
data class SearchResults(
	val titles: List<BaseItemDto> = emptyList(),
	val episodes: List<BaseItemDto> = emptyList(),
	val channels: List<BaseItemDto> = emptyList(),
	val people: List<BaseItemDto> = emptyList(),
	val music: List<BaseItemDto> = emptyList(),
	val books: List<BaseItemDto> = emptyList(),
) {
	val isEmpty: Boolean get() = titles.isEmpty() && episodes.isEmpty() && channels.isEmpty() && people.isEmpty() && music.isEmpty() && books.isEmpty()
}

/**
 * How well a name matches the search words (lower is better), or null when a word is missing.
 * A leading "The" is ignored, so "office" finds "The Office" as a title match.
 */
internal fun searchScore(name: String, words: List<String>): Int? {
	val full = name.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex(" +"), " ").trim()
	val title = full.removePrefix("the ")
	val phrase = words.joinToString(" ")
	val nameWords = full.split(' ')
	if (words.any { word -> nameWords.none { it.startsWith(word) } && !full.contains(word) }) return null
	return when {
		title == phrase || full == phrase -> 0
		title.startsWith(phrase) || full.startsWith(phrase) -> 1
		words.all { word -> nameWords.any { it.startsWith(word) } } -> 2
		else -> 3
	}
}

/**
 * Everything Khylo's screens load from the server. Calls run off the main thread and return
 * plain lists, so screens stay simple.
 */
class KhyloRepository(private val api: ApiClient) {
	private val cardFields = listOf(
		ItemFields.OVERVIEW,
		ItemFields.GENRES,
		ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
		ItemFields.TAGLINES,
		ItemFields.PEOPLE,
	)

	// ---- Images ---------------------------------------------------------------------------

	fun imageUrl(item: BaseItemDto?, type: ImageType = ImageType.PRIMARY, maxWidth: Int = 480): String? {
		item ?: return null
		val tag = when (type) {
			ImageType.BACKDROP -> item.backdropImageTags?.firstOrNull()
			else -> item.imageTags?.get(type)
		} ?: return null
		return api.imageApi.getItemImageUrl(itemId = item.id, imageType = type, tag = tag, maxWidth = maxWidth)
	}

	/** Landscape art for an item: its backdrop, or its series' / parent's when it has none. */
	fun backdropUrl(item: BaseItemDto?, maxWidth: Int = 1920): String? {
		item ?: return null
		imageUrl(item, ImageType.BACKDROP, maxWidth)?.let { return it }
		val parentId = item.parentBackdropItemId
		val parentTag = item.parentBackdropImageTags?.firstOrNull()
		if (parentId != null && parentTag != null) {
			return api.imageApi.getItemImageUrl(itemId = parentId, imageType = ImageType.BACKDROP, tag = parentTag, maxWidth = maxWidth)
		}
		return null
	}

	/** Wide card art for continue watching: episode still, or the show's backdrop/thumb. */
	fun thumbUrl(item: BaseItemDto?, maxWidth: Int = 640): String? {
		item ?: return null
		if (item.type == BaseItemKind.EPISODE) imageUrl(item, ImageType.PRIMARY, maxWidth)?.let { return it }
		imageUrl(item, ImageType.THUMB, maxWidth)?.let { return it }
		return backdropUrl(item, maxWidth) ?: imageUrl(item, ImageType.PRIMARY, maxWidth)
	}

	fun logoUrl(item: BaseItemDto?, maxWidth: Int = 700): String? = imageUrl(item, ImageType.LOGO, maxWidth)

	/** An image of any item by id (the tag, when known, lets it be cached). */
	fun imageUrlFor(itemId: UUID, tag: String?, maxWidth: Int = 480, type: ImageType = ImageType.PRIMARY): String =
		api.imageApi.getItemImageUrl(itemId = itemId, imageType = type, tag = tag, maxWidth = maxWidth)

	/** The logo of a show (for an episode) or of the item itself. */
	fun showLogoUrl(item: BaseItemDto?, maxWidth: Int = 500): String? {
		item ?: return null
		logoUrl(item, maxWidth)?.let { return it }
		val parentId = item.parentLogoItemId ?: return null
		val tag = item.parentLogoImageTag ?: return null
		return api.imageApi.getItemImageUrl(itemId = parentId, imageType = ImageType.LOGO, tag = tag, maxWidth = maxWidth)
	}

	fun personImageUrl(person: BaseItemPerson, maxWidth: Int = 300): String? {
		val tag = person.primaryImageTag ?: return null
		return api.imageApi.getItemImageUrl(itemId = person.id, imageType = ImageType.PRIMARY, tag = tag, maxWidth = maxWidth)
	}

	// ---- Home -----------------------------------------------------------------------------

	suspend fun libraries(): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.userViewsApi.getUserViews(includeHidden = false).content.items
	}

	suspend fun homeRows(): List<KhyloRow> = withContext(Dispatchers.IO) {
		coroutineScope {
			val resume = async {
				runCatching {
					api.itemsApi.getResumeItems(
						limit = 20,
						fields = cardFields,
						imageTypeLimit = 1,
						mediaTypes = listOf(MediaType.VIDEO),
						enableTotalRecordCount = false,
					).content.items.oneEpisodePerShow()
				}.getOrDefault(emptyList())
			}
			val nextUp = async {
				val hidden = UpNextHidden.load(api)
				runCatching {
					api.tvShowsApi.getNextUp(limit = 20, fields = cardFields, imageTypeLimit = 1, enableResumable = false).content.items
						.filter { it.seriesId !in hidden }
				}.getOrDefault(emptyList())
			}
			val views = async { runCatching { libraries() }.getOrDefault(emptyList()) }
			val latest = views.await()
				.filter { it.collectionType == CollectionType.MOVIES || it.collectionType == CollectionType.TVSHOWS }
				.map { view ->
					async {
						val items = runCatching {
							api.userLibraryApi.getLatestMedia(parentId = view.id, limit = 20, fields = cardFields, imageTypeLimit = 1, groupItems = true).content
						}.getOrDefault(emptyList())
						KhyloRow("Recently added in ${view.name}", items)
					}
				}

			buildList {
				val continueItems = resume.await()
				if (continueItems.isNotEmpty()) add(KhyloRow("Continue watching", continueItems, wide = true))
				val upNext = nextUp.await().filter { next -> continueItems.none { it.seriesId == next.seriesId } }
				if (upNext.isNotEmpty()) add(KhyloRow("Up next", upNext, wide = true))
				latest.forEach { row -> row.await().takeIf { it.items.isNotEmpty() }?.let(::add) }
			}
		}
	}

	/** A few titles worth featuring at the top of Home. */
	suspend fun featured(): List<BaseItemDto> = withContext(Dispatchers.IO) {
		runCatching {
			api.itemsApi.getItems(
				includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
				recursive = true,
				sortBy = listOf(ItemSortBy.RANDOM),
				imageTypes = listOf(ImageType.BACKDROP),
				fields = cardFields,
				limit = 6,
				enableTotalRecordCount = false,
			).content.items
		}.getOrDefault(emptyList())
	}

	// ---- Libraries ------------------------------------------------------------------------

	private fun typesFor(library: BaseItemDto): List<BaseItemKind>? = if (org.khylo.tv.app.isAudiobookLibrary(library)) {
		// Every book in the library, however it's foldered; the server lists a book in several files once
		listOf(BaseItemKind.AUDIO_BOOK)
	} else when (library.collectionType) {
		CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
		CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
		CollectionType.BOXSETS -> listOf(BaseItemKind.BOX_SET)
		CollectionType.MUSIC -> listOf(BaseItemKind.MUSIC_ALBUM)
		else -> null
	}

	suspend fun libraryItems(
		library: BaseItemDto,
		startIndex: Int,
		limit: Int,
		sortBy: ItemSortBy = ItemSortBy.SORT_NAME,
		genre: BaseItemDto? = null,
	): Pair<List<BaseItemDto>, Int> = withContext(Dispatchers.IO) {
		val byName = genre?.type == BaseItemKind.MUSIC_GENRE
		val types = typesFor(library)
		val response = api.itemsApi.getItems(
			parentId = library.id,
			includeItemTypes = types,
			recursive = types != null,
			genreIds = genre?.id?.takeUnless { byName }?.let { listOf(it) },
			genres = genre?.name?.takeIf { byName }?.let { listOf(it) },
			sortBy = listOf(sortBy, ItemSortBy.SORT_NAME),
			sortOrder = listOf(if (sortBy == ItemSortBy.SORT_NAME) SortOrder.ASCENDING else SortOrder.DESCENDING),
			fields = cardFields,
			imageTypeLimit = 1,
			startIndex = startIndex,
			limit = limit,
		).content
		response.items to response.totalRecordCount
	}

	/**
	 * A Movies or TV library's Recommended page, the way Plex does it: what you're in the middle
	 * of, what's new, the best rated, things you haven't seen, then a few categories.
	 */
	suspend fun libraryRows(view: BaseItemDto): List<KhyloRow> = withContext(Dispatchers.IO) {
		coroutineScope {
			val tv = view.collectionType == CollectionType.TVSHOWS
			val type = if (tv) BaseItemKind.SERIES else BaseItemKind.MOVIE
			fun row(
				title: String,
				sort: ItemSortBy,
				isPlayed: Boolean? = null,
				genre: UUID? = null,
				minRating: Double? = null,
			) = async {
				runCatching {
					KhyloRow(
						title,
						api.itemsApi.getItems(
							parentId = view.id,
							includeItemTypes = listOf(type),
							recursive = true,
							sortBy = listOf(sort),
							sortOrder = listOf(SortOrder.DESCENDING),
							isPlayed = isPlayed,
							genreIds = genre?.let { listOf(it) },
							minCommunityRating = minRating,
							fields = cardFields,
							imageTypeLimit = 1,
							limit = 20,
							enableTotalRecordCount = false,
						).content.items,
					)
				}.getOrNull()
			}

			val resume = async {
				runCatching {
					KhyloRow("Continue watching", api.itemsApi.getResumeItems(parentId = view.id, limit = 20, fields = cardFields, imageTypeLimit = 1, enableTotalRecordCount = false).content.items.oneEpisodePerShow(), wide = true)
				}.getOrNull()
			}
			val nextUp = async {
				if (!tv) return@async null
				val hidden = UpNextHidden.load(api)
				runCatching {
					KhyloRow("Up next", api.tvShowsApi.getNextUp(parentId = view.id, limit = 20, fields = cardFields, imageTypeLimit = 1, enableResumable = false).content.items.filter { it.seriesId !in hidden }, wide = true)
				}.getOrNull()
			}
			val latest = async {
				runCatching {
					KhyloRow("Recently added", api.userLibraryApi.getLatestMedia(parentId = view.id, limit = 20, fields = cardFields, imageTypeLimit = 1, groupItems = true).content)
				}.getOrNull()
			}
			val fresh = if (tv) row("New episodes", ItemSortBy.DATE_LAST_CONTENT_ADDED) else row("Recently released", ItemSortBy.PREMIERE_DATE)
			val top = row("Top rated", ItemSortBy.COMMUNITY_RATING, minRating = 7.0)
			val unseen = row(if (tv) "Shows you haven't started" else "You haven't seen these", ItemSortBy.RANDOM, isPlayed = false)
			// A few categories, different each visit
			val genreRows = runCatching { genres(view) }.getOrDefault(emptyList())
				.shuffled()
				.take(4)
				.map { genre -> row(genre.name.orEmpty(), ItemSortBy.RANDOM, genre = genre.id) }

			(listOf(resume, nextUp, latest, fresh, top, unseen) + genreRows)
				.mapNotNull { it.await() }
				.filter { it.items.isNotEmpty() }
		}
	}

	/**
	 * A standout title for each category, picked from its best-rated titles that have official
	 * landscape art. No title fronts two categories: each takes the best one not already used.
	 */
	suspend fun genreFeatures(view: BaseItemDto, genres: List<BaseItemDto>): Map<UUID, BaseItemDto> = withContext(Dispatchers.IO) {
		val candidates = coroutineScope {
			genres.map { genre ->
				async {
					genre.id to runCatching {
						api.itemsApi.getItems(
							parentId = view.id,
							includeItemTypes = typesFor(view),
							recursive = true,
							genreIds = listOf(genre.id),
							imageTypes = listOf(ImageType.THUMB),
							sortBy = listOf(ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME),
							sortOrder = listOf(SortOrder.DESCENDING),
							limit = 20,
							fields = cardFields,
							enableTotalRecordCount = false,
						).content.items
					}.getOrDefault(emptyList())
				}
			}.awaitAll()
		}
		// Genres with fewer options choose first, so every tile gets a title of its own
		val used = mutableSetOf<UUID>()
		buildMap {
			candidates.sortedBy { it.second.size }.forEach { (genreId, items) ->
				val pick = items.firstOrNull { it.id !in used } ?: items.firstOrNull()
				if (pick != null) {
					used += pick.id
					put(genreId, pick)
				}
			}
		}
	}

	/** Covers of the newest titles in a library, for a library tile that has no artwork of its own. */
	suspend fun libraryCovers(library: BaseItemDto, limit: Int = 3): List<BaseItemDto> = withContext(Dispatchers.IO) {
		runCatching {
			api.itemsApi.getItems(
				parentId = library.id,
				recursive = true,
				includeItemTypes = listOf(
					BaseItemKind.AUDIO_BOOK, BaseItemKind.MUSIC_ALBUM, BaseItemKind.BOOK, BaseItemKind.SERIES,
					BaseItemKind.MOVIE, BaseItemKind.BOX_SET, BaseItemKind.FOLDER, BaseItemKind.PHOTO_ALBUM,
				),
				imageTypes = listOf(ImageType.PRIMARY),
				sortBy = listOf(ItemSortBy.DATE_CREATED),
				sortOrder = listOf(SortOrder.DESCENDING),
				imageTypeLimit = 1,
				limit = limit,
				enableTotalRecordCount = false,
			).content.items
		}.getOrDefault(emptyList())
	}

	/** Movie collections (box sets) on the server. */
	suspend fun collections(): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.itemsApi.getItems(
			includeItemTypes = listOf(BaseItemKind.BOX_SET),
			recursive = true,
			sortBy = listOf(ItemSortBy.SORT_NAME),
			fields = cardFields,
			imageTypeLimit = 1,
		).content.items
	}

	/** The profile's video playlists. */
	suspend fun playlists(): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.itemsApi.getItems(
			includeItemTypes = listOf(BaseItemKind.PLAYLIST),
			recursive = true,
			sortBy = listOf(ItemSortBy.SORT_NAME),
			fields = cardFields,
			imageTypeLimit = 1,
		).content.items.filter { it.mediaType != MediaType.AUDIO }
	}

	/** The categories (genres) a library's titles fall under. */
	suspend fun genres(library: BaseItemDto): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.genresApi.getGenres(
			parentId = library.id,
			includeItemTypes = typesFor(library),
			sortBy = listOf(ItemSortBy.SORT_NAME),
			enableTotalRecordCount = false,
		).content.items
	}

	suspend fun similar(item: BaseItemDto): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.libraryApi.getSimilarItems(itemId = item.id, limit = 16, fields = cardFields).content.items
	}

	/** Movies and shows a person appears in, newest first. */
	suspend fun personItems(personId: UUID): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.itemsApi.getItems(
			personIds = listOf(personId),
			recursive = true,
			includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			sortBy = listOf(ItemSortBy.PREMIERE_DATE),
			sortOrder = listOf(SortOrder.DESCENDING),
			fields = cardFields,
			imageTypeLimit = 1,
			limit = 200,
		).content.items
	}

	/** The episode after [episode] in its show, if there is one. */
	suspend fun nextEpisode(episode: BaseItemDto): BaseItemDto? = withContext(Dispatchers.IO) {
		val seriesId = episode.seriesId ?: return@withContext null
		api.tvShowsApi.getEpisodes(seriesId = seriesId, startItemId = episode.id, limit = 2, fields = cardFields).content.items
			.firstOrNull { it.id != episode.id }
	}

	/** The episode Play starts for a show: where you left off, else the next one, else the first. */
	suspend fun seriesPlayTarget(series: BaseItemDto): BaseItemDto? = withContext(Dispatchers.IO) {
		runCatching {
			api.tvShowsApi.getNextUp(seriesId = series.id, limit = 1, fields = cardFields, enableResumable = true).content.items.firstOrNull()
				?: api.tvShowsApi.getEpisodes(seriesId = series.id, limit = 1, fields = cardFields).content.items.firstOrNull()
		}.getOrNull()
	}

	suspend fun item(id: UUID): BaseItemDto = withContext(Dispatchers.IO) {
		api.userLibraryApi.getItem(itemId = id).content
	}

	/**
	 * A season's episodes in order, one per episode number. When two files claim the same episode
	 * (a bonus clip numbered like an episode), the one named like an episode (S01E01) wins.
	 */
	suspend fun seasonEpisodes(seasonId: UUID): List<BaseItemDto> = withContext(Dispatchers.IO) {
		val episodeName = Regex("""S\d+\s*E\d+""", RegexOption.IGNORE_CASE)
		api.itemsApi.getItems(
			parentId = seasonId,
			includeItemTypes = listOf(BaseItemKind.EPISODE),
			fields = cardFields + ItemFields.PATH,
			sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME),
			imageTypeLimit = 1,
		).content.items
			.groupBy { ep -> ep.indexNumber?.let { (ep.parentIndexNumber ?: 0) to it } ?: ep.id }
			.values
			.map { same -> same.firstOrNull { episodeName.containsMatchIn(it.path.orEmpty()) } ?: same.first() }
	}

	suspend fun children(parentId: UUID, types: List<BaseItemKind>? = null): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.itemsApi.getItems(
			parentId = parentId,
			includeItemTypes = types,
			fields = cardFields,
			sortBy = listOf(ItemSortBy.SORT_NAME),
			imageTypeLimit = 1,
		).content.items
	}

	/**
	 * Search, in separate passes so each kind of result comes back properly (asking the server
	 * for everything at once misses most movies and shows), then ranked here: exact titles first,
	 * then titles starting with the words, then words starting with them, then anything else.
	 */
	suspend fun search(term: String): SearchResults = withContext(Dispatchers.IO) {
		val query = term.trim()
		if (query.length < 2) return@withContext SearchResults()
		coroutineScope {
			fun items(types: List<BaseItemKind>, limit: Int) = async {
				runCatching {
					api.itemsApi.getItems(
						searchTerm = query,
						recursive = true,
						includeItemTypes = types,
						fields = cardFields,
						imageTypeLimit = 1,
						limit = limit,
					).content.items
				}.getOrDefault(emptyList())
			}
			val titles = items(listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.BOX_SET), 60)
			val episodes = items(listOf(BaseItemKind.EPISODE), 60)
			val channels = items(listOf(BaseItemKind.TV_CHANNEL), 30)
			val music = items(listOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST), 40)
			val books = items(listOf(BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK), 60)
			val people = async {
				runCatching { api.personsApi.getPersons(searchTerm = query, limit = 20).content.items }.getOrDefault(emptyList())
			}

			val words = query.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(Regex(" +")).filter { it.isNotEmpty() }
			fun rank(list: List<BaseItemDto>, name: (BaseItemDto) -> String?) = list
				.mapNotNull { item -> searchScore(name(item).orEmpty(), words)?.let { item to it } }
				.sortedWith(compareBy<Pair<BaseItemDto, Int>> { it.second }.thenByDescending { it.first.communityRating ?: 0f })
				.map { it.first }

			val titleResults = rank(titles.await()) { it.name }
			// Episodes of a show that already matched are reached through the show
			val matchedShows = titleResults.filter { it.type == BaseItemKind.SERIES }.mapNotNull { it.id }.toSet()
			SearchResults(
				titles = titleResults,
				episodes = rank(episodes.await().filter { it.seriesId !in matchedShows }) { it.name },
				channels = rank(channels.await()) { it.name },
				people = rank(people.await().filter { it.imageTags?.isNotEmpty() == true || it.name != null }) { it.name }.take(16),
				music = rank(music.await()) { it.name },
				// An audiobook comes as many parts; show each book once
				books = rank(books.await().distinctBy { if (it.type == BaseItemKind.AUDIO_BOOK) it.album ?: it.parentId?.toString() ?: it.id.toString() else it.id.toString() }) { it.album ?: it.name },
			)
		}
	}

	// ---- Watch state ----------------------------------------------------------------------

	/** Takes an item off Continue watching by clearing its resume point (its history stays). */
	/**
	 * Takes a title off Continue watching by clearing where it was stopped. For a show, every
	 * half-watched episode is cleared, since the row shows the show once for all of them.
	 */
	suspend fun removeFromContinueWatching(item: BaseItemDto) = withContext(Dispatchers.IO) {
		val seriesId = item.seriesId
		val started = if (item.type == BaseItemKind.EPISODE && seriesId != null) {
			api.itemsApi.getItems(
				parentId = seriesId,
				recursive = true,
				includeItemTypes = listOf(BaseItemKind.EPISODE),
				filters = listOf(org.khylo.sdk.model.api.ItemFilter.IS_RESUMABLE),
				enableTotalRecordCount = false,
			).content.items.map { it.id }.plus(item.id).distinct()
		} else {
			listOf(item.id)
		}
		started.forEach { id ->
			api.itemsApi.updateItemUserData(itemId = id, data = UpdateUserItemDataDto(playbackPositionTicks = 0, playedPercentage = 0.0))
		}
	}

	/**
	 * Marks a movie, episode or whole show as watched on the server (a show marks every episode), so
	 * Up next moves on to the next episode.
	 */
	suspend fun markWatched(item: BaseItemDto) = withContext(Dispatchers.IO) {
		api.playStateApi.markPlayedItem(itemId = item.id)
		WatchStateChanges.changed(item)
	}

	/** Marks a movie, episode or whole show as not watched on the server. */
	suspend fun markUnwatched(item: BaseItemDto) = withContext(Dispatchers.IO) {
		api.playStateApi.markUnplayedItem(itemId = item.id)
		WatchStateChanges.changed(item)
	}

	/** Takes a show off Up next until one of its episodes is played again. Watch history is untouched. */
	suspend fun removeFromUpNext(item: BaseItemDto) = withContext(Dispatchers.IO) {
		val seriesId = item.seriesId ?: return@withContext
		UpNextHidden.hide(api, seriesId)
	}

	// ---- Music, audiobooks and books -----------------------------------------------------

	/**
	 * Search inside one library. Music finds artists, albums and songs; other libraries find their
	 * own titles (movies, shows, books, audiobooks, ...). Ranked the same way as the main search.
	 */
	suspend fun searchLibrary(view: BaseItemDto, term: String): LibrarySearchResults = withContext(Dispatchers.IO) {
		val query = term.trim()
		if (query.length < 2) return@withContext LibrarySearchResults()
		val words = query.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(Regex(" +")).filter { it.isNotEmpty() }
		fun rank(list: List<BaseItemDto>, name: (BaseItemDto) -> String?) = list
			.mapNotNull { item -> searchScore(name(item).orEmpty(), words)?.let { item to it } }
			.sortedWith(compareBy<Pair<BaseItemDto, Int>> { it.second }.thenByDescending { it.first.communityRating ?: 0f })
			.map { it.first }
		suspend fun find(types: List<BaseItemKind>?, limit: Int, inLibrary: Boolean = true) = runCatching {
			api.itemsApi.getItems(
				parentId = view.id.takeIf { inLibrary },
				searchTerm = query,
				recursive = true,
				includeItemTypes = types,
				fields = cardFields,
				imageTypeLimit = 1,
				limit = limit,
			).content.items
		}.getOrDefault(emptyList())

		coroutineScope {
			when (view.collectionType) {
				CollectionType.MUSIC -> {
					val artists = async { find(listOf(BaseItemKind.MUSIC_ARTIST), 30, inLibrary = false) }
					val albums = async { find(listOf(BaseItemKind.MUSIC_ALBUM), 60) }
					val songs = async { find(listOf(BaseItemKind.AUDIO), 40) }
					LibrarySearchResults(
						main = rank(albums.await()) { it.name },
						artists = rank(artists.await()) { it.name },
						songs = rank(songs.await()) { it.name },
					)
				}
				else -> {
					val types = when {
						view.collectionType == CollectionType.BOOKS -> listOf(BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK)
						else -> typesFor(view)
					}
					val found = find(types, 80)
						// Folders are only containers; audiobooks come as parts, so show each book once
						.filter { it.isFolder != true || it.type in listOf(BaseItemKind.SERIES, BaseItemKind.MUSIC_ALBUM, BaseItemKind.BOX_SET) }
						.distinctBy { if (it.type == BaseItemKind.AUDIO_BOOK) it.album ?: it.id.toString() else it.id.toString() }
					LibrarySearchResults(main = rank(found) { it.album?.takeIf { _ -> it.type == BaseItemKind.AUDIO_BOOK } ?: it.name })
				}
			}
		}
	}

	/** Music home: albums you played lately (newest first), from the songs you listened to. */
	suspend fun recentlyPlayedAlbums(view: BaseItemDto): List<BaseItemDto> = withContext(Dispatchers.IO) {
		val songs = api.itemsApi.getItems(
			parentId = view.id,
			includeItemTypes = listOf(BaseItemKind.AUDIO),
			recursive = true,
			sortBy = listOf(ItemSortBy.DATE_PLAYED),
			sortOrder = listOf(SortOrder.DESCENDING),
			filters = listOf(org.khylo.sdk.model.api.ItemFilter.IS_PLAYED),
			limit = 80,
			enableTotalRecordCount = false,
		).content.items
		val albumIds = songs.mapNotNull { it.albumId }.distinct().take(20)
		if (albumIds.isEmpty()) return@withContext emptyList()
		val albums = api.itemsApi.getItems(ids = albumIds, fields = cardFields).content.items.associateBy { it.id }
		albumIds.mapNotNull(albums::get)
	}

	suspend fun recentlyAddedAlbums(view: BaseItemDto): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.userLibraryApi.getLatestMedia(parentId = view.id, includeItemTypes = listOf(BaseItemKind.AUDIO), limit = 24, groupItems = true, fields = cardFields).content
			.filter { it.type == BaseItemKind.MUSIC_ALBUM }
	}

	suspend fun randomAlbums(view: BaseItemDto, genre: String? = null, limit: Int = 20): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.itemsApi.getItems(
			parentId = view.id,
			includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
			recursive = true,
			genres = genre?.let { listOf(it) },
			imageTypes = listOf(ImageType.PRIMARY),
			sortBy = listOf(ItemSortBy.RANDOM),
			fields = cardFields,
			limit = limit,
			enableTotalRecordCount = false,
		).content.items
	}

	/** Artists, alphabetically (a page at a time) or at random; [withPhoto] keeps only ones with a backdrop. */
	suspend fun musicArtists(startIndex: Int = 0, limit: Int = 60, random: Boolean = false, withPhoto: Boolean = false): Pair<List<BaseItemDto>, Int> = withContext(Dispatchers.IO) {
		val result = api.itemsApi.getItems(
			includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
			recursive = true,
			imageTypes = listOf(if (withPhoto) ImageType.BACKDROP else ImageType.PRIMARY),
			sortBy = listOf(if (random) ItemSortBy.RANDOM else ItemSortBy.SORT_NAME),
			fields = cardFields,
			startIndex = startIndex,
			limit = limit,
		).content
		result.items to result.totalRecordCount
	}

	/**
	 * The library's music genres, most common first. Built from the albums' own genre names, since
	 * the server doesn't always keep a separate list of music genres.
	 */
	suspend fun musicGenres(view: BaseItemDto): List<BaseItemDto> = withContext(Dispatchers.IO) {
		val albums = api.itemsApi.getItems(
			parentId = view.id,
			includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
			recursive = true,
			fields = listOf(ItemFields.GENRES),
			enableImages = false,
			enableUserData = false,
			limit = 4000,
			enableTotalRecordCount = false,
		).content.items
		// "Rock" and "rock" are one genre; show it with the spelling most albums use
		albums.flatMap { it.genres.orEmpty() }
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.groupBy { it.lowercase() }
			.values.sortedByDescending { it.size }
			.take(40)
			.map { spellings ->
				val name = spellings.groupingBy { it }.eachCount().maxBy { it.value }.key
				BaseItemDto(id = UUID.nameUUIDFromBytes(name.lowercase().toByteArray()), type = BaseItemKind.MUSIC_GENRE, name = name)
			}
	}

	/** An album's songs in disc and track order. */
	suspend fun albumTracks(albumId: UUID): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.itemsApi.getItems(
			parentId = albumId,
			includeItemTypes = listOf(BaseItemKind.AUDIO),
			recursive = true,
			sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME),
			fields = cardFields,
			enableUserData = true,
		).content.items
	}

	/**
	 * An artist's albums, newest first: their own albums, then the ones they appear on (as a guest
	 * or on a compilation).
	 */
	suspend fun artistAlbums(artistId: UUID): Pair<List<BaseItemDto>, List<BaseItemDto>> = withContext(Dispatchers.IO) {
		suspend fun query(own: Boolean) = api.itemsApi.getItems(
			albumArtistIds = if (own) listOf(artistId) else null,
			contributingArtistIds = if (own) null else listOf(artistId),
			includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
			recursive = true,
			sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
			sortOrder = listOf(SortOrder.DESCENDING),
			fields = cardFields,
			imageTypeLimit = 1,
		).content.items
		val own = runCatching { query(true) }.getOrDefault(emptyList())
		val ownIds = own.map { it.id }.toSet()
		val appearsOn = runCatching { query(false) }.getOrDefault(emptyList()).filter { it.id !in ownIds }
		own to appearsOn
	}

	/** Artists like this one, for the artist page. */
	suspend fun similarArtists(artistId: UUID): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.libraryApi.getSimilarArtists(itemId = artistId, limit = 16, fields = cardFields).content.items
	}

	/** An artist's songs, album by album. */
	suspend fun artistTracks(artistId: UUID): List<BaseItemDto> = withContext(Dispatchers.IO) {
		api.itemsApi.getItems(
			artistIds = listOf(artistId),
			includeItemTypes = listOf(BaseItemKind.AUDIO),
			recursive = true,
			sortBy = listOf(ItemSortBy.ALBUM, ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
			fields = cardFields,
			limit = 300,
		).content.items
	}

	/** All the parts of the audiobook [part] belongs to (its folder), in order. */
	suspend fun audiobookParts(part: BaseItemDto): List<BaseItemDto> = withContext(Dispatchers.IO) {
		val parentId = part.parentId ?: return@withContext listOf(part)
		api.itemsApi.getItems(
			parentId = parentId,
			includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK),
			sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME),
			fields = cardFields,
			enableUserData = true,
		).content.items.ifEmpty { listOf(part) }
	}

	/** Downloads a book's file to [target] (skipped when it's already there). */
	suspend fun downloadBook(item: BaseItemDto, target: java.io.File, http: okhttp3.OkHttpClient) = withContext(Dispatchers.IO) {
		if (target.exists() && target.length() > 0) return@withContext
		val request = okhttp3.Request.Builder()
			.url("${api.baseUrl}/Items/${item.id}/Download")
			.header("Authorization", "MediaBrowser Token=\"${api.accessToken}\"")
			.build()
		http.newCall(request).execute().use { response ->
			check(response.isSuccessful) { "HTTP ${response.code}" }
			val partial = java.io.File(target.parentFile, target.name + ".part")
			partial.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
			partial.renameTo(target)
		}
	}

	// ---- Player extras --------------------------------------------------------------------

	/** Intro, recap and credits markers for an item (found by the server's detector). */
	suspend fun segments(itemId: UUID): List<org.khylo.sdk.model.api.MediaSegmentDto> = withContext(Dispatchers.IO) {
		runCatching { api.mediaSegmentsApi.getItemSegments(itemId = itemId).content.items }.getOrDefault(emptyList())
	}

	suspend fun setFavorite(itemId: UUID, favorite: Boolean) = withContext(Dispatchers.IO) {
		if (favorite) api.userLibraryApi.markFavoriteItem(itemId = itemId) else api.userLibraryApi.unmarkFavoriteItem(itemId = itemId)
	}

	/** Schedules a recording of one airing. Returns the recording's id, so it can be cancelled again. */
	suspend fun recordProgram(programId: String): String? = withContext(Dispatchers.IO) {
		val defaults = api.liveTvApi.getDefaultTimer(programId).content
		api.liveTvApi.createTimer(
			TimerInfoDto(
				type = defaults.type,
				serverId = defaults.serverId,
				externalId = defaults.externalId,
				channelId = defaults.channelId,
				externalChannelId = defaults.externalChannelId,
				channelName = defaults.channelName,
				programId = defaults.programId,
				externalProgramId = defaults.externalProgramId,
				name = defaults.name,
				overview = defaults.overview,
				startDate = defaults.startDate,
				endDate = defaults.endDate,
				serviceName = defaults.serviceName,
				priority = defaults.priority,
				prePaddingSeconds = defaults.prePaddingSeconds,
				postPaddingSeconds = defaults.postPaddingSeconds,
				isPrePaddingRequired = defaults.isPrePaddingRequired,
				isPostPaddingRequired = defaults.isPostPaddingRequired,
				keepUntil = defaults.keepUntil,
			)
		)
		api.liveTvApi.getTimers().content.items.firstOrNull { it.programId == programId }?.id
	}

	suspend fun cancelRecording(timerId: String) = withContext(Dispatchers.IO) { api.liveTvApi.cancelTimer(timerId) }

	/** Records every airing of a show from now on. Returns the series recording's id. */
	suspend fun recordSeries(programId: String): String? = withContext(Dispatchers.IO) {
		val defaults = api.liveTvApi.getDefaultTimer(programId).content
		api.liveTvApi.createSeriesTimer(defaults)
		api.liveTvApi.getProgram(programId).content.seriesTimerId
	}

	suspend fun cancelSeriesRecording(timerId: String) = withContext(Dispatchers.IO) { api.liveTvApi.cancelSeriesTimer(timerId) }

	// ---- Preferences (stored on the server, so they follow the profile everywhere) ---------

	suspend fun userConfiguration(): UserConfiguration? = withContext(Dispatchers.IO) {
		api.userApi.getCurrentUser().content.configuration
	}

	suspend fun saveUserConfiguration(config: UserConfiguration) = withContext(Dispatchers.IO) {
		api.userApi.updateUserConfiguration(data = config)
	}

	suspend fun serverVersion(): String? = withContext(Dispatchers.IO) {
		runCatching { api.systemApi.getPublicSystemInfo().content.version }.getOrNull()
	}

	// ---- Live TV --------------------------------------------------------------------------

	suspend fun channels(limit: Int, favoritesOnly: Boolean = false): ChannelPage = withContext(Dispatchers.IO) {
		val response = api.liveTvApi.getLiveTvChannels(
			addCurrentProgram = true,
			enableFavoriteSorting = true,
			isFavorite = if (favoritesOnly) true else null,
			sortBy = listOf(ItemSortBy.SORT_NAME),
			imageTypeLimit = 1,
			limit = limit,
		).content
		ChannelPage(response.items, response.totalRecordCount)
	}

	suspend fun programs(channelIds: List<UUID>, start: LocalDateTime, end: LocalDateTime): List<BaseItemDto> = withContext(Dispatchers.IO) {
		if (channelIds.isEmpty()) return@withContext emptyList()
		api.liveTvApi.getLiveTvPrograms(
			channelIds = channelIds,
			enableImages = false,
			sortBy = listOf(ItemSortBy.START_DATE),
			maxStartDate = end,
			minEndDate = start,
		).content.items
	}
}
