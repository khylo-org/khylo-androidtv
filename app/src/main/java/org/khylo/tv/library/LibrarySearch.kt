package org.khylo.tv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.CollectionType
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.data.LibrarySearchResults
import org.khylo.tv.design.KCard
import org.khylo.tv.design.KCardShape
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSectionTitle
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KTextField
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.home.openItem
import org.khylo.tv.player.formatTime
import org.koin.compose.koinInject
import java.util.UUID


/** A magnifying-glass tab that sits at the start of a library's tabs. */
@Composable
fun SearchTab(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		Spacer(Modifier.height(5.dp))
		Box(
			Modifier
				.size(34.dp)
				.clip(CircleShape)
				.background(if (focused) KhyloColors.Focus else Color.Transparent)
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			KIcon(icon, if (focused) KhyloColors.OnFocus else if (selected) KhyloColors.Text else Color(0x99FFFFFF), Modifier.size(20.dp), "Search this library")
		}
		Spacer(Modifier.height(3.dp))
		Box(Modifier.width(18.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (selected && !focused) KhyloColors.Accent else Color.Transparent))
	}
}

/**
 * Search inside one library: type, and matching titles from just this library appear underneath.
 * Music shows artists, albums and songs.
 */
@Composable
fun LibrarySearch(view: BaseItemDto) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	var query by remember(view.id) { mutableStateOf(org.khylo.tv.app.SearchMemory.library[view.id].orEmpty()) }
	LaunchedEffect(query) { org.khylo.tv.app.SearchMemory.library[view.id] = query }
	val term = query.trim()
	val results by produceState<LibrarySearchResults?>(LibrarySearchResults(), view.id, term) {
		if (term.length < 2) {
			value = LibrarySearchResults()
			return@produceState
		}
		value = null
		delay(300)
		value = runCatching { repository.searchLibrary(view, term) }.getOrDefault(LibrarySearchResults())
	}
	val fieldFocus = remember { FocusRequester() }
	LaunchedEffect(Unit) {
		delay(80)
		runCatching { fieldFocus.requestFocus() }
	}
	val isMusic = view.collectionType == CollectionType.MUSIC

	Column(Modifier.fillMaxSize()) {
		KTextField(
			value = query,
			onValueChange = { query = it },
			label = "Search ${view.name.orEmpty()}",
			placeholder = when (view.collectionType) {
				CollectionType.MUSIC -> "Artists, albums, songs"
				CollectionType.MOVIES -> "Movie titles"
				CollectionType.TVSHOWS -> "Show titles"
				else -> "Titles in ${view.name.orEmpty()}"
			},
			modifier = Modifier.padding(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = 8.dp).width(640.dp).focusRequester(fieldFocus),
		)
		Spacer(Modifier.height(18.dp))

		val found = results
		when {
			term.length < 2 -> KText("Type at least two letters.", style = KhyloType.Body, modifier = Modifier.padding(horizontal = KhyloSpacing.Gutter))
			found == null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) { KSpinner() }
			found.isEmpty -> KText("Nothing in ${view.name.orEmpty()} matches “$term”.", style = KhyloType.Body, modifier = Modifier.padding(horizontal = KhyloSpacing.Gutter))
			isMusic -> MusicResults(found, repository, onOpen = { navigator.go(Route.Details(it.id)) }, onSong = { navigator.go(Route.Music(it)) })
			else -> LazyVerticalGrid(
				columns = GridCells.Adaptive(160.dp),
				contentPadding = PaddingValues(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, bottom = 48.dp, top = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(22.dp),
				verticalArrangement = Arrangement.spacedBy(26.dp),
			) {
				items(found.main, key = { it.id }) { item ->
					KCard(
						watched = item.userData?.played == true,
						imageUrl = repository.imageUrl(item),
						title = item.album?.takeIf { item.type == BaseItemKind.AUDIO_BOOK } ?: item.name.orEmpty(),
						subtitle = item.productionYear?.toString() ?: item.albumArtist,
						width = 160.dp,
						showTitle = true,
						onClick = { openItem(navigator, item) },
					)
				}
			}
		}
	}
}

@Composable
private fun MusicResults(found: LibrarySearchResults, repository: KhyloRepository, onOpen: (BaseItemDto) -> Unit, onSong: (BaseItemDto) -> Unit) {
	LazyColumn(contentPadding = PaddingValues(bottom = 240.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
		if (found.artists.isNotEmpty()) item(key = "artists") {
			Column {
				KSectionTitle("Artists", Modifier.padding(start = KhyloSpacing.Gutter))
				LazyRow(contentPadding = PaddingValues(horizontal = KhyloSpacing.Gutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
					items(found.artists, key = { it.id }) { artist ->
						KCard(
							imageUrl = repository.imageUrl(artist),
							title = artist.name.orEmpty(),
							shape = KCardShape.Square,
							width = 140.dp,
							showTitle = true,
							onClick = { onOpen(artist) },
						)
					}
				}
			}
		}
		if (found.main.isNotEmpty()) item(key = "albums") {
			Column {
				KSectionTitle("Albums", Modifier.padding(start = KhyloSpacing.Gutter))
				LazyRow(contentPadding = PaddingValues(horizontal = KhyloSpacing.Gutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
					items(found.main, key = { it.id }) { album ->
						KCard(
							imageUrl = repository.imageUrl(album),
							title = album.name.orEmpty(),
							subtitle = album.albumArtist,
							shape = KCardShape.Square,
							width = 160.dp,
							showTitle = true,
							onClick = { onOpen(album) },
						)
					}
				}
			}
		}
		if (found.songs.isNotEmpty()) item(key = "songs") {
			Column(Modifier.padding(horizontal = KhyloSpacing.Gutter)) {
				KSectionTitle("Songs")
				Spacer(Modifier.height(6.dp))
				found.songs.forEach { song -> SongRow(song, repository) { onSong(song) } }
			}
		}
	}
}

@Composable
private fun SongRow(song: BaseItemDto, repository: KhyloRepository, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(if (focused) Color.White else Color.Transparent)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 10.dp, vertical = 7.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(KhyloColors.SurfaceRaised)) {
			val art = repository.imageUrl(song) ?: song.albumId?.let { id -> song.albumPrimaryImageTag?.let { repository.imageUrlFor(id, it, 120) } }
			if (art != null) KImage(art, Modifier.fillMaxSize())
		}
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f)) {
			KText(song.name.orEmpty(), style = KhyloType.Label, color = if (focused) KhyloColors.OnFocus else KhyloColors.Text, maxLines = 1)
			KText(
				listOfNotNull(song.albumArtist ?: song.artists?.firstOrNull(), song.album).joinToString("  ·  "),
				style = KhyloType.Caption,
				color = if (focused) Color(0xFF555555) else KhyloColors.TextMuted,
				maxLines = 1,
			)
		}
		song.runTimeTicks?.let { KText(formatTime(it / 10_000), style = KhyloType.Caption, color = if (focused) Color(0xFF555555) else KhyloColors.TextMuted) }
	}
}
