package org.khylo.tv.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.ImageType
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSectionTitle
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.design.rememberedFocus
import org.khylo.tv.player.formatTime
import org.koin.compose.koinInject

/**
 * An album, the way a music app shows it: the cover large on the left over a soft wash of its own
 * colors, and on the right what it is, Play and Shuffle, and the songs.
 */
@Composable
fun MusicAlbumScreen(album: BaseItemDto) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val tracks by produceState(emptyList<BaseItemDto>(), album.id) {
		value = runCatching { repository.albumTracks(album.id) }.getOrDefault(emptyList())
	}
	val cover = repository.imageUrl(album, ImageType.PRIMARY, 900)
	val artistName = album.albumArtist ?: album.artists?.firstOrNull()
	val artistId = album.albumArtists?.firstOrNull()?.id ?: album.artistItems?.firstOrNull()?.id

	Box(Modifier.fillMaxSize().background(KhyloColors.Background)) {
		CoverWash(repository.imageUrl(album, ImageType.PRIMARY, 64))

		Row(Modifier.fillMaxSize().padding(start = 72.dp, end = 64.dp, top = KhyloSpacing.NavHeight + 28.dp)) {
			// The cover
			Box(
				Modifier
					.size(340.dp)
					.shadow(36.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black)
					.clip(RoundedCornerShape(14.dp))
					.background(KhyloColors.SurfaceRaised),
				contentAlignment = Alignment.Center,
			) {
				if (cover != null) KImage(cover, Modifier.fillMaxSize()) else KIcon(KIcons.Music, Color(0x55FFFFFF), Modifier.size(96.dp))
			}
			Spacer(Modifier.width(56.dp))

			LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 120.dp)) {
				item(key = "header") {
					Column {
						KText(releaseKind(tracks.size), style = KhyloType.Eyebrow, color = Color(0xB3FFFFFF))
						Spacer(Modifier.height(10.dp))
						KText(
							album.name.orEmpty(),
							style = KhyloType.Display.copy(fontSize = 46.sp, lineHeight = 50.sp),
							maxLines = 2,
						)
						artistName?.let {
							Spacer(Modifier.height(8.dp))
							KText(it, style = KhyloType.Heading.copy(fontSize = 22.sp), color = Color(0xE6FFFFFF), maxLines = 1)
						}
						Spacer(Modifier.height(10.dp))
						val minutes = tracks.sumOf { it.runTimeTicks ?: 0L } / 600_000_000
						val details = listOfNotNull(
							album.productionYear?.toString(),
							tracks.size.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" },
							minutes.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60} hr ${it % 60} min" else "$it min" },
							album.genres?.firstOrNull(),
						)
						KText(details.joinToString("  ·  "), style = KhyloType.Meta, color = Color(0xB3FFFFFF), maxLines = 1)
						Spacer(Modifier.height(24.dp))
						Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
							KButton("Play", onClick = { navigator.go(Route.Music(album)) }, icon = KIcons.Play, style = KButtonStyle.Primary)
							KButton("Shuffle", onClick = { navigator.go(Route.Music(album, shuffle = true)) }, icon = KIcons.Shuffle)
							if (artistId != null) KButton("View artist", onClick = { navigator.go(Route.Details(artistId)) }, icon = KIcons.Music)
						}
						Spacer(Modifier.height(28.dp))
					}
				}

				// Songs, with a heading for each disc when there's more than one
				val discs = tracks.groupBy { it.parentIndexNumber ?: 1 }
				discs.forEach { (disc, songs) ->
					if (discs.size > 1) {
						item(key = "disc$disc") {
							KText("DISC $disc", style = KhyloType.Eyebrow, color = Color(0x99FFFFFF), modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 6.dp))
						}
					}
					items(songs, key = { it.id }) { song ->
						SongRow(song, albumArtist = artistName) { navigator.go(Route.Music(song)) }
					}
				}
			}
		}
	}
}

/**
 * An artist: their photo across the top with their name, Play and Shuffle, then their albums,
 * albums they appear on, and similar artists.
 */
@Composable
fun MusicArtistScreen(artist: BaseItemDto) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val albums by produceState(emptyList<BaseItemDto>() to emptyList<BaseItemDto>(), artist.id) {
		value = runCatching { repository.artistAlbums(artist.id) }.getOrDefault(emptyList<BaseItemDto>() to emptyList())
	}
	val similar by produceState(emptyList<BaseItemDto>(), artist.id) {
		value = runCatching { repository.similarArtists(artist.id) }.getOrDefault(emptyList())
	}
	val backdrop = repository.backdropUrl(artist, 1920)
	val photo = repository.imageUrl(artist, ImageType.PRIMARY, 1280)

	Box(Modifier.fillMaxSize().background(KhyloColors.Background)) {
		if (backdrop == null) CoverWash(repository.imageUrl(artist, ImageType.PRIMARY, 64))

		LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
			item(key = "hero") {
				Box(Modifier.fillMaxWidth().height(440.dp)) {
					if (backdrop != null) {
						KImage(backdrop, Modifier.fillMaxSize(), ContentScale.Crop, Alignment.TopCenter)
						Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.35f to Color.Transparent, 0.75f to Color(0xB30B0C0E), 1f to KhyloColors.Background)))
						Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xCC0B0C0E), 0.55f to Color.Transparent)))
					} else if (photo != null) {
						// Without a wide photo, their portrait fills the right of the page and fades into it
						Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.62f).fillMaxHeight()) {
							KImage(photo, Modifier.fillMaxSize(), ContentScale.Crop, Alignment.TopCenter)
							Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to KhyloColors.Background, 0.45f to Color(0x800B0C0E), 1f to Color.Transparent)))
							Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to KhyloColors.Background)))
						}
					}
					Row(Modifier.align(Alignment.BottomStart).padding(start = 72.dp, end = 64.dp, bottom = 28.dp), verticalAlignment = Alignment.Bottom) {
						Column {
							KText("ARTIST", style = KhyloType.Eyebrow, color = Color(0xB3FFFFFF))
							Spacer(Modifier.height(8.dp))
							KText(artist.name.orEmpty(), style = KhyloType.Display.copy(fontSize = 64.sp, lineHeight = 66.sp, letterSpacing = (-1.5).sp), maxLines = 1)
							val count = albums.first.size
							if (count > 0) {
								Spacer(Modifier.height(6.dp))
								KText(if (count == 1) "1 album" else "$count albums", style = KhyloType.Meta, color = Color(0xB3FFFFFF))
							}
							Spacer(Modifier.height(22.dp))
							Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
								KButton("Play", onClick = { navigator.go(Route.Music(artist)) }, icon = KIcons.Play, style = KButtonStyle.Primary)
								KButton("Shuffle", onClick = { navigator.go(Route.Music(artist, shuffle = true)) }, icon = KIcons.Shuffle)
							}
						}
					}
				}
			}

			listOf("Albums" to albums.first, "Appears on" to albums.second).forEach { (title, list) ->
				if (list.isEmpty()) return@forEach
				item(key = title) {
					Column(Modifier.padding(top = 26.dp)) {
						KSectionTitle(title, Modifier.padding(start = 72.dp))
						LazyRow(contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
							items(list, key = { it.id }) { album ->
								AlbumCard(album, repository, size = 190.dp, showYear = true) { navigator.go(Route.Details(album.id)) }
							}
						}
					}
				}
			}

			if (similar.isNotEmpty()) {
				item(key = "similar") {
					Column(Modifier.padding(top = 26.dp)) {
						KSectionTitle("Similar artists", Modifier.padding(start = 72.dp))
						LazyRow(contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
							items(similar, key = { it.id }) { other ->
								ArtistCircle(other, repository, size = 160.dp) { navigator.go(Route.Details(other.id)) }
							}
						}
					}
				}
			}
		}
	}
}

/** One song in an album's list: number, title (and any featured artists), and length. */
@Composable
private fun SongRow(song: BaseItemDto, albumArtist: String?, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		Modifier
			.fillMaxWidth()
			.widthIn(max = 900.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(if (focused) Color.White else Color.Transparent)
			.rememberedFocus("song|${song.id}")
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 14.dp, vertical = 11.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		KText(
			"${song.indexNumber ?: ""}",
			style = KhyloType.Label,
			color = if (focused) Color(0xFF666666) else Color(0x80FFFFFF),
			modifier = Modifier.width(40.dp),
		)
		Column(Modifier.weight(1f)) {
			KText(song.name.orEmpty(), style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), color = if (focused) KhyloColors.OnFocus else Color.White, maxLines = 1)
			// Name the other artists on a song when they aren't just the album's artist
			val others = song.artists.orEmpty().filter { it != albumArtist }
			if (others.isNotEmpty()) {
				KText(others.joinToString(", "), style = KhyloType.Caption, color = if (focused) Color(0xFF666666) else Color(0x99FFFFFF), maxLines = 1)
			}
		}
		song.runTimeTicks?.let {
			KText(formatTime(it / 10_000), style = KhyloType.Caption, color = if (focused) Color(0xFF666666) else Color(0x99FFFFFF))
		}
	}
}

/** A soft, darkened wash of an image's colors behind the whole page (from a tiny version of it). */
@Composable
private fun CoverWash(url: String?) {
	if (url != null) {
		KImage(url, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = 0.6f }.blur(70.dp), ContentScale.Crop)
	}
	Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.5f to Color(0xB30B0C0E), 1f to Color(0xF20B0C0E))))
	Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0x66000000), 1f to Color.Transparent)))
}

/** Album, EP or single, by how many songs it has. */
private fun releaseKind(songs: Int) = when {
	songs == 0 -> "ALBUM"
	songs <= 3 -> "SINGLE"
	songs <= 6 -> "EP"
	else -> "ALBUM"
}
