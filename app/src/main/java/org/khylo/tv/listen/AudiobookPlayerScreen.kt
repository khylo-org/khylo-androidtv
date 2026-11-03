package org.khylo.tv.listen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.ImageType
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.player.formatTime
import org.khylo.tv.player.rememberAudioPlayer
import org.koin.compose.koinInject

// A warmer room than music or video: amber light on a dark, paper-brown backdrop
private val Amber = Color(0xFFE3A857)
private val Room = Color(0xFF15100B)
private val Cream = Color(0xFFF4E8D2)

private val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val sleepOptions = listOf(0, 15, 30, 45, 60)

/** "Along Came a Spider - Part 10" is part of the book "Along Came a Spider". */
private fun bookTitle(part: BaseItemDto): String =
	part.album?.takeIf { it.isNotBlank() }?.replace(Regex("""^\d+\s*[-.]?\s+"""), "")
		?: part.name.orEmpty().replace(Regex("""\s*[-–:]?\s*(part|pt|chapter|disc|cd)\s*\d+\s*$""", RegexOption.IGNORE_CASE), "").ifBlank { part.name.orEmpty() }

/**
 * The audiobook player: the book's cover on a warm backdrop, where you are in the whole book (not
 * just this part), back 10 / forward 30, reading speed and a sleep timer. Picks up where you left off.
 */
@Composable
fun AudiobookPlayerScreen(start: BaseItemDto) {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val player = rememberAudioPlayer(LocalContext.current)
	val state by player.state.collectAsState()
	var parts by remember { mutableStateOf<List<BaseItemDto>?>(null) }
	var loadError by remember { mutableStateOf(false) }
	var showParts by remember { mutableStateOf(false) }
	var sleepMinutes by remember { mutableStateOf(0) }
	var sleepEndsAt by remember { mutableStateOf(0L) }
	val playFocus = remember { FocusRequester() }

	LaunchedEffect(start.id) {
		runCatching {
			val all = repository.audiobookParts(start)
			// Resume: the chosen part if it was started, otherwise the part you were last in
			val chosen = all.indexOfFirst { it.id == start.id }.coerceAtLeast(0)
			val resumeIndex = if ((all[chosen].userData?.playbackPositionTicks ?: 0) > 0) chosen
			else all.indices.filter { (all[it].userData?.playbackPositionTicks ?: 0) > 0 }
				.maxByOrNull { all[it].userData?.lastPlayedDate?.toString().orEmpty() } ?: chosen
			parts = all
			player.play(all, resumeIndex, (all[resumeIndex].userData?.playbackPositionTicks ?: 0) / 10_000, speech = true)
		}.onFailure { loadError = true }
	}
	LaunchedEffect(parts) {
		if (parts != null) {
			delay(100)
			runCatching { playFocus.requestFocus() }
		}
	}
	// Sleep timer: the voice fades out, then a Goodnight screen, then Khylo closes so the TV can sleep
	var goodnight by remember { mutableStateOf(false) }
	val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
	LaunchedEffect(sleepEndsAt) {
		if (sleepEndsAt == 0L) return@LaunchedEffect
		delay((sleepEndsAt - org.khylo.tv.player.SLEEP_FADE_MS - System.currentTimeMillis()).coerceAtLeast(0))
		org.khylo.tv.player.fadeOutAndPause(player.exoPlayer, (sleepEndsAt - System.currentTimeMillis()).coerceIn(1_000, org.khylo.tv.player.SLEEP_FADE_MS))
		sleepMinutes = 0
		sleepEndsAt = 0
		goodnight = true
	}
	if (goodnight) {
		org.khylo.tv.player.GoodnightScreen(
			onStay = { goodnight = false },
			onLeave = { activity?.finish() ?: navigator.back() },
		)
	}
	BackHandler { if (showParts) showParts = false else navigator.back() }

	val all = parts
	val current = all?.getOrNull(state.index)
	val cover = repository.imageUrl(current, ImageType.PRIMARY, 700)
		?: current?.parentId?.let { repository.imageUrlFor(it, null, 700) }
		?: repository.imageUrl(start, ImageType.PRIMARY, 700)

	Box(Modifier.fillMaxSize().background(Room)) {
		if (cover != null) KImage(cover, Modifier.fillMaxSize().blur(110.dp), ContentScale.Crop)
		Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xD915100B), 1f to Color(0xF215100B))))

		when {
			loadError -> KText("This audiobook couldn't be loaded.", style = KhyloType.Heading, modifier = Modifier.align(Alignment.Center))
			all == null || current == null -> KSpinner(Modifier.align(Alignment.Center), size = 56.dp)
			else -> Row(
				Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				// The book itself: a tall cover with a little spine shading
				Box(
					Modifier
						.fillMaxHeight(0.86f)
						.aspectRatio(2f / 3f)
						.shadow(36.dp, RoundedCornerShape(4.dp, 10.dp, 10.dp, 4.dp))
						.clip(RoundedCornerShape(4.dp, 10.dp, 10.dp, 4.dp))
						.background(Color(0xFF2A2018)),
					contentAlignment = Alignment.Center,
				) {
					if (cover != null) KImage(cover, Modifier.fillMaxSize()) else KIcon(KIcons.Audiobook, Amber.copy(alpha = 0.5f), Modifier.size(96.dp))
					Box(Modifier.fillMaxHeight().width(14.dp).align(Alignment.CenterStart).background(Brush.horizontalGradient(listOf(Color(0x66000000), Color.Transparent))))
				}
				Spacer(Modifier.width(72.dp))

				// Whole-book position: finished parts plus where you are in this one
				val durations = all.map { (it.runTimeTicks ?: 0) / 10_000 }
				val total = durations.sum().coerceAtLeast(1)
				val done = durations.take(state.index).sum() + state.positionMs
				val bookFraction = (done.toFloat() / total).coerceIn(0f, 1f)

				Column(Modifier.weight(1f)) {
					KText("AUDIOBOOK", style = KhyloType.Eyebrow, color = Amber)
					Spacer(Modifier.height(12.dp))
					KText(bookTitle(current), style = KhyloType.Display.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold), color = Cream, maxLines = 2)
					(current.albumArtist ?: current.artists?.firstOrNull())?.let {
						Spacer(Modifier.height(6.dp))
						KText("by $it", style = KhyloType.Heading.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal), color = Cream.copy(alpha = 0.8f), maxLines = 1)
					}
					Spacer(Modifier.height(28.dp))

					// The whole book
					Row(verticalAlignment = Alignment.CenterVertically) {
						KText("${(bookFraction * 100).toInt()}% of the book", style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), color = Cream)
						Spacer(Modifier.weight(1f))
						KText("${formatLong(total - done)} left", style = KhyloType.Label, color = Cream.copy(alpha = 0.7f))
					}
					Spacer(Modifier.height(8.dp))
					Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x26F4E8D2))) {
						Box(Modifier.fillMaxWidth(bookFraction).height(8.dp).background(Amber))
					}
					Spacer(Modifier.height(22.dp))

					// This part
					KText(
						if (all.size > 1) "Part ${state.index + 1} of ${all.size}" else "Now listening",
						style = KhyloType.Caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
						color = Cream.copy(alpha = 0.7f),
					)
					Spacer(Modifier.height(6.dp))
					ListenSeekBar(state.positionMs, state.durationMs, Amber, player::seekTo, Modifier.fillMaxWidth(), trackColor = Color(0x26F4E8D2))
					Spacer(Modifier.height(16.dp))

					Row(verticalAlignment = Alignment.CenterVertically) {
						ListenButton(KIcons.Previous, "Previous part", player::previous, size = 46.dp)
						ListenButton(KIcons.Back10, "Back 10s", { player.seekBy(-10_000) })
						ListenButton(
							if (state.isPlaying) KIcons.Pause else KIcons.Play,
							if (state.isPlaying) "Pause" else "Listen",
							player::togglePlayPause,
							Modifier.focusRequester(playFocus),
							size = 76.dp,
							primary = true,
							accent = Amber,
							discColor = Cream,
						)
						ListenButton(KIcons.Forward30, "Forward 30s", { player.seekBy(30_000) })
						ListenButton(KIcons.NextEpisode, "Next part", player::next, size = 46.dp)
					}
					Spacer(Modifier.height(18.dp))

					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						ListenPill(
							"${state.speed.toString().removeSuffix(".0")}×",
							KIcons.Speed,
							{ player.setSpeed(speeds[(speeds.indexOf(state.speed).coerceAtLeast(0) + 1) % speeds.size]) },
							accent = Amber,
							active = state.speed != 1f,
						)
						ListenPill(
							if (sleepMinutes == 0) "Sleep timer" else "Sleep in ${((sleepEndsAt - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)}m",
							KIcons.Sleep,
							{
								sleepMinutes = sleepOptions[(sleepOptions.indexOf(sleepMinutes) + 1) % sleepOptions.size]
								sleepEndsAt = if (sleepMinutes == 0) 0 else System.currentTimeMillis() + sleepMinutes * 60_000L
							},
							accent = Amber,
							active = sleepMinutes > 0,
						)
						if (all.size > 1) ListenPill("All parts", KIcons.Parts, { showParts = true }, accent = Amber)
					}
					state.error?.let {
						Spacer(Modifier.height(8.dp))
						KText(it, style = KhyloType.Meta, color = Color(0xFFEF8F8F))
					}
				}
			}
		}

		// The list of parts slides in from the right
		AnimatedVisibility(
			visible = showParts && all != null,
			enter = fadeIn() + slideInHorizontally { it / 3 },
			exit = fadeOut() + slideOutHorizontally { it / 3 },
			modifier = Modifier.align(Alignment.CenterEnd),
		) {
			val listFocus = remember { FocusRequester() }
			LaunchedEffect(Unit) {
				delay(80)
				runCatching { listFocus.requestFocus() }
			}
			Column(Modifier.fillMaxHeight().width(420.dp).background(Color(0xF2100C08)).padding(start = 28.dp, end = 28.dp, top = 40.dp)) {
				KText("Parts", style = KhyloType.Title, color = Cream)
				Spacer(Modifier.height(12.dp))
				LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
					itemsIndexed(all.orEmpty()) { index, part ->
						PartRow(
							index = index,
							part = part,
							playing = index == state.index,
							modifier = if (index == state.index) Modifier.focusRequester(listFocus) else Modifier,
							onClick = {
								player.playIndex(index)
								showParts = false
							},
						)
					}
				}
			}
		}
	}
}

@Composable
private fun PartRow(index: Int, part: BaseItemDto, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(if (focused) Cream else Color.Transparent)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 14.dp, vertical = 11.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		KText(
			"Part ${index + 1}",
			style = KhyloType.Label.copy(fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium),
			color = if (focused) Room else if (playing) Amber else Cream,
			modifier = Modifier.weight(1f),
		)
		part.runTimeTicks?.let { KText(formatTime(it / 10_000), style = KhyloType.Caption, color = if (focused) Room else Cream.copy(alpha = 0.6f)) }
	}
}
