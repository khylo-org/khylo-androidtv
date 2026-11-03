package org.khylo.tv.listen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.ImageType
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.player.rememberAudioPlayer
import org.koin.compose.koinInject
import java.util.UUID

private val MusicAccent = KhyloColors.Accent

/**
 * Now Playing for music, like the big streaming apps on a TV: the artist's photo fills the screen
 * and drifts slowly (the album art stands in when there's no photo), with the song, its cover and
 * the controls set low on the left, and what's next in the corner.
 */
@Composable
fun MusicPlayerScreen(start: BaseItemDto, shuffle: Boolean = false) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val player = rememberAudioPlayer(LocalContext.current)
	val state by player.state.collectAsState()
	var queue by remember { mutableStateOf<List<BaseItemDto>?>(null) }
	var loadError by remember { mutableStateOf(false) }
	val playFocus = remember { FocusRequester() }

	// What to play: a whole album, an artist's songs, or a song with the rest of its album after it
	LaunchedEffect(start.id) {
		runCatching {
			val (items, index) = when (start.type) {
				BaseItemKind.MUSIC_ALBUM -> repository.albumTracks(start.id) to 0
				BaseItemKind.MUSIC_ARTIST -> repository.artistTracks(start.id) to 0
				else -> {
					val album = start.albumId?.let { runCatching { repository.albumTracks(it) }.getOrNull() }.orEmpty()
					val index = album.indexOfFirst { it.id == start.id }
					if (index >= 0) album to index else listOf(start) to 0
				}
			}
			check(items.isNotEmpty())
			queue = items
			// Shuffle starts on a random song, with shuffle on for the rest
			player.play(items, if (shuffle) items.indices.random() else index)
			if (shuffle && !player.exoPlayer.shuffleModeEnabled) player.toggleShuffle()
		}.onFailure { loadError = true }
	}
	LaunchedEffect(queue) {
		if (queue != null) {
			delay(100)
			runCatching { playFocus.requestFocus() }
		}
	}
	BackHandler { navigator.back() }

	val items = queue
	val current = items?.getOrNull(state.index)
	val cover = albumCover(repository, current) ?: repository.imageUrl(start, ImageType.PRIMARY, 900)
	val artistId: UUID? = current?.albumArtists?.firstOrNull()?.id ?: current?.artistItems?.firstOrNull()?.id
	// Only use the artist's photo when the server really has one; otherwise the album art fills the screen
	val artistPhoto by androidx.compose.runtime.produceState<String?>(null, artistId) {
		value = artistId?.let { id ->
			runCatching { repository.item(id) }.getOrNull()?.backdropImageTags?.firstOrNull()?.let { tag -> repository.imageUrlFor(id, tag, 1920, ImageType.BACKDROP) }
		}
	}

	Box(Modifier.fillMaxSize().background(Color.Black)) {
		// The backdrop drifts slowly, so the screen feels alive while music plays
		val drift = rememberInfiniteTransition(label = "drift")
		val zoom by drift.animateFloat(1f, 1.12f, infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
		AnimatedContent(targetState = artistPhoto to cover, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "musicBackdrop") { (photo, art) ->
			Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }) {
				if (photo != null) {
					KImage(photo, Modifier.fillMaxSize(), ContentScale.Crop, Alignment.TopCenter)
				} else if (art != null) {
					// No artist photo: the album art, large and softly focused, lit up rather than dimmed away
					KImage(art, Modifier.fillMaxSize().blur(14.dp).graphicsLayer { alpha = 0.9f }, ContentScale.Crop)
				}
			}
		}
		// Shade only where the text sits (left and bottom); the right side keeps the artwork bright
		Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xCC000000), 0.5f to Color(0x66000000), 1f to Color(0x14000000))))
		Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xD9000000))))

		when {
			loadError -> KText("This music couldn't be loaded.", style = KhyloType.Heading, modifier = Modifier.align(Alignment.Center))
			items == null || current == null -> KSpinner(Modifier.align(Alignment.Center), size = 56.dp)
			else -> {
				// What's next, quietly in the corner
				items.getOrNull(state.index + 1)?.let { next ->
					UpNext(next, albumCover(repository, next), Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 56.dp)) {
						player.next()
					}
				}

				Row(
					Modifier.align(Alignment.BottomStart).padding(start = 72.dp, end = 72.dp, bottom = 56.dp),
					verticalAlignment = Alignment.Bottom,
				) {
					Box(
						Modifier
							.size(232.dp)
							.shadow(32.dp, RoundedCornerShape(10.dp))
							.clip(RoundedCornerShape(10.dp))
							.background(Color(0xFF1A1A1C)),
						contentAlignment = Alignment.Center,
					) {
						if (cover != null) KImage(cover, Modifier.fillMaxSize()) else KIcon(KIcons.Music, Color(0x66FFFFFF), Modifier.size(80.dp))
					}
					Spacer(Modifier.width(40.dp))

					Column(Modifier.width(660.dp)) {
						Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
							PlayingBars(playing = state.isPlaying)
							KText(if (state.isPlaying) "NOW PLAYING" else "PAUSED", style = KhyloType.Eyebrow, color = MusicAccent)
						}
						Spacer(Modifier.height(10.dp))
						KText(current.name.orEmpty(), style = KhyloType.Display, maxLines = 2)
						Spacer(Modifier.height(4.dp))
						KText(
							current.albumArtist ?: current.artists?.joinToString(", ").orEmpty(),
							style = KhyloType.Title.copy(fontWeight = FontWeight.Medium),
							color = Color(0xE6FFFFFF),
							maxLines = 1,
						)
						Spacer(Modifier.height(4.dp))
						KText(
							listOfNotNull(current.album, current.productionYear?.toString()).joinToString("  ·  "),
							style = KhyloType.Label,
							color = Color(0x99FFFFFF),
							maxLines = 1,
						)
						Spacer(Modifier.height(22.dp))
						ListenSeekBar(state.positionMs, state.durationMs, MusicAccent, player::seekTo, Modifier.fillMaxWidth())
						Spacer(Modifier.height(10.dp))
						Row(verticalAlignment = Alignment.CenterVertically) {
							ListenButton(KIcons.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle", player::toggleShuffle, active = state.shuffle, accent = MusicAccent, size = 44.dp)
							ListenButton(KIcons.Previous, "Previous", player::previous)
							ListenButton(
								if (state.isPlaying) KIcons.Pause else KIcons.Play,
								if (state.isPlaying) "Pause" else "Play",
								player::togglePlayPause,
								Modifier.focusRequester(playFocus),
								size = 68.dp,
								primary = true,
								accent = MusicAccent,
							)
							ListenButton(KIcons.NextEpisode, "Next", player::next)
							ListenButton(
								if (state.repeat == Player.REPEAT_MODE_ONE) KIcons.RepeatOne else KIcons.Repeat,
								when (state.repeat) {
									Player.REPEAT_MODE_ALL -> "Repeat all"
									Player.REPEAT_MODE_ONE -> "Repeat one"
									else -> "Repeat"
								},
								player::cycleRepeat,
								active = state.repeat != Player.REPEAT_MODE_OFF,
								accent = MusicAccent,
								size = 44.dp,
							)
						}
						state.error?.let { KText(it, style = KhyloType.Meta, color = Color(0xFFEF8F8F)) }
					}
				}
			}
		}
	}
}

/** A song's album cover (songs carry their album's image). */
private fun albumCover(repository: KhyloRepository, track: BaseItemDto?): String? {
	track ?: return null
	return repository.imageUrl(track, ImageType.PRIMARY, 900)
		?: track.albumId?.let { id -> track.albumPrimaryImageTag?.let { tag -> repository.imageUrlFor(id, tag, 900) } }
}

/** Three little bars dancing beside "Now playing"; still when paused. */
@Composable
private fun PlayingBars(playing: Boolean) {
	val transition = rememberInfiniteTransition(label = "bars")
	Row(Modifier.height(14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
		listOf(520, 380, 640).forEachIndexed { index, period ->
			val level by transition.animateFloat(
				0.25f,
				1f,
				infiniteRepeatable(tween(period, delayMillis = index * 90), RepeatMode.Reverse),
				label = "bar$index",
			)
			Box(
				Modifier
					.width(3.dp)
					.fillMaxHeight(if (playing) level else 0.3f)
					.clip(RoundedCornerShape(1.dp))
					.background(MusicAccent)
			)
		}
	}
}

/** "Up next" in the corner: the next song with its cover; OK skips to it. */
@Composable
private fun UpNext(next: BaseItemDto, cover: String?, modifier: Modifier, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		modifier
			.scale(if (focused) 1.04f else 1f)
			.clip(RoundedCornerShape(12.dp))
			.background(if (focused) Color.White else Color(0x66000000))
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A1A1C))) {
			if (cover != null) KImage(cover, Modifier.fillMaxSize())
		}
		Spacer(Modifier.width(12.dp))
		Column(Modifier.width(220.dp)) {
			KText("UP NEXT", style = KhyloType.Eyebrow, color = if (focused) Color(0xFF555555) else Color(0x99FFFFFF))
			KText(next.name.orEmpty(), style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), color = if (focused) Color(0xFF0B0B0C) else Color.White, maxLines = 1)
			(next.albumArtist ?: next.artists?.firstOrNull())?.let {
				KText(it, style = KhyloType.Caption, color = if (focused) Color(0xFF555555) else Color(0x99FFFFFF), maxLines = 1)
			}
		}
	}
}
