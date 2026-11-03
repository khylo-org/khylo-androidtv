package org.khylo.tv.livetv

import org.khylo.tv.design.KIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.khylo.tv.core.R
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.player.VideoSurface
import org.khylo.tv.player.rememberKhyloPlayer
import org.koin.compose.koinInject
import java.util.UUID

const val MULTI_VIEW_MAX = 4

private data class TileRect(val x: Dp, val y: Dp, val w: Dp, val h: Dp)

/**
 * Where each view goes. 1: full screen. 2: side by side at 16:9, centered. 3: one across the top,
 * two below. 4: one per corner. With a spotlight: one big view left, the rest stacked right.
 */
private fun layout(count: Int, spotlight: Int?, width: Dp, height: Dp, gap: Dp): List<TileRect> {
	if (spotlight != null && count > 1) {
		val bigW = (width - gap) * 0.75f
		val smallW = width - gap - bigW
		val smallH = (height - gap * 2) / 3
		var row = 0
		return List(count) { i ->
			if (i == spotlight) TileRect(0.dp, 0.dp, bigW, height)
			else TileRect(bigW + gap, (smallH + gap) * (row++).toFloat(), smallW, smallH)
		}
	}
	val halfW = (width - gap) / 2
	val halfH = (height - gap) / 2
	return when (count) {
		1 -> listOf(TileRect(0.dp, 0.dp, width, height))
		2 -> {
			val h = halfW * 9f / 16f
			val top = (height - h) / 2
			listOf(TileRect(0.dp, top, halfW, h), TileRect(halfW + gap, top, halfW, h))
		}
		3 -> listOf(
			TileRect(0.dp, 0.dp, width, halfH),
			TileRect(0.dp, halfH + gap, halfW, halfH),
			TileRect(halfW + gap, halfH + gap, halfW, halfH),
		)
		else -> listOf(
			TileRect(0.dp, 0.dp, halfW, halfH),
			TileRect(halfW + gap, 0.dp, halfW, halfH),
			TileRect(0.dp, halfH + gap, halfW, halfH),
			TileRect(halfW + gap, halfH + gap, halfW, halfH),
		)
	}
}

/** Watch up to four live channels at once. OK on a view opens its options. */
@Composable
fun MultiViewScreen(initial: List<BaseItemDto>) {
	val navigator = navigator()
	val channels = remember { mutableStateListOf<BaseItemDto>().apply { addAll(initial.take(MULTI_VIEW_MAX)) } }
	var audioId by remember { mutableStateOf(initial.firstOrNull()?.id) }
	var spotlightId by remember { mutableStateOf<UUID?>(null) }
	var menuFor by remember { mutableStateOf<UUID?>(null) }
	// Picking a channel: null = closed, "" = add a new view, id = replace that view
	var chooserFor by remember { mutableStateOf<String?>(if (initial.isEmpty()) "" else null) }
	val firstTileFocus = remember { FocusRequester() }

	val audioChannel = channels.firstOrNull { it.id == audioId }?.id ?: channels.firstOrNull()?.id
	val spotlightIndex = channels.indexOfFirst { it.id == spotlightId }.takeIf { it >= 0 && channels.size > 1 }

	BackHandler {
		when {
			chooserFor != null && channels.isNotEmpty() -> chooserFor = null
			menuFor != null -> menuFor = null
			else -> navigator.back()
		}
	}

	Box(Modifier.fillMaxSize().background(Color(0xFF050404))) {
		Column(Modifier.fillMaxSize()) {
			// Top bar
			Row(
				Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Column(Modifier.weight(1f)) {
					KText("Multi View", style = KhyloType.Heading)
					KText("${channels.size} of $MULTI_VIEW_MAX · press OK on a view for options", style = KhyloType.Caption)
				}
				if (channels.size < MULTI_VIEW_MAX) KButton("Add channel", onClick = { chooserFor = "" }, icon = KIcons.Add)
				KButton("Close", onClick = { navigator.back() })
			}

			BoxWithConstraints(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
				val rects = layout(channels.size.coerceAtLeast(1), spotlightIndex, maxWidth, maxHeight, 8.dp)
				channels.forEachIndexed { index, channel ->
					key(channel.id) {
						val rect = rects[index]
						MultiViewTile(
							channel = channel,
							hasAudio = channel.id == audioChannel,
							modifier = Modifier
								.offset(rect.x, rect.y)
								.size(rect.w, rect.h)
								.then(if (index == 0) Modifier.focusRequester(firstTileFocus) else Modifier),
							onClick = {
								audioId = channel.id
								menuFor = channel.id
							},
						)
					}
				}
			}
		}

		// Options for one view
		menuFor?.let { id ->
			val channel = channels.firstOrNull { it.id == id }
			if (channel == null) menuFor = null else TileMenu(
				channel = channel,
				isSpotlight = spotlightId == id,
				canSpotlight = channels.size > 1,
				onListen = { audioId = id; menuFor = null },
				onChange = { chooserFor = id.toString(); menuFor = null },
				onSpotlight = { spotlightId = if (spotlightId == id) null else id; menuFor = null },
				onRemove = {
					channels.removeAll { it.id == id }
					menuFor = null
					if (channels.isEmpty()) chooserFor = ""
				},
				onDismiss = { menuFor = null },
			)
		}

		chooserFor?.let { target ->
			ChannelChooser(
				title = if (target.isEmpty()) "Add a channel" else "Change channel",
				exclude = channels.map { it.id }.toSet(),
				onChoose = { picked ->
					if (target.isEmpty()) {
						if (channels.size < MULTI_VIEW_MAX) channels.add(picked)
						if (channels.size == 1) audioId = picked.id
					} else {
						val index = channels.indexOfFirst { it.id.toString() == target }
						if (index >= 0) {
							val old = channels[index].id
							channels[index] = picked
							if (audioId == old) audioId = picked.id
							if (spotlightId == old) spotlightId = picked.id
						}
					}
					chooserFor = null
				},
				onDismiss = { if (channels.isEmpty()) navigator.back() else chooserFor = null },
			)
		}
	}

	LaunchedEffect(chooserFor, menuFor, channels.size) {
		if (chooserFor == null && menuFor == null && channels.isNotEmpty()) runCatching { firstTileFocus.requestFocus() }
	}
}

@Composable
private fun MultiViewTile(channel: BaseItemDto, hasAudio: Boolean, modifier: Modifier, onClick: () -> Unit) {
	val player = rememberKhyloPlayer(channel.id)
	val state by player.state.collectAsState()
	LaunchedEffect(player) { player.play(channel) }
	LaunchedEffect(hasAudio) { player.volume = if (hasAudio) 1f else 0f }

	val (source, focused) = rememberFocusState()
	Box(
		modifier
			.clip(RoundedCornerShape(8.dp))
			.background(Color.Black)
			.border(
				width = if (focused || hasAudio) 3.dp else 1.dp,
				color = when {
					focused -> Color.White
					hasAudio -> KhyloColors.Accent
					else -> KhyloColors.Border
				},
				shape = RoundedCornerShape(8.dp),
			)
			.clickable(interactionSource = source, indication = null, onClick = onClick),
	) {
		VideoSurface(player, Modifier.fillMaxSize())
		if (state.isLoading && state.error == null) {
			Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
				KSpinner()
				Spacer(Modifier.height(10.dp))
				KText("Tuning in…", style = KhyloType.Caption)
			}
		}
		state.error?.let {
			Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
				KText("This channel couldn't start", style = KhyloType.Label)
				Spacer(Modifier.height(6.dp))
				KText("Your TV service may be out of connections. Remove another view and try again.", style = KhyloType.Caption)
			}
		}
		// Label, bottom left
		Row(
			Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
				.padding(horizontal = 14.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			org.khylo.tv.design.KIcon(
				if (hasAudio) KIcons.Sound else KIcons.Muted,
				if (hasAudio) KhyloColors.Accent else KhyloColors.TextMuted,
				Modifier.size(18.dp),
				if (hasAudio) "Playing sound" else "Muted",
			)
			KText(listOfNotNull(channel.channelNumber, channel.name).joinToString(" · "), style = KhyloType.Label, maxLines = 1)
		}
	}
}

@Composable
private fun TileMenu(
	channel: BaseItemDto,
	isSpotlight: Boolean,
	canSpotlight: Boolean,
	onListen: () -> Unit,
	onChange: () -> Unit,
	onSpotlight: () -> Unit,
	onRemove: () -> Unit,
	onDismiss: () -> Unit,
) {
	val first = remember { FocusRequester() }
	LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
	Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
		Column(
			Modifier
				.padding(bottom = 48.dp)
				.clip(RoundedCornerShape(16.dp))
				.background(KhyloColors.Surface)
				.border(1.dp, KhyloColors.Border, RoundedCornerShape(16.dp))
				.padding(24.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			KText(channel.name.orEmpty(), style = KhyloType.Heading)
			Spacer(Modifier.height(18.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
				KButton("Hear this", onClick = onListen, icon = KIcons.Sound, style = KButtonStyle.Primary, modifier = Modifier.focusRequester(first))
				KButton("Change channel", onClick = onChange, icon = KIcons.Swap)
				if (canSpotlight) KButton(if (isSpotlight) "Equal size" else "Make bigger", onClick = onSpotlight, icon = KIcons.Bigger)
				KButton("Remove", onClick = onRemove, icon = KIcons.Remove)
			}
		}
	}
}

/** Pick a live channel: favorites first, then the rest. */
@Composable
fun ChannelChooser(title: String, exclude: Set<UUID>, onChoose: (BaseItemDto) -> Unit, onDismiss: () -> Unit) {
	val repository = koinInject<KhyloRepository>()
	val channels by produceState<List<BaseItemDto>?>(null) {
		value = runCatching { repository.channels(400).channels }.getOrDefault(emptyList())
	}
	val first = remember { FocusRequester() }
	BackHandler(onBack = onDismiss)

	Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
		Column(
			Modifier
				.width(640.dp)
				.fillMaxSize()
				.padding(vertical = 40.dp)
				.clip(RoundedCornerShape(16.dp))
				.background(KhyloColors.Surface)
				.border(1.dp, KhyloColors.Border, RoundedCornerShape(16.dp))
				.padding(24.dp),
		) {
			KText(title, style = KhyloType.Title)
			Spacer(Modifier.height(16.dp))
			val list = channels
			if (list == null) {
				Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
			} else {
				val available = list.filter { it.id !in exclude }
				LaunchedEffect(available.size) { if (available.isNotEmpty()) runCatching { first.requestFocus() } }
				LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					items(available, key = { it.id }) { channel ->
						ChooserRow(
							channel = channel,
							repository = repository,
							modifier = if (channel == available.first()) Modifier.focusRequester(first) else Modifier,
							onClick = { onChoose(channel) },
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ChooserRow(channel: BaseItemDto, repository: KhyloRepository, modifier: Modifier, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		modifier
			.fillMaxWidth()
			.height(64.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(if (focused) KhyloColors.Accent else KhyloColors.SurfaceRaised)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		KText(channel.channelNumber.orEmpty(), style = KhyloType.Caption, color = if (focused) Color.White else KhyloColors.TextMuted, modifier = Modifier.width(52.dp), maxLines = 1)
		Box(Modifier.size(width = 56.dp, height = 36.dp), contentAlignment = Alignment.Center) {
			repository.imageUrl(channel, maxWidth = 160)?.let { KImage(it, Modifier.fillMaxSize(), ContentScale.Fit) }
		}
		Column(Modifier.weight(1f)) {
			KText(channel.name.orEmpty(), style = KhyloType.Label, color = if (focused) Color.White else KhyloColors.Text, maxLines = 1)
			channel.currentProgram?.name?.let { KText(it, style = KhyloType.Caption, color = if (focused) Color(0xDDFFFFFF) else KhyloColors.TextMuted, maxLines = 1) }
		}
	}
}
