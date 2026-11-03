package org.khylo.tv.library

import org.khylo.tv.design.KIcons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.khylo.sdk.model.api.BaseItemPerson
import org.khylo.sdk.model.api.PersonKind
import org.khylo.tv.design.rememberFocusState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import org.khylo.tv.core.R
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.ItemSortBy
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.BottomScrim
import org.khylo.tv.design.HeroScrim
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KCard
import org.khylo.tv.design.KCardShape
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSectionTitle
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KTab
import org.khylo.tv.design.KText
import org.khylo.tv.design.KTextField
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.fadeUnderTopNav
import org.khylo.tv.home.ContentStart
import org.khylo.tv.home.metaLine
import org.khylo.tv.home.openItem
import org.khylo.tv.home.subtitleFor
import org.khylo.tv.home.typeLabel
import org.koin.compose.koinInject
import java.util.UUID

private const val PAGE = 60

/**
 * Titles already loaded for each library grid (by library, sort and category), so coming back to
 * a grid shows it as it was instead of loading from the top. Choosing a section from the top bar
 * starts fresh.
 */
object LibraryPages {
	class Pages {
		val items = androidx.compose.runtime.mutableStateListOf<BaseItemDto>()
		var total by mutableStateOf(-1)
		var loading by mutableStateOf(false)
	}

	private val cache = object : LinkedHashMap<String, Pages>(16, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pages>?) = size > 12
	}

	fun get(key: String): Pages = cache.getOrPut(key) { Pages() }

	fun clear() = cache.clear()
}

/**
 * A whole library (or one of its categories, when [genre] is set) as a poster grid, loaded a page
 * at a time. [showTitle] is off when it sits under a library's own tabs.
 */
@Composable
fun LibraryScreen(view: BaseItemDto, genre: BaseItemDto? = null, showTitle: Boolean = true, onFocusItem: (BaseItemDto) -> Unit = {}) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	var sort by androidx.compose.runtime.saveable.rememberSaveable(view.id) { mutableStateOf(ItemSortBy.SORT_NAME) }
	// Libraries shown on their own get their own search (inside other screens, those screens have it)
	val canSearch = showTitle && genre == null
	var searching by remember(view.id) { mutableStateOf(org.khylo.tv.app.SearchMemory.library.containsKey(view.id)) }
	val pages = remember(view.id, sort, genre) { LibraryPages.get("${view.id}|$sort|${genre?.id}") }
	val items = pages.items
	val total = pages.total
	val gridState = rememberLazyGridState()
	val isMusic = view.collectionType == org.khylo.sdk.model.api.CollectionType.MUSIC
	val isAudiobooks = org.khylo.tv.app.isAudiobookLibrary(view)

	suspend fun loadMore() {
		if (pages.loading || (pages.total >= 0 && items.size >= pages.total)) return
		pages.loading = true
		runCatching { repository.libraryItems(view, items.size, PAGE, sort, genre) }.onSuccess { (page, count) ->
			items.addAll(page)
			pages.total = count
		}
		pages.loading = false
	}

	// Coming back: titles watched (or marked) meanwhile get their check mark
	LaunchedEffect(Unit) {
		items.withIndex().filter { (_, item) -> org.khylo.tv.data.WatchStateChanges.contains(item.id) }.forEach { (index, old) ->
			runCatching { repository.item(old.id) }.onSuccess { fresh ->
				if (index < items.size && items[index].id == old.id) items[index] = fresh
			}
		}
	}

	LaunchedEffect(view.id, sort, genre) {
		// A grid shown before keeps its titles and scroll position; a new sort starts at the top
		if (items.isEmpty()) {
			gridState.scrollToItem(0)
			loadMore()
		}
	}
	LaunchedEffect(gridState, view.id, sort, genre) {
		snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
			.collect { last -> if (last >= items.size - 12) loadMore() }
	}

	Column(Modifier.fillMaxSize()) {
		Row(
			Modifier.fillMaxWidth().padding(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = if (showTitle) 24.dp else 4.dp, bottom = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Column(Modifier.weight(1f)) {
				if (showTitle) KText(genre?.name ?: view.name.orEmpty(), style = KhyloType.Title)
				val count = if (total >= 0) "$total titles" else null
				listOfNotNull(genre?.let { view.name }, count).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { KText(it, style = KhyloType.Caption) }
			}
			if (canSearch) SearchTab(KIcons.Search, searching, { searching = !searching; if (!searching) org.khylo.tv.app.SearchMemory.library.remove(view.id) })
			KTab("A–Z", sort == ItemSortBy.SORT_NAME && !searching, { searching = false; org.khylo.tv.app.SearchMemory.library.remove(view.id); sort = ItemSortBy.SORT_NAME })
			KTab("Recently added", sort == ItemSortBy.DATE_CREATED && !searching, { searching = false; org.khylo.tv.app.SearchMemory.library.remove(view.id); sort = ItemSortBy.DATE_CREATED })
			KTab("Release date", sort == ItemSortBy.PREMIERE_DATE && !searching, { searching = false; org.khylo.tv.app.SearchMemory.library.remove(view.id); sort = ItemSortBy.PREMIERE_DATE })
		}

		if (searching) {
			LibrarySearch(view)
		} else if (total < 0) {
			Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
		} else {
			org.khylo.tv.design.PivotScroll {
			LazyVerticalGrid(
				state = gridState,
				columns = GridCells.Adaptive(170.dp),
				contentPadding = PaddingValues(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, bottom = 48.dp, top = 8.dp),
				horizontalArrangement = Arrangement.spacedBy(24.dp),
				verticalArrangement = Arrangement.spacedBy(28.dp),
			) {
				items(items, key = { it.id }) { item ->
					KCard(
						watched = item.userData?.played == true,
						imageUrl = repository.imageUrl(item),
						// A book in several files is listed by its first part; it's named for the book
						title = bookTitle(item),
						subtitle = when {
							isMusic -> item.albumArtist ?: item.productionYear?.toString()
							isAudiobooks -> item.albumArtist ?: item.artists?.firstOrNull() ?: item.productionYear?.toString()
							else -> item.productionYear?.toString()
						},
						width = 170.dp,
						shape = if (isMusic || isAudiobooks) KCardShape.Square else KCardShape.Poster,
						// Album and audiobook covers alone don't say which it is; name them underneath
						showTitle = isMusic || isAudiobooks,
						onClick = { openItem(navigator, item) },
						onFocus = { onFocusItem(item) },
						backdropUrl = repository.backdropUrl(item, 1280),
						focusKey = item.id.toString(),
					)
				}
			}
			}
		}
	}
}

/** One title: artwork, details, play. Shows list their seasons and episodes. */
@Composable
fun DetailsScreen(itemId: UUID) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val item by produceState<BaseItemDto?>(null, itemId) { value = runCatching { repository.item(itemId) }.getOrNull() }
	val current = item ?: run {
		Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
		return
	}
	// Albums and artists have their own pages, laid out like a music app
	when (current.type) {
		BaseItemKind.MUSIC_ALBUM -> {
			org.khylo.tv.listen.MusicAlbumScreen(current)
			return
		}
		BaseItemKind.MUSIC_ARTIST -> {
			org.khylo.tv.listen.MusicArtistScreen(current)
			return
		}
		else -> Unit
	}

	val seasons by produceState(emptyList<BaseItemDto>(), current.id) {
		if (current.type == BaseItemKind.SERIES) value = runCatching { repository.children(current.id, listOf(BaseItemKind.SEASON)) }.getOrDefault(emptyList())
	}
	var season by remember(current.id) { mutableStateOf<BaseItemDto?>(null) }
	val activeSeason = season ?: seasons.firstOrNull()
	// Watched state, changed from this page with Mark watched; a show's episodes reload to match
	var watched by remember(current.id) { mutableStateOf(current.userData?.played == true) }
	var watchVersion by remember(current.id) { mutableStateOf(0) }
	val watchScope = androidx.compose.runtime.rememberCoroutineScope()
	val episodes by produceState(emptyList<BaseItemDto>(), activeSeason?.id, watchVersion) {
		val id = activeSeason?.id ?: return@produceState
		value = runCatching { repository.seasonEpisodes(id) }.getOrDefault(emptyList())
	}
	val boxSetItems by produceState(emptyList<BaseItemDto>(), current.id) {
		if (current.type == BaseItemKind.BOX_SET || current.type == BaseItemKind.PLAYLIST) value = runCatching { repository.children(current.id) }.getOrDefault(emptyList())
	}

	// Music, audiobooks and books show their cover art beside the title
	val coverUrl = if (current.type in listOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.AUDIO, BaseItemKind.MUSIC_ARTIST, BaseItemKind.AUDIO_BOOK, BaseItemKind.BOOK)) {
		repository.imageUrl(current, org.khylo.sdk.model.api.ImageType.PRIMARY, 600)
			?: current.albumId?.let { id -> current.albumPrimaryImageTag?.let { tag -> repository.imageUrlFor(id, tag, 600) } }
	} else null
	val tracks by produceState(emptyList<BaseItemDto>(), current.id) {
		value = runCatching {
			when (current.type) {
				BaseItemKind.MUSIC_ALBUM -> repository.albumTracks(current.id)
				BaseItemKind.AUDIO_BOOK -> repository.audiobookParts(current).takeIf { it.size > 1 }.orEmpty()
				else -> emptyList()
			}
		}.getOrDefault(emptyList())
	}
	// An artist's albums, to pick one to play
	val artistAlbums by produceState(emptyList<BaseItemDto>() to emptyList<BaseItemDto>(), current.id) {
		if (current.type == BaseItemKind.MUSIC_ARTIST) value = runCatching { repository.artistAlbums(current.id) }.getOrDefault(emptyList<BaseItemDto>() to emptyList())
	}
	val similar by produceState(emptyList<BaseItemDto>(), current.id) {
		if (current.type == BaseItemKind.MOVIE || current.type == BaseItemKind.SERIES) {
			value = runCatching { repository.similar(current) }.getOrDefault(emptyList())
		}
	}
	val people = remember(current.id) {
		val order = listOf(PersonKind.ACTOR, PersonKind.GUEST_STAR, PersonKind.DIRECTOR, PersonKind.WRITER, PersonKind.PRODUCER)
		current.people.orEmpty().filter { it.type in order }.sortedBy { order.indexOf(it.type) }.distinctBy { it.id }.take(30)
	}

	val resumeMs = (current.userData?.playbackPositionTicks ?: 0L) / 10_000
	val nextEpisode = episodes.firstOrNull { it.userData?.played != true } ?: episodes.firstOrNull()

	// Focus starts on Play with the page at the top. From the first row below, Up always comes
	// back to Play (otherwise it can jump to the top bar once Play has scrolled away).
	val listState = androidx.compose.foundation.lazy.rememberLazyListState()
	val scope = androidx.compose.runtime.rememberCoroutineScope()
	val primaryFocus = remember { androidx.compose.ui.focus.FocusRequester() }
	val hasPrimary = current.type != BaseItemKind.BOX_SET && current.type != BaseItemKind.PLAYLIST
	var focusedRow by remember(current.id) { mutableStateOf(0) }
	LaunchedEffect(current.id) {
		if (!hasPrimary) return@LaunchedEffect
		repeat(20) {
			delay(50)
			if (runCatching { primaryFocus.requestFocus() }.isSuccess) {
				listState.scrollToItem(0)
				return@LaunchedEffect
			}
		}
	}
	fun Modifier.row(index: Int) = onFocusChanged { if (it.hasFocus) focusedRow = index }

	Box(Modifier.fillMaxSize()) {
		Box(Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
			val backdrop = repository.backdropUrl(current)
			if (backdrop == null && coverUrl != null) {
				KImage(coverUrl, Modifier.fillMaxSize().blur(90.dp), ContentScale.Crop)
				Box(Modifier.fillMaxSize().background(Color(0x99000000)))
			} else {
				KImage(backdrop, Modifier.fillMaxSize(), ContentScale.Crop)
			}
			Box(Modifier.fillMaxSize().background(HeroScrim))
			Box(Modifier.fillMaxSize().background(BottomScrim))
		}

		LazyColumn(
			state = listState,
			contentPadding = PaddingValues(bottom = 48.dp),
			verticalArrangement = Arrangement.spacedBy(32.dp),
			modifier = Modifier.fadeUnderTopNav().onPreviewKeyEvent { event ->
				if (hasPrimary && focusedRow == 1 && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
					scope.launch { listState.animateScrollToItem(0) }
					runCatching { primaryFocus.requestFocus() }
					true
				} else {
					false
				}
			},
		) {
			var rows = 0
			item {
				Row(Modifier.row(0).padding(start = ContentStart, top = KhyloSpacing.NavHeight + 40.dp), verticalAlignment = Alignment.Bottom) {
				coverUrl?.let { url ->
					val square = current.type == BaseItemKind.MUSIC_ALBUM || current.type == BaseItemKind.AUDIO || current.type == BaseItemKind.MUSIC_ARTIST
					Box(
						Modifier
							.width(if (square) 260.dp else 210.dp)
							.aspectRatio(if (square) 1f else 2f / 3f)
							.shadow(28.dp, RoundedCornerShape(10.dp))
							.clip(RoundedCornerShape(10.dp))
							.background(KhyloColors.SurfaceRaised)
					) { KImage(url, Modifier.fillMaxSize()) }
					Spacer(Modifier.width(40.dp))
				}
				Column(Modifier.fillMaxWidth(0.6f)) {
					val eyebrow = if (current.type == BaseItemKind.EPISODE) {
						listOfNotNull(current.seriesName, current.parentIndexNumber?.let { s -> current.indexNumber?.let { e -> "S$s E$e" } }).joinToString(" · ").uppercase()
					} else typeLabel(current)
					KText(eyebrow, style = KhyloType.Eyebrow)
					Spacer(Modifier.height(12.dp))
					val logo = repository.logoUrl(current)
					if (logo != null) KImage(logo, Modifier.height(120.dp).width(460.dp), ContentScale.Fit)
					else KText(if (tracks.size > 1) bookTitle(current) else current.name.orEmpty(), style = KhyloType.Display, maxLines = 2)
					Spacer(Modifier.height(14.dp))
					org.khylo.tv.home.MetaRow(current)
					current.taglines?.firstOrNull()?.let {
						Spacer(Modifier.height(10.dp))
						KText("“$it”", style = KhyloType.Body, color = KhyloColors.Text, maxLines = 1)
					}
					current.overview?.let {
						Spacer(Modifier.height(10.dp))
						KText(it, style = KhyloType.Body, maxLines = 4)
					}
					Spacer(Modifier.height(24.dp))
					Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
						when (current.type) {
							BaseItemKind.SERIES -> {
								// Shown straight away; it names the episode once the list has loaded
								val ep = nextEpisode
								val label = when {
									ep == null -> "Play"
									(ep.userData?.playbackPositionTicks ?: 0) > 0 -> "Resume S${ep.parentIndexNumber} E${ep.indexNumber}"
									else -> "Play S${ep.parentIndexNumber} E${ep.indexNumber}"
								}
								KButton(label, onClick = { ep?.let { navigator.go(Route.Player(it)) } }, icon = KIcons.Play, style = KButtonStyle.Primary, modifier = Modifier.focusRequester(primaryFocus))
							}
							BaseItemKind.BOX_SET, BaseItemKind.PLAYLIST -> Unit
							else -> {
								if (resumeMs > 0) {
									KButton(resumeLabel(current), onClick = { navigator.go(playRoute(current)) }, icon = playIcon(current), style = KButtonStyle.Primary, modifier = Modifier.focusRequester(primaryFocus))
									if (current.type !in listOf(BaseItemKind.AUDIO_BOOK, BaseItemKind.BOOK)) {
										KButton("Start over", onClick = { navigator.go(playRoute(current, 0)) }, icon = KIcons.Retry)
									}
								} else {
									KButton(playLabel(current), onClick = { navigator.go(playRoute(current)) }, icon = playIcon(current), style = KButtonStyle.Primary, modifier = Modifier.focusRequester(primaryFocus))
								}
								// A DVD has its own menu: scenes, extras
								if (current.videoType == org.khylo.sdk.model.api.VideoType.DVD || (current.videoType == org.khylo.sdk.model.api.VideoType.ISO && current.isoType == org.khylo.sdk.model.api.IsoType.DVD)) {
									KButton("Disc menu", onClick = { navigator.go(Route.DiscMenu(current)) }, icon = KIcons.Collection)
								}
								current.seriesId?.let { seriesId ->
									KButton("Go to show", onClick = { navigator.go(Route.Details(seriesId)) }, icon = KIcons.Tv)
								}
							}
						}
						// Watched or not, told to the server straight away (a show marks every episode)
						if (current.type in listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.SERIES, BaseItemKind.VIDEO)) {
							// One toggle: says what pressing it will do
							KButton(
								if (watched) "Mark unwatched" else "Mark watched",
								onClick = {
									watchScope.launch {
										runCatching { if (watched) repository.markUnwatched(current) else repository.markWatched(current) }
											.onSuccess {
												watched = !watched
												watchVersion++
											}
									}
								},
								icon = KIcons.Check,
							)
						}
					}
				}
				}
			}

			// An album's songs, or an audiobook's parts, as a simple list
			if (tracks.isNotEmpty()) {
				val rowTracks = ++rows
				item {
					Column(Modifier.row(rowTracks).padding(horizontal = ContentStart)) {
						KSectionTitle(if (current.type == BaseItemKind.MUSIC_ALBUM) "Songs" else "Parts")
						Spacer(Modifier.height(8.dp))
						tracks.forEachIndexed { index, track ->
							TrackListRow(index, track) {
								navigator.go(if (track.type == BaseItemKind.AUDIO_BOOK) Route.Audiobook(track) else Route.Music(track))
							}
						}
					}
				}
			}

			listOf("Albums" to artistAlbums.first, "Appears on" to artistAlbums.second).forEach { (title, albums) ->
				if (albums.isEmpty()) return@forEach
				val rowAlbums = ++rows
				item(key = title) {
					Column(Modifier.row(rowAlbums)) {
						KSectionTitle(title, Modifier.padding(start = ContentStart))
						LazyRow(contentPadding = PaddingValues(horizontal = ContentStart, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
							items(albums, key = { it.id }) { album ->
								KCard(
									imageUrl = repository.imageUrl(album),
									title = album.name.orEmpty(),
									subtitle = album.productionYear?.toString(),
									shape = KCardShape.Square,
									width = 170.dp,
									showTitle = true,
									onClick = { navigator.go(Route.Details(album.id)) },
								)
							}
						}
					}
				}
			}

			if (seasons.isNotEmpty()) {
				val row1 = ++rows
				item {
					LazyRow(
						modifier = Modifier.row(row1),
						contentPadding = PaddingValues(horizontal = ContentStart),
						horizontalArrangement = Arrangement.spacedBy(2.dp),
					) {
						items(seasons, key = { it.id }) { s ->
							KTab(s.name.orEmpty(), s.id == activeSeason?.id, { season = s })
						}
					}
				}
				val row2 = ++rows
				item {
					LazyRow(
						modifier = Modifier.row(row2),
						contentPadding = PaddingValues(horizontal = ContentStart, vertical = 8.dp),
						horizontalArrangement = Arrangement.spacedBy(22.dp),
					) {
						items(episodes, key = { it.id }) { ep ->
							KCard(
								watched = ep.userData?.played == true,
								imageUrl = repository.thumbUrl(ep),
								// Episode number and title, with the running time underneath
								title = listOfNotNull(ep.indexNumber?.toString(), ep.name).joinToString(". "),
								subtitle = ep.runTimeTicks?.let { ticks ->
									val minutes = ticks / 600_000_000
									if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
								},
								showTitle = true,
								shape = KCardShape.Wide,
								width = 320.dp,
								progress = ep.userData?.playedPercentage?.toFloat()?.div(100f),
								onClick = { navigator.go(Route.Player(ep)) },
							)
						}
					}
				}
			}

			if (boxSetItems.isNotEmpty()) {
				val row3 = ++rows
				item {
					Column(Modifier.row(row3)) {
						KSectionTitle(if (current.type == BaseItemKind.PLAYLIST) "In this playlist" else "In this collection", Modifier.padding(start = ContentStart))
						LazyRow(contentPadding = PaddingValues(horizontal = ContentStart, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
							items(boxSetItems, key = { it.id }) { child ->
								KCard(repository.imageUrl(child), child.name.orEmpty(), onClick = { openItem(navigator, child) }, subtitle = child.productionYear?.toString(), watched = child.userData?.played == true)
							}
						}
					}
				}
			}

			if (people.isNotEmpty()) {
				val row4 = ++rows
				item {
					Column(Modifier.row(row4)) {
						KSectionTitle("Cast & crew", Modifier.padding(start = ContentStart))
						LazyRow(contentPadding = PaddingValues(horizontal = ContentStart, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
							items(people, key = { it.id.toString() + it.type }) { person ->
								PersonCard(person, repository.personImageUrl(person)) { navigator.go(Route.Person(person.id, person.name.orEmpty())) }
							}
						}
					}
				}
			}

			if (similar.isNotEmpty()) {
				val row5 = ++rows
				item {
					Column(Modifier.row(row5)) {
						KSectionTitle("More like this", Modifier.padding(start = ContentStart))
						LazyRow(contentPadding = PaddingValues(horizontal = ContentStart, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
							items(similar, key = { it.id }) { other ->
								KCard(repository.imageUrl(other), other.name.orEmpty(), onClick = { openItem(navigator, other) }, width = 148.dp, watched = other.userData?.played == true)
							}
						}
					}
				}
			}

			val row6 = ++rows
			item {
				Box(Modifier.row(row6)) { AboutPanel(current) }
			}
		}
	}
}


/** A round headshot with the person's name and role; opens everything they're in. */
@Composable
private fun PersonCard(person: BaseItemPerson, imageUrl: String?, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "personScale")
	Column(Modifier.width(118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		Box(
			Modifier
				.size(108.dp)
				.scale(scale)
				.clip(CircleShape)
				.background(KhyloColors.SurfaceRaised)
				.border(if (focused) 3.dp else 0.dp, if (focused) KhyloColors.Focus else Color.Transparent, CircleShape)
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			if (imageUrl != null) {
				KImage(imageUrl, Modifier.fillMaxSize())
			} else {
				val initials = person.name.orEmpty().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }
				KText(initials.uppercase(), style = KhyloType.Title, color = KhyloColors.TextSecondary)
			}
		}
		Spacer(Modifier.height(10.dp))
		KText(person.name.orEmpty(), style = KhyloType.Label, color = if (focused) KhyloColors.Text else KhyloColors.TextSecondary, maxLines = 1, textAlign = TextAlign.Center)
		val isCast = person.type == PersonKind.ACTOR || person.type == PersonKind.GUEST_STAR
		val role = person.role?.takeIf { isCast && it.isNotBlank() }
			?: person.type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
		KText(role, style = KhyloType.Caption, maxLines = 1, textAlign = TextAlign.Center)
	}
}

/** The full story plus the facts: genres, who made it, when, and the picture and sound. */
@Composable
private fun AboutPanel(item: BaseItemDto) {
	val (source, focused) = rememberFocusState()
	val facts = buildList {
		item.genres?.takeIf { it.isNotEmpty() }?.let { add("Genres" to it.joinToString(", ")) }
		val people = item.people.orEmpty()
		people.filter { it.type == PersonKind.DIRECTOR }.mapNotNull { it.name }.takeIf { it.isNotEmpty() }?.let { add("Directed by" to it.take(3).joinToString(", ")) }
		people.filter { it.type == PersonKind.WRITER }.mapNotNull { it.name }.takeIf { it.isNotEmpty() }?.let { add("Written by" to it.take(3).joinToString(", ")) }
		item.studios?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.let { add((if (item.type == BaseItemKind.SERIES) "Network" else "Studio") to it.take(2).joinToString(", ")) }
		item.premiereDate?.let { add("Released" to it.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))) }
		if (item.type == BaseItemKind.SERIES) item.childCount?.takeIf { it > 0 }?.let { add("Seasons" to it.toString()) }
		val ratings = listOfNotNull(
			item.officialRating,
			item.communityRating?.let { "★ %.1f".format(it) },
			item.criticRating?.let { "${it.toInt()}% critics" },
		)
		if (ratings.isNotEmpty()) add("Rating" to ratings.joinToString("  ·  "))
		val streams = item.mediaSources?.firstOrNull()?.mediaStreams.orEmpty()
		streams.firstOrNull { it.type == org.khylo.sdk.model.api.MediaStreamType.VIDEO }?.displayTitle?.let { add("Video" to it) }
		val audio = streams.filter { it.type == org.khylo.sdk.model.api.MediaStreamType.AUDIO }
		(audio.firstOrNull { it.isDefault } ?: audio.firstOrNull())?.displayTitle?.let { add("Audio" to it) }
		streams.count { it.type == org.khylo.sdk.model.api.MediaStreamType.SUBTITLE }.takeIf { it > 0 }?.let { add("Subtitles" to "$it available") }
	}

	Column(
		Modifier
			.padding(horizontal = ContentStart)
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.border(1.dp, if (focused) Color(0x66FFFFFF) else Color.Transparent, RoundedCornerShape(14.dp))
			.focusable(interactionSource = source)
			.padding(vertical = 20.dp, horizontal = 4.dp),
	) {
		KText("About", style = KhyloType.Heading)
		Spacer(Modifier.height(16.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
			Column(Modifier.weight(1f)) {
				KText(item.name.orEmpty(), style = KhyloType.Label)
				Spacer(Modifier.height(8.dp))
				KText(item.overview ?: "No description yet.", style = KhyloType.Body)
			}
			Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
				facts.forEach { (label, value) ->
					Row {
						KText(label, style = KhyloType.Meta, color = KhyloColors.TextMuted, modifier = Modifier.width(120.dp))
						KText(value, style = KhyloType.Meta, color = KhyloColors.Text, maxLines = 2)
					}
				}
			}
		}
	}
}

/** Everything a person is in, from their headshot on a title page. */
@Composable
fun PersonScreen(personId: UUID, name: String) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val person by produceState<BaseItemDto?>(null, personId) { value = runCatching { repository.item(personId) }.getOrNull() }
	val titles by produceState<List<BaseItemDto>?>(null, personId) {
		value = runCatching { repository.personItems(personId) }.getOrDefault(emptyList())
	}

	Column(Modifier.fillMaxSize()) {
		Row(
			Modifier.padding(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = 24.dp, bottom = 24.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(28.dp),
		) {
			Box(Modifier.size(110.dp).clip(CircleShape).background(KhyloColors.SurfaceRaised)) {
				repository.imageUrl(person, maxWidth = 300)?.let { KImage(it, Modifier.fillMaxSize()) }
			}
			Column(Modifier.weight(1f)) {
				KText(name, style = KhyloType.Display, maxLines = 1)
				titles?.let { KText("${it.size} titles in your library", style = KhyloType.Caption) }
				person?.overview?.let {
					Spacer(Modifier.height(8.dp))
					KText(it, style = KhyloType.Body, maxLines = 3)
				}
			}
		}
		when (val list = titles) {
			null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
			else -> LazyVerticalGrid(
				columns = GridCells.Adaptive(170.dp),
				contentPadding = PaddingValues(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, bottom = 48.dp, top = 8.dp),
				horizontalArrangement = Arrangement.spacedBy(24.dp),
				verticalArrangement = Arrangement.spacedBy(28.dp),
			) {
				items(list, key = { it.id }) { item ->
					KCard(repository.imageUrl(item), item.name.orEmpty(), onClick = { openItem(navigator, item) }, width = 170.dp, subtitle = item.productionYear?.toString(), watched = item.userData?.played == true)
				}
			}
		}
	}
}

/** An audiobook part is named for its book (the album); everything else by its own name. */
fun bookTitle(item: BaseItemDto): String =
	item.album?.takeIf { item.type == BaseItemKind.AUDIO_BOOK && it.isNotBlank() } ?: item.name.orEmpty()

/** Where Play takes an item: music and audiobooks have their own players, books open to read. */
fun playRoute(item: BaseItemDto, startPositionMs: Long? = null): Route = when (item.type) {
	BaseItemKind.MUSIC_ALBUM, BaseItemKind.AUDIO, BaseItemKind.MUSIC_ARTIST -> Route.Music(item)
	BaseItemKind.AUDIO_BOOK -> Route.Audiobook(item)
	BaseItemKind.BOOK -> Route.Reader(item)
	else -> Route.Player(item, startPositionMs)
}

private fun playLabel(item: BaseItemDto) = when (item.type) {
	BaseItemKind.AUDIO_BOOK -> "Listen"
	BaseItemKind.BOOK -> "Read"
	BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST -> "Play all"
	else -> "Play"
}

private fun resumeLabel(item: BaseItemDto) = when (item.type) {
	BaseItemKind.AUDIO_BOOK -> "Keep listening"
	BaseItemKind.BOOK -> "Keep reading"
	else -> "Resume"
}

private fun playIcon(item: BaseItemDto) = when (item.type) {
	BaseItemKind.AUDIO_BOOK -> KIcons.Audiobook
	BaseItemKind.BOOK -> KIcons.Book
	else -> KIcons.Play
}

@Composable
private fun TrackListRow(index: Int, track: BaseItemDto, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(if (focused) Color.White else Color.Transparent)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 14.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		KText(
			"${track.indexNumber ?: index + 1}",
			style = KhyloType.Label,
			color = if (focused) Color(0xFF555555) else KhyloColors.TextMuted,
			modifier = Modifier.width(40.dp),
		)
		KText(track.name.orEmpty(), style = KhyloType.Label, color = if (focused) KhyloColors.OnFocus else KhyloColors.Text, maxLines = 1, modifier = Modifier.weight(1f))
		track.runTimeTicks?.let {
			KText(org.khylo.tv.player.formatTime(it / 10_000), style = KhyloType.Caption, color = if (focused) Color(0xFF555555) else KhyloColors.TextMuted)
		}
	}
}
