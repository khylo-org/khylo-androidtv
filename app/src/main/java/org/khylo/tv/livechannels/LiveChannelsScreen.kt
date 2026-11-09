package org.khylo.tv.livechannels

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.design.rememberedFocus
import org.koin.compose.koinInject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

private val clock = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
private const val GUIDE_HOURS = 6
private const val VISIBLE_MINUTES = 150f
private val CHANNEL_COLUMN = 230.dp
private val ROW_HEIGHT = 64.dp

private fun Instant.floorToHalfHour(): Instant {
	val local = atZone(ZoneId.systemDefault())
	return local.withMinute(if (local.minute < 30) 0 else 30).truncatedTo(ChronoUnit.MINUTES).toInstant()
}

/**
 * Khylo's always-on channels as a TV guide: the highlighted show up top, then every channel
 * against a timeline. The timeline scrolls sideways (all rows together) to see what's later.
 * OK on any channel tunes in mid-program, like real TV.
 */
@Composable
fun LiveChannelsScreen() {
	val channelsApi = koinInject<LiveChannelsApi>()
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	val scope = rememberCoroutineScope()
	var tuning by remember { mutableStateOf<String?>(null) }
	var error by remember { mutableStateOf<String?>(null) }

	var now by remember { mutableStateOf(Instant.now()) }
	LaunchedEffect(Unit) {
		while (true) {
			delay(30_000)
			now = Instant.now()
		}
	}
	val channels by produceState(GuideCache.channels) {
		// A failed load keeps the guide that was already shown rather than emptying it
		value = runCatching { channelsApi.guide(GUIDE_HOURS + 1) }.getOrNull()?.also { GuideCache.channels = it } ?: GuideCache.channels ?: emptyList()
	}

	var focused by remember { mutableStateOf<Pair<LiveChannel, LiveChannelProgram?>?>(null) }

	fun tuneIn(channel: LiveChannel) {
		if (tuning != null) return
		tuning = channel.id
		error = null
		scope.launch {
			runCatching {
				val tune = channelsApi.tune(channel.id)
				val current = requireNotNull(tune.current)
				val item = repository.item(current.itemId.toItemId())
				navigator.go(Route.Player(item, tune.startPositionTicks / 10_000, liveChannelId = channel.id))
			}.onFailure { error = "${channel.name} couldn't start. Try again." }
			tuning = null
		}
	}

	val list = channels
	when {
		list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
		list.isEmpty() -> Box(Modifier.fillMaxSize().padding(KhyloSpacing.Gutter), contentAlignment = Alignment.Center) {
			KText("No Khylo TV channels yet. They appear once your libraries have enough to program them.", style = KhyloType.Body)
		}
		else -> {
			val highlighted = focused ?: list.first().let { it to it.programs.airingAt(now) }
			Column(Modifier.fillMaxSize()) {
				Preview(highlighted.first, highlighted.second, now, repository, error)
				Guide(
					channels = list,
					now = now,
					logoUrl = channelsApi::logoUrl,
					tuning = tuning,
					onFocus = { channel, program -> focused = channel to program },
					onTune = ::tuneIn,
				)
			}
		}
	}
}

/** The guide from last time, shown straight away (back from watching) while it refreshes. */
private object GuideCache {
	var channels: List<LiveChannel>? = null
}

private fun List<LiveChannelProgram>.airingAt(now: Instant) = firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

/** The highlighted show: its artwork fills the right side, details on the left. */
@Composable
private fun Preview(channel: LiveChannel, program: LiveChannelProgram?, now: Instant, repository: KhyloRepository, error: String?) {
	// Load the full item briefly after focus settles, for its artwork
	val item by produceState<BaseItemDto?>(null, program?.itemId) {
		value = null
		val id = program?.itemId ?: return@produceState
		delay(250)
		value = runCatching { repository.item(id.toItemId()) }.getOrNull()
	}

	// Full-width artwork under the top bar, fading into the guide, like Movies and TV
	Box(Modifier.fillMaxWidth().height(KhyloSpacing.NavHeight + 230.dp)) {
		androidx.compose.animation.AnimatedContent(targetState = item?.id, transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() }, label = "channelArt") { _ ->
			Box(Modifier.fillMaxSize()) {
				repository.backdropUrl(item, 1920)?.let { KImage(it, Modifier.fillMaxSize(), ContentScale.Crop, Alignment.TopCenter) }
				Box(Modifier.fillMaxSize().background(org.khylo.tv.design.HeroScrim))
				Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to KhyloColors.Background)))
			}
		}
		Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.55f).padding(start = KhyloSpacing.Gutter, bottom = 14.dp)) {
			val airing = program != null && !now.isBefore(program.start) && now.isBefore(program.end)
			KText(
				(if (airing) "ON NOW · " else "") + "${channel.number}  ${channel.name}".uppercase(),
				style = KhyloType.Eyebrow,
				color = if (airing) KhyloColors.Accent else KhyloColors.TextSecondary,
				maxLines = 1,
			)
			Spacer(Modifier.height(10.dp))
			KText(program?.title ?: channel.name, style = KhyloType.Title, maxLines = 1)
			Spacer(Modifier.height(4.dp))
			val meta = listOfNotNull(program?.detail, program?.let { "${clock.format(it.start)} – ${clock.format(it.end)}" }).joinToString("  ·  ")
			if (meta.isNotEmpty()) KText(meta, style = KhyloType.Label, color = KhyloColors.TextSecondary, maxLines = 1)
			(program?.overview ?: channel.description.takeIf { it.isNotBlank() })?.let {
				Spacer(Modifier.height(8.dp))
				KText(it, style = KhyloType.Body, maxLines = 2)
			}
			if (airing && program != null) {
				val total = Duration.between(program.start, program.end).seconds.coerceAtLeast(1)
				val done = Duration.between(program.start, now).seconds.coerceIn(0, total)
				Spacer(Modifier.height(12.dp))
				Box(Modifier.width(320.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
					Box(Modifier.fillMaxWidth(done.toFloat() / total).height(3.dp).background(KhyloColors.Accent))
				}
			}
			error?.let {
				Spacer(Modifier.height(8.dp))
				KText(it, style = KhyloType.Meta, color = Color(0xFFEF8F8F))
			}
		}
	}
}

@Composable
private fun Guide(
	channels: List<LiveChannel>,
	now: Instant,
	logoUrl: (String) -> String,
	tuning: String?,
	onFocus: (LiveChannel, LiveChannelProgram?) -> Unit,
	onTune: (LiveChannel) -> Unit,
) {
	// The guide starts at this minute, so what's on now lines up down the left edge on every channel
	val windowStart = remember(now) { now.truncatedTo(ChronoUnit.MINUTES) }
	val windowEnd = windowStart.plus(Duration.ofHours(GUIDE_HOURS.toLong()))
	// Every row and the time header share one scroll, so the whole guide moves together
	val timeline = rememberScrollState()

	BoxWithConstraints(Modifier.fillMaxSize().padding(start = KhyloSpacing.Gutter)) {
		val dpPerMinute = (maxWidth - CHANNEL_COLUMN) / VISIBLE_MINUTES
		val totalWidth = dpPerMinute * (GUIDE_HOURS * 60f)

		Column(Modifier.fillMaxSize()) {
			TimeHeader(windowStart, dpPerMinute, totalWidth, timeline)
			Box(Modifier.fillMaxSize()) {
				LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
					items(channels, key = { it.id }) { channel ->
						GuideRow(channel, now, windowStart, windowEnd, dpPerMinute, totalWidth, timeline, logoUrl(channel.id), tuning == channel.id, onFocus, onTune)
					}
				}
			}
		}
	}
}

@Composable
private fun TimeHeader(windowStart: Instant, dpPerMinute: Dp, totalWidth: Dp, timeline: ScrollState) {
	Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
		KText("Today", style = KhyloType.Caption.copy(fontWeight = FontWeight.SemiBold), color = KhyloColors.TextSecondary, modifier = Modifier.width(CHANNEL_COLUMN))
		Box(Modifier.weight(1f).horizontalScroll(timeline, enabled = false)) {
			Box(Modifier.width(totalWidth)) {
				KText("Now", style = KhyloType.Caption.copy(fontWeight = FontWeight.SemiBold), color = KhyloColors.Accent, modifier = Modifier.padding(start = 10.dp))
				val firstHalfHour = windowStart.floorToHalfHour().plus(Duration.ofMinutes(30))
				for (slot in 0 until GUIDE_HOURS * 2) {
					val time = firstHalfHour.plus(Duration.ofMinutes(30L * slot))
					val minutes = Duration.between(windowStart, time).toMinutes()
					// Skip a label that would crowd "Now"
					if (minutes < 12) continue
					KText(
						clock.format(time),
						style = KhyloType.Caption,
						modifier = Modifier.offset(x = dpPerMinute * minutes.toFloat()).padding(start = 10.dp),
					)
				}
			}
		}
	}
}

@Composable
private fun GuideRow(
	channel: LiveChannel,
	now: Instant,
	windowStart: Instant,
	windowEnd: Instant,
	dpPerMinute: Dp,
	totalWidth: Dp,
	timeline: ScrollState,
	logoUrl: String,
	busy: Boolean,
	onFocus: (LiveChannel, LiveChannelProgram?) -> Unit,
	onTune: (LiveChannel) -> Unit,
) {
	Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 3.dp)) {
		// Channel: number, logo, name
		val (source, focused) = rememberFocusState()
		if (focused) LaunchedEffect(Unit) { onFocus(channel, channel.programs.airingAt(now)) }
		Row(
			Modifier
				.width(CHANNEL_COLUMN - 8.dp)
				.fillMaxHeight()
				.clip(RoundedCornerShape(8.dp))
				.background(if (focused) KhyloColors.Focus else Color.Transparent)
				// Back from watching lands on this same channel
				.rememberedFocus("channel|${channel.id}")
				.clickable(interactionSource = source, indication = null) { onTune(channel) }
				.padding(horizontal = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			val text = if (focused) KhyloColors.OnFocus else KhyloColors.Text
			KText(channel.number.toString(), style = KhyloType.Caption.copy(fontWeight = FontWeight.SemiBold), color = if (focused) KhyloColors.OnFocus else KhyloColors.TextMuted, modifier = Modifier.width(30.dp), maxLines = 1)
			Box(Modifier.size(width = 56.dp, height = 36.dp), contentAlignment = Alignment.Center) {
				// Network channels have a real logo; the rest get an icon for their kind
				var hasLogo by remember { mutableStateOf(false) }
				if (!hasLogo) {
					val icon = when (channel.category) {
						"Movies" -> KIcons.Movie
						"Collections" -> KIcons.Collection
						else -> KIcons.Tv
					}
					KIcon(icon, if (focused) KhyloColors.OnFocus else Color(0x80FFFFFF), Modifier.size(24.dp))
				}
				KImage(logoUrl, Modifier.fillMaxSize(), ContentScale.Fit, onLoaded = { hasLogo = true })
			}
			KText(if (busy) "Tuning…" else channel.name, style = KhyloType.Label, color = text, maxLines = 1)
		}
		Spacer(Modifier.width(8.dp))

		// Programs on the shared timeline
		Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(timeline)) {
			Box(Modifier.width(totalWidth).fillMaxHeight()) {
				channel.programs.forEach { program ->
					val start = if (program.start.isBefore(windowStart)) windowStart else program.start
					val end = if (program.end.isAfter(windowEnd)) windowEnd else program.end
					val minutes = Duration.between(start, end).toMinutes()
					if (minutes <= 0) return@forEach
					ProgramCell(
						program = program,
						airing = !now.isBefore(program.start) && now.isBefore(program.end),
						width = dpPerMinute * minutes.toFloat(),
						offset = dpPerMinute * Duration.between(windowStart, start).toMinutes().toFloat(),
						onFocus = { onFocus(channel, program) },
						onClick = { onTune(channel) },
						// The show on now keeps one key per channel, so it still matches after that show ends
						focusKey = if (!now.isBefore(program.start) && now.isBefore(program.end)) "channel|${channel.id}|now" else "channel|${channel.id}|${program.start}",
					)
				}
			}
		}
	}
}

@Composable
private fun ProgramCell(program: LiveChannelProgram, airing: Boolean, width: Dp, offset: Dp, onFocus: () -> Unit, onClick: () -> Unit, focusKey: String) {
	val (source, focused) = rememberFocusState()
	if (focused) LaunchedEffect(Unit) { onFocus() }
	Column(
		Modifier
			.offset(x = offset)
			.width(width - 4.dp)
			.fillMaxHeight()
			.clip(RoundedCornerShape(8.dp))
			.background(
				when {
					focused -> KhyloColors.Focus
					airing -> Color(0x1FFFFFFF)
					else -> Color(0x0DFFFFFF)
				}
			)
			.border(1.dp, if (focused || airing) Color.Transparent else Color(0x14FFFFFF), RoundedCornerShape(8.dp))
			.rememberedFocus(focusKey)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 12.dp),
		verticalArrangement = Arrangement.Center,
	) {
		KText(program.title, style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), color = if (focused) KhyloColors.OnFocus else KhyloColors.Text, maxLines = 1)
		if (width > 110.dp) {
			KText(
				program.detail ?: "${clock.format(program.start)} – ${clock.format(program.end)}",
				style = KhyloType.Caption,
				color = if (focused) Color(0xFF444444) else KhyloColors.TextMuted,
				maxLines = 1,
			)
		}
	}
}
