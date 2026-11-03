package org.khylo.tv.listen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.ImageType
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.rememberedFocus
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSectionTitle
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KTab
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.library.LibraryScreen
import org.koin.compose.koinInject
import java.util.UUID

private enum class MusicTab(val label: String) {
	ListenNow("Listen Now"),
	Albums("Albums"),
	Artists("Artists"),
	Genres("Genres"),
}

private val lastMusicTab = mutableMapOf<UUID, MusicTab>()

/** One row of the Listen Now page. */
private sealed interface MusicRow {
	val title: String

	data class Albums(override val title: String, val albums: List<BaseItemDto>) : MusicRow
	data class Artists(override val title: String, val artists: List<BaseItemDto>) : MusicRow
}

/**
 * Music, the way music apps do it on a TV: a Listen Now page (a featured artist, what you played
 * lately, new additions, artists to explore and a few genres), then Albums, Artists and Genres.
 */
@Composable
fun MusicLibraryScreen(view: BaseItemDto) {
	var tab by remember(view.id) { mutableStateOf(lastMusicTab[view.id] ?: MusicTab.ListenNow) }
	var searching by remember(view.id) { mutableStateOf(org.khylo.tv.app.SearchMemory.library.containsKey(view.id)) }

	Column(Modifier.fillMaxSize()) {
		Row(
			Modifier.fillMaxWidth().padding(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = 16.dp, bottom = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			KText(view.name.orEmpty(), style = KhyloType.Title, maxLines = 1)
			Spacer(Modifier.width(28.dp))
			org.khylo.tv.library.SearchTab(KIcons.Search, searching, { searching = true })
			MusicTab.entries.forEach { entry ->
				KTab(entry.label, entry == tab && !searching, {
					searching = false; org.khylo.tv.app.SearchMemory.library.remove(view.id)
					tab = entry
					lastMusicTab[view.id] = entry
				})
			}
		}
		if (searching) org.khylo.tv.library.LibrarySearch(view) else when (tab) {
			MusicTab.ListenNow -> ListenNow(view)
			MusicTab.Albums -> LibraryScreen(view, showTitle = false)
			MusicTab.Artists -> ArtistsGrid()
			MusicTab.Genres -> GenresGrid(view)
		}
	}
}

@Composable
private fun ListenNow(view: BaseItemDto) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val featured by produceState<BaseItemDto?>(null, view.id) {
		value = runCatching { repository.musicArtists(limit = 1, random = true, withPhoto = true).first.firstOrNull() }.getOrNull()
	}
	val rows by produceState<List<MusicRow>?>(null, view.id) {
		value = runCatching {
			coroutineScope {
				val played = async { runCatching { repository.recentlyPlayedAlbums(view) }.getOrDefault(emptyList()) }
				val added = async { runCatching { repository.recentlyAddedAlbums(view) }.getOrDefault(emptyList()) }
				val artists = async { runCatching { repository.musicArtists(limit = 20, random = true).first }.getOrDefault(emptyList()) }
				val discover = async { runCatching { repository.randomAlbums(view) }.getOrDefault(emptyList()) }
				val genres = runCatching { repository.musicGenres(view) }.getOrDefault(emptyList())
					.shuffled().take(3)
					.map { genre -> async { MusicRow.Albums(genre.name.orEmpty(), runCatching { repository.randomAlbums(view, genre.name) }.getOrDefault(emptyList())) } }
				buildList {
					add(MusicRow.Albums("Recently played", played.await()))
					add(MusicRow.Albums("Recently added", added.await()))
					add(MusicRow.Artists("Artists to explore", artists.await()))
					genres.forEach { add(it.await()) }
					add(MusicRow.Albums("Discover albums", discover.await()))
				}.filter {
					when (it) {
						is MusicRow.Albums -> it.albums.isNotEmpty()
						is MusicRow.Artists -> it.artists.isNotEmpty()
					}
				}
			}
		}.getOrDefault(emptyList())
	}

	val listState = rememberLazyListState()
	var focusedRow by remember { mutableIntStateOf(-1) }
	LaunchedEffect(focusedRow) { if (focusedRow >= 0) listState.animateScrollToItem(focusedRow) }

	when (val loaded = rows) {
		null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
		else -> org.khylo.tv.design.SnapRows {
		LazyColumn(
			state = listState,
			contentPadding = PaddingValues(top = 8.dp, bottom = 260.dp),
			verticalArrangement = Arrangement.spacedBy(28.dp),
		) {
			featured?.let { artist ->
				item(key = "featured") {
					// The banner is the top "row": landing on it scrolls back up to it
					Box(Modifier.onFocusChanged { if (it.hasFocus) focusedRow = 0 }) {
					FeaturedArtist(
						artist,
						repository,
						onPlay = { navigator.go(Route.Music(artist)) },
						onShuffle = { navigator.go(Route.Music(artist, shuffle = true)) },
						onOpen = { navigator.go(Route.Details(artist.id)) },
					)
					}
				}
			}
			val offset = if (featured != null) 1 else 0
			loaded.forEachIndexed { index, row ->
				item(key = row.title) {
					Column {
						KSectionTitle(row.title, Modifier.padding(start = KhyloSpacing.Gutter))
						org.khylo.tv.design.SnapRow {
						LazyRow(
							contentPadding = PaddingValues(horizontal = KhyloSpacing.Gutter, vertical = 10.dp),
							horizontalArrangement = Arrangement.spacedBy(22.dp),
						) {
							when (row) {
								is MusicRow.Albums -> items(row.albums, key = { it.id }) { album ->
									AlbumCard(album, repository, onFocus = { focusedRow = index + offset }) { navigator.go(Route.Details(album.id)) }
								}
								is MusicRow.Artists -> items(row.artists, key = { it.id }) { artist ->
									ArtistCircle(artist, repository, size = 150.dp, onFocus = { focusedRow = index + offset }) { navigator.go(Route.Details(artist.id)) }
								}
							}
						}
						}
					}
				}
			}
		}
		}
	}
}

/**
 * One artist across the full width of the page: their photo edge to edge, fading into the page on
 * every side, with their name large, Play, Shuffle and View artist.
 */
@Composable
private fun FeaturedArtist(artist: BaseItemDto, repository: KhyloRepository, onPlay: () -> Unit, onShuffle: () -> Unit, onOpen: () -> Unit) {
	Box(Modifier.fillMaxWidth().height(340.dp)) {
		KImage(repository.imageUrlFor(artist.id, null, 1920, ImageType.BACKDROP), Modifier.fillMaxSize(), ContentScale.Crop, Alignment.TopCenter)
		Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF20B0C0E), 0.45f to Color(0x800B0C0E), 1f to Color(0x1A0B0C0E))))
		Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x660B0C0E), 0.3f to Color.Transparent, 0.75f to Color.Transparent, 1f to KhyloColors.Background)))
		Column(Modifier.align(Alignment.BottomStart).padding(start = KhyloSpacing.Gutter, bottom = 30.dp)) {
			KText("FEATURED ARTIST", style = KhyloType.Eyebrow, color = Color(0xB3FFFFFF))
			Spacer(Modifier.height(8.dp))
			KText(artist.name.orEmpty(), style = KhyloType.Display.copy(fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-1.2).sp), maxLines = 1)
			Spacer(Modifier.height(20.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				KButton("Play", onClick = onPlay, icon = KIcons.Play, style = KButtonStyle.Primary)
				KButton("Shuffle", onClick = onShuffle, icon = KIcons.Shuffle)
				KButton("View artist", onClick = onOpen, icon = KIcons.Music)
			}
		}
	}
}

/** An album: square cover (a play badge appears when it's focused), its title and artist or year underneath. */
@Composable
internal fun AlbumCard(album: BaseItemDto, repository: KhyloRepository, size: Dp = 180.dp, showYear: Boolean = false, onFocus: () -> Unit = {}, onClick: () -> Unit) {
	val focusKey = "album|${album.id}"
	val (source, focused) = rememberFocusState()
	val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "albumScale")
	if (focused) LaunchedEffect(Unit) { onFocus() }
	Column(Modifier.width(size)) {
		Box(
			Modifier
				.size(size)
				.scale(scale)
				.shadow(if (focused) 20.dp else 4.dp, RoundedCornerShape(8.dp))
				.clip(RoundedCornerShape(8.dp))
				.background(KhyloColors.SurfaceRaised)
				.then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
				.rememberedFocus(focusKey)
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			val art = repository.imageUrl(album, ImageType.PRIMARY, 400)
			if (art != null) KImage(art, Modifier.fillMaxSize()) else KIcon(KIcons.Music, Color(0x55FFFFFF), Modifier.size(56.dp))
			if (focused) {
				Box(
					Modifier
						.align(Alignment.BottomEnd)
						.padding(10.dp)
						.size(40.dp)
						.shadow(10.dp, CircleShape)
						.clip(CircleShape)
						.background(Color.White),
					contentAlignment = Alignment.Center,
				) {
					KIcon(KIcons.Play, KhyloColors.OnFocus, Modifier.size(24.dp))
				}
			}
		}
		Spacer(Modifier.height(10.dp))
		KText(album.name.orEmpty(), style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), color = if (focused) Color.White else Color(0xE6FFFFFF), maxLines = 1)
		val under = if (showYear) album.productionYear?.toString() else album.albumArtist ?: album.artists?.firstOrNull()
		under?.let { KText(it, style = KhyloType.Caption, maxLines = 1) }
	}
}

/** An artist: a round photo with their name. */
@Composable
internal fun ArtistCircle(artist: BaseItemDto, repository: KhyloRepository, size: Dp, onFocus: () -> Unit = {}, onClick: () -> Unit) {
	val focusKey = "artist|${artist.id}"
	val (source, focused) = rememberFocusState()
	val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "artistScale")
	if (focused) LaunchedEffect(Unit) { onFocus() }
	Column(Modifier.width(size + 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		Box(
			Modifier
				.size(size)
				.scale(scale)
				.shadow(if (focused) 20.dp else 4.dp, CircleShape)
				.clip(CircleShape)
				.background(KhyloColors.SurfaceRaised)
				.then(if (focused) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
				.rememberedFocus(focusKey)
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			val photo = repository.imageUrl(artist, ImageType.PRIMARY, 400)
			if (photo != null) {
				KImage(photo, Modifier.fillMaxSize())
			} else {
				KText(artist.name.orEmpty().take(1).uppercase(), style = KhyloType.Display, color = Color(0x66FFFFFF))
			}
		}
		Spacer(Modifier.height(10.dp))
		KText(artist.name.orEmpty(), style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, textAlign = TextAlign.Center)
	}
}

/** Every artist, A to Z, loaded a page at a time. */
@Composable
private fun ArtistsGrid() {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val artists = remember { mutableStateListOf<BaseItemDto>() }
	var total by remember { mutableIntStateOf(-1) }
	var loading by remember { mutableStateOf(false) }
	val gridState = rememberLazyGridState()

	suspend fun loadMore() {
		if (loading || (total >= 0 && artists.size >= total)) return
		loading = true
		runCatching { repository.musicArtists(startIndex = artists.size, limit = 60) }.onSuccess { (page, count) ->
			artists.addAll(page)
			total = count
		}
		loading = false
	}
	LaunchedEffect(Unit) { loadMore() }
	LaunchedEffect(gridState) {
		snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
			.collect { last -> if (last >= artists.size - 12) loadMore() }
	}

	if (total < 0) {
		Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
		return
	}
	LazyVerticalGrid(
		state = gridState,
		columns = GridCells.Adaptive(170.dp),
		contentPadding = PaddingValues(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = 16.dp, bottom = 48.dp),
		horizontalArrangement = Arrangement.spacedBy(20.dp),
		verticalArrangement = Arrangement.spacedBy(26.dp),
	) {
		items(artists, key = { it.id }) { artist ->
			Box(contentAlignment = Alignment.Center) {
				ArtistCircle(artist, repository, size = 150.dp) { navigator.go(Route.Details(artist.id)) }
			}
		}
	}
}

/** Genre colours, so each genre tile has its own mood. */
private val genreColors = listOf(
	Color(0xFFB4442E), Color(0xFF2E6CB4), Color(0xFF7A3BB0), Color(0xFF1F8A6B), Color(0xFFC07A1F),
	Color(0xFFB0306A), Color(0xFF3B4FB0), Color(0xFF5E7A1F), Color(0xFF8A2E2E), Color(0xFF2E8A9E),
)

/** Genres as coloured tiles; each opens that genre's albums. */
@Composable
private fun GenresGrid(view: BaseItemDto) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val genres by produceState<List<BaseItemDto>?>(null, view.id) {
		value = runCatching { repository.musicGenres(view) }.getOrDefault(emptyList())
	}
	when (val list = genres) {
		null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
		else -> LazyVerticalGrid(
			columns = GridCells.Fixed(5),
			contentPadding = PaddingValues(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = 16.dp, bottom = 48.dp),
			horizontalArrangement = Arrangement.spacedBy(18.dp),
			verticalArrangement = Arrangement.spacedBy(18.dp),
		) {
			items(list, key = { it.id }) { genre ->
				val (source, focused) = rememberFocusState()
				val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "genreScale")
				val color = genreColors[Math.floorMod(genre.name.orEmpty().hashCode(), genreColors.size)]
				Box(
					Modifier
						.fillMaxWidth()
						.aspectRatio(16f / 9f)
						.scale(scale)
						.shadow(if (focused) 18.dp else 0.dp, RoundedCornerShape(10.dp))
						.clip(RoundedCornerShape(10.dp))
						.background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f).compositeOver(Color.Black))))
						.then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
						.clickable(interactionSource = source, indication = null) { navigator.go(Route.Genre(view, genre)) }
						.padding(16.dp),
				) {
					KText(genre.name.orEmpty(), style = KhyloType.Heading.copy(fontWeight = FontWeight.Bold), maxLines = 2, modifier = Modifier.align(Alignment.BottomStart))
				}
			}
		}
	}
}
