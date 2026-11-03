package org.khylo.tv.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.CollectionType
import org.khylo.sdk.model.api.ImageType
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.data.KhyloRow
import org.khylo.tv.design.KCard
import org.khylo.tv.design.KImage
import org.khylo.tv.design.LocalAmbient
import org.khylo.tv.design.rememberedFocus
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KTab
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.home.ItemRow
import org.khylo.tv.home.openItem
import org.koin.compose.koinInject
import java.util.UUID

/** Recommended rows from last time for each library, shown straight away while fresh ones load. */
private object RecommendedCache {
	val rows = mutableMapOf<UUID, List<KhyloRow>>()
}

private enum class LibraryTab(val label: String) {
	Recommended("Recommended"),
	Browse("Browse"),
	Collections("Collections"),
	Playlists("Playlists"),
	Categories("Categories"),
}

/** Which tab each library was left on, so coming back from a title returns to the same place. */
private val lastTab = mutableMapOf<UUID, LibraryTab>()

/**
 * Movies and TV Shows, laid out like Plex: a handful of tabs (Recommended, Browse, Collections,
 * Playlists, Categories) instead of everything across the top at once.
 */
@Composable
fun MediaLibraryScreen(view: BaseItemDto) {
	val repository = koinInject<KhyloRepository>()
	var tab by remember(view.id) { mutableStateOf(lastTab[view.id] ?: LibraryTab.Recommended) }
	var searching by remember(view.id) { mutableStateOf(org.khylo.tv.app.SearchMemory.library.containsKey(view.id)) }
	val isMovies = view.collectionType == CollectionType.MOVIES

	val collections by produceState(emptyList<BaseItemDto>(), view.id) {
		if (isMovies) value = runCatching { repository.collections() }.getOrDefault(emptyList())
	}
	val playlists by produceState(emptyList<BaseItemDto>(), view.id) {
		value = runCatching { repository.playlists() }.getOrDefault(emptyList())
	}
	// Collections and Playlists only show up once there's something in them
	val tabs = LibraryTab.entries.filter {
		when (it) {
			LibraryTab.Collections -> collections.isNotEmpty()
			LibraryTab.Playlists -> playlists.isNotEmpty()
			else -> true
		}
	}

	// The artwork of whatever you're on fills the page behind the rows, dimmed
	val ambient = LocalAmbient.current
	val onFocusItem: (BaseItemDto) -> Unit = { ambient?.show(repository.backdropUrl(it, 1280), isBackdrop = true) }

	Box(Modifier.fillMaxSize()) {
	Column(Modifier.fillMaxSize().padding(top = KhyloSpacing.NavHeight)) {
		Row(
			Modifier.fillMaxWidth().padding(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = 16.dp, bottom = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			KText(view.name.orEmpty(), style = KhyloType.Title, maxLines = 1)
			Spacer(Modifier.width(28.dp))
			SearchTab(org.khylo.tv.design.KIcons.Search, searching, { searching = true })
			tabs.forEach { entry ->
				KTab(entry.label, entry == tab && !searching, {
					searching = false; org.khylo.tv.app.SearchMemory.library.remove(view.id)
					tab = entry
					lastTab[view.id] = entry
				})
			}
		}

		if (searching) LibrarySearch(view) else when (tab) {
			LibraryTab.Recommended -> Recommended(view, onFocusItem)
			LibraryTab.Browse -> LibraryScreen(view, showTitle = false, onFocusItem = onFocusItem)
			LibraryTab.Collections -> PosterGrid(collections)
			LibraryTab.Playlists -> PosterGrid(playlists)
			LibraryTab.Categories -> Categories(view)
		}
	}
	}
}

/** Rows of picks for this library; the row you're on snaps into full view. */
@Composable
private fun Recommended(view: BaseItemDto, onFocusItem: (BaseItemDto) -> Unit) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val rows by produceState(RecommendedCache.rows[view.id], view.id) {
		value = runCatching { repository.libraryRows(view) }.getOrDefault(emptyList()).also { RecommendedCache.rows[view.id] = it }
	}
	// Start with the first title's artwork behind the page
	LaunchedEffect(rows) { rows?.firstOrNull()?.items?.firstOrNull()?.let(onFocusItem) }
	val listState = rememberLazyListState()
	// The row you're on, kept across visits so coming back doesn't jump
	var focusedRow by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(-1) }
	LaunchedEffect(focusedRow) { if (focusedRow >= 0) listState.animateScrollToItem(focusedRow) }

	when (val loaded = rows) {
		null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
		else -> org.khylo.tv.design.SnapRows {
			LazyColumn(
				state = listState,
				contentPadding = PaddingValues(top = 8.dp, bottom = 320.dp),
				verticalArrangement = Arrangement.spacedBy(26.dp),
			) {
				itemsIndexed(loaded, key = { _, row -> row.title }) { index, row ->
					ItemRow(row, repository, onFocus = { focusedRow = index; onFocusItem(it) }, onOpen = { openItem(navigator, it) })
				}
			}
		}
	}
}

@Composable
private fun PosterGrid(items: List<BaseItemDto>) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	org.khylo.tv.design.PivotScroll {
	LazyVerticalGrid(
		columns = GridCells.Adaptive(170.dp),
		contentPadding = PaddingValues(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = 12.dp, bottom = 48.dp),
		horizontalArrangement = Arrangement.spacedBy(24.dp),
		verticalArrangement = Arrangement.spacedBy(28.dp),
	) {
		items(items, key = { it.id }) { item ->
			KCard(repository.imageUrl(item), item.name.orEmpty(), onClick = { openItem(navigator, item) }, width = 170.dp, focusKey = item.id.toString(), watched = item.userData?.played == true)
		}
	}
}
}

/** Every category as a picture tile; each opens that category's titles. */
@Composable
private fun Categories(view: BaseItemDto) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val genres by produceState<List<BaseItemDto>?>(null, view.id) {
		value = runCatching { repository.genres(view) }.getOrDefault(emptyList())
	}
	// One standout title per category, each shown by its official landscape art
	val featured by produceState(emptyMap<java.util.UUID, BaseItemDto>(), genres) {
		genres?.let { value = runCatching { repository.genreFeatures(view, it) }.getOrDefault(emptyMap()) }
	}
	when (val list = genres) {
		null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
		else -> org.khylo.tv.design.PivotScroll {
		LazyVerticalGrid(
			columns = GridCells.Fixed(4),
			contentPadding = PaddingValues(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = 12.dp, bottom = 48.dp),
			horizontalArrangement = Arrangement.spacedBy(20.dp),
			verticalArrangement = Arrangement.spacedBy(20.dp),
		) {
			items(list, key = { it.id }) { genre ->
				CategoryTile(genre, featured[genre.id], repository) { navigator.go(Route.Genre(view, genre)) }
			}
		}
		}
	}
}

@Composable
private fun CategoryTile(genre: BaseItemDto, title: BaseItemDto?, repository: KhyloRepository, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "categoryScale")
	val name = genre.name.orEmpty()
	val (deep, rich) = org.khylo.tv.design.genreColors(name)
	val art = title?.let { repository.imageUrl(it, ImageType.THUMB, 640) }
	val ambient = LocalAmbient.current
	if (focused) LaunchedEffect(title?.id) { ambient?.show(repository.backdropUrl(title, 1280) ?: art, isBackdrop = true) }
	Box(
		Modifier
			.fillMaxWidth()
			.aspectRatio(16f / 9f)
			.scale(scale)
			.shadow(if (focused) 28.dp else 8.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black)
			.clip(RoundedCornerShape(12.dp))
			// The genre's color shows while the art loads, and wherever there is none
			.background(Brush.linearGradient(0f to deep, 1f to rich, start = Offset(0f, Float.POSITIVE_INFINITY), end = Offset(Float.POSITIVE_INFINITY, 0f)))
			.border(if (focused) 3.dp else 1.dp, if (focused) KhyloColors.Focus else Color(0x14FFFFFF), RoundedCornerShape(12.dp))
			.rememberedFocus(genre.id.toString())
			.clickable(interactionSource = source, indication = null, onClick = onClick),
	) {
		if (art != null) KImage(art, Modifier.fillMaxSize())
		// The genre's color rises from the bottom and carries the name
		Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.42f to Color.Transparent, 0.78f to deep.copy(alpha = 0.82f), 1f to deep)))
		KText(
			name,
			style = KhyloType.Heading.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
			color = Color.White,
			maxLines = 1,
			modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp, end = 16.dp),
		)
	}
}
