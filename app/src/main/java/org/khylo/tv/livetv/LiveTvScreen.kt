package org.khylo.tv.livetv

import org.khylo.tv.design.KIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.khylo.tv.core.R
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.koin.compose.koinInject
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val PAGE_SIZE = 60
private const val WINDOW_MINUTES = 180L
private val CHANNEL_COLUMN = 250.dp
private val ROW_HEIGHT = 76.dp
private val clockFormat = DateTimeFormatter.ofPattern("h:mm a")

private fun LocalDateTime.floorToHalfHour(): LocalDateTime = withMinute(if (minute < 30) 0 else 30).withSecond(0).withNano(0)

// The SDK already turns server times into the TV's local time
fun BaseItemDto.startLocal(): LocalDateTime? = startDate
fun BaseItemDto.endLocal(): LocalDateTime? = endDate

fun BaseItemDto.isAiring(now: LocalDateTime): Boolean {
	val start = startLocal() ?: return false
	val end = endLocal() ?: return false
	return !now.isBefore(start) && now.isBefore(end)
}

fun formatClock(time: LocalDateTime?): String = time?.format(clockFormat).orEmpty()

@Composable
fun LiveTvScreen() {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()

	var now by remember { mutableStateOf(LocalDateTime.now()) }
	LaunchedEffect(Unit) {
		while (true) {
			delay(30_000)
			now = LocalDateTime.now()
		}
	}
	var windowOffsetSlots by remember { mutableIntStateOf(0) }
	val windowStart = now.floorToHalfHour().plusMinutes(30L * windowOffsetSlots)
	val windowEnd = windowStart.plusMinutes(WINDOW_MINUTES)

	var limit by remember { mutableIntStateOf(PAGE_SIZE) }
	var favoritesOnly by remember { mutableStateOf(false) }
	val page by produceState<org.khylo.tv.data.ChannelPage?>(null, limit, favoritesOnly) {
		value = runCatching { repository.channels(limit, favoritesOnly) }.getOrNull() ?: value
	}
	val channels = page?.channels.orEmpty()
	val channelKey = channels.joinToString(",") { it.id.toString() }

	// Bumped after a recording is set up or cancelled, so the marks in the guide are fresh
	var recordingsTick by remember { mutableIntStateOf(0) }
	var options by remember { mutableStateOf<Pair<BaseItemDto, BaseItemDto>?>(null) }
	val programs by produceState(emptyMap<java.util.UUID, List<BaseItemDto>>(), channelKey, windowStart, recordingsTick) {
		val list = runCatching {
			repository.programs(channels.map { it.id }, windowStart, windowEnd)
		}.getOrDefault(emptyList())
		value = list.groupBy { it.channelId ?: java.util.UUID(0, 0) }
	}

	var focusedChannel by remember { mutableStateOf<BaseItemDto?>(null) }
	var focusedProgram by remember { mutableStateOf<BaseItemDto?>(null) }
	val previewChannel = focusedChannel ?: channels.firstOrNull()
	val previewProgram = focusedProgram ?: previewChannel?.let { ch -> programs[ch.id]?.firstOrNull { it.isAiring(now) } ?: ch.currentProgram }

	val listState = rememberLazyListState()
	LaunchedEffect(listState, channels.size) {
		// Load the next page when nearing the end of the list
		androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
			.collect { last -> if (page != null && last >= channels.size - 8 && channels.size < (page?.total ?: 0)) limit = channels.size + PAGE_SIZE }
	}

	if (page == null) {
		Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
		return
	}
	if (channels.isEmpty() && !favoritesOnly) {
		NotSetUp()
		return
	}

	val scope = rememberCoroutineScope()
	var message by remember { mutableStateOf<String?>(null) }
	LaunchedEffect(message) {
		if (message != null) {
			delay(3_500)
			message = null
		}
	}
	Box(Modifier.fillMaxSize()) {
	Column(Modifier.fillMaxSize()) {
		Preview(previewChannel, previewProgram, now, repository, onWatch = { previewChannel?.let { navigator.go(Route.Player(it)) } })

		// Toolbar
		Row(
			Modifier.fillMaxWidth().padding(horizontal = KhyloSpacing.Gutter, vertical = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			KText("Live TV", style = KhyloType.Title)
			KText("${page?.total ?: 0} channels  ·  hold OK on a show to record it", style = KhyloType.Caption, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
			Spacer(Modifier.weight(1f))
			KButton(if (favoritesOnly) "All channels" else "Favorites", onClick = { favoritesOnly = !favoritesOnly; limit = PAGE_SIZE }, icon = KIcons.Favorite)
			KButton("Earlier", onClick = { if (windowOffsetSlots > 0) windowOffsetSlots-- }, icon = KIcons.Earlier, enabled = windowOffsetSlots > 0)
			KButton("Later", onClick = { windowOffsetSlots++ }, icon = KIcons.Later)
			KButton("Multi View", onClick = { navigator.go(Route.MultiView(listOfNotNull(previewChannel))) }, icon = KIcons.Grid, style = KButtonStyle.Primary)
		}

		// Guide
		BoxWithConstraints(Modifier.fillMaxSize().padding(start = KhyloSpacing.Gutter, end = 24.dp)) {
			val timelineWidth = maxWidth - CHANNEL_COLUMN
			val dpPerMinute = timelineWidth / WINDOW_MINUTES.toFloat()

			Column(Modifier.fillMaxSize()) {
				TimeHeader(windowStart, timelineWidth, dpPerMinute)
				Box(Modifier.fillMaxSize()) {
					LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
						itemsIndexed(channels, key = { _, ch -> ch.id }) { _, channel ->
							GuideRow(
								channel = channel,
								programs = programs[channel.id].orEmpty(),
								now = now,
								windowStart = windowStart,
								windowEnd = windowEnd,
								dpPerMinute = dpPerMinute,
								repository = repository,
								onFocusChannel = { focusedChannel = channel; focusedProgram = null },
								onFocusProgram = { focusedChannel = channel; focusedProgram = it },
								onWatch = { navigator.go(Route.Player(channel)) },
								onOptions = { program -> options = channel to program },
							)
						}
					}
					// The current time, across every row
					val minutesIn = Duration.between(windowStart, now).toMinutes()
					if (minutesIn in 0..WINDOW_MINUTES) {
						Box(
							Modifier
								.offset(x = CHANNEL_COLUMN + dpPerMinute * minutesIn.toFloat())
								.width(2.dp)
								.fillMaxHeight()
								.background(KhyloColors.Accent)
						)
					}
				}
			}
		}
	}
	options?.let { (channel, program) ->
		RecordOptions(
			channel = channel,
			program = program,
			now = now,
			onDismiss = { options = null },
			onWatch = { options = null; navigator.go(Route.Player(channel)) },
			onRecord = { cancel ->
				options = null
				scope.launch {
					val timerId = program.timerId
					runCatching {
						if (cancel && timerId != null) repository.cancelRecording(timerId) else repository.recordProgram(program.id.toString())
					}.onSuccess { message = if (cancel) "Recording cancelled" else "Recording ${program.name.orEmpty()}" }
						.onFailure { message = "Couldn't change the recording" }
					recordingsTick++
				}
			},
			onRecordSeries = { cancel ->
				options = null
				scope.launch {
					val seriesId = program.seriesTimerId
					runCatching {
						if (cancel && seriesId != null) repository.cancelSeriesRecording(seriesId) else repository.recordSeries(program.id.toString())
					}.onSuccess { message = if (cancel) "Series recording cancelled" else "Every ${program.name.orEmpty()} will be recorded" }
						.onFailure { message = "Couldn't change the series recording" }
					recordingsTick++
				}
			},
		)
	}
	message?.let {
		KText(
			it,
			style = KhyloType.Label,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = 40.dp)
				.clip(RoundedCornerShape(24.dp))
				.background(Color(0xE6000000))
				.padding(horizontal = 24.dp, vertical = 12.dp),
		)
	}
	}
}

/** What can be done with one show in the guide: watch it, record it, or record every airing. */
@Composable
private fun RecordOptions(
	channel: BaseItemDto,
	program: BaseItemDto,
	now: LocalDateTime,
	onDismiss: () -> Unit,
	onWatch: () -> Unit,
	onRecord: (cancel: Boolean) -> Unit,
	onRecordSeries: (cancel: Boolean) -> Unit,
) {
	BackHandler(onBack = onDismiss)
	val first = remember { FocusRequester() }
	LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
	val airing = program.isAiring(now)
	val recording = program.timerId != null
	val seriesRecording = program.seriesTimerId != null
	Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
		Column(
			Modifier
				.width(520.dp)
				.clip(RoundedCornerShape(16.dp))
				.background(KhyloColors.Surface)
				.border(1.dp, KhyloColors.Border, RoundedCornerShape(16.dp))
				.padding(28.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			KText(program.name.orEmpty(), style = KhyloType.Title, maxLines = 2)
			KText(
				listOfNotNull(channel.name, "${formatClock(program.startLocal())} – ${formatClock(program.endLocal())}".takeIf { program.startDate != null }).joinToString("  ·  "),
				style = KhyloType.Label,
				color = KhyloColors.TextSecondary,
				maxLines = 1,
			)
			Spacer(Modifier.height(6.dp))
			if (airing) KButton("Watch now", onClick = onWatch, icon = KIcons.Play, style = KButtonStyle.Primary, modifier = Modifier.fillMaxWidth().focusRequester(first))
			KButton(
				if (recording) "Cancel this recording" else "Record this",
				onClick = { onRecord(recording) },
				icon = KIcons.Record,
				modifier = if (airing) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().focusRequester(first),
			)
			if (program.isSeries == true || seriesRecording) {
				KButton(
					if (seriesRecording) "Stop recording this series" else "Record every episode",
					onClick = { onRecordSeries(seriesRecording) },
					icon = KIcons.Record,
					modifier = Modifier.fillMaxWidth(),
				)
			}
			KButton("Close", onClick = onDismiss, style = KButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
		}
	}
}

@Composable
private fun Preview(channel: BaseItemDto?, program: BaseItemDto?, now: LocalDateTime, repository: KhyloRepository, onWatch: () -> Unit) {
	Row(
		Modifier.fillMaxWidth().height(230.dp).padding(start = KhyloSpacing.Gutter, top = 32.dp, end = KhyloSpacing.Gutter),
		horizontalArrangement = Arrangement.spacedBy(28.dp),
	) {
		Column(Modifier.weight(1f)) {
			val airing = program?.isAiring(now) == true
			KText(if (airing) "ON NOW · ${channel?.name.orEmpty()}" else (channel?.name ?: "").uppercase(), style = KhyloType.Eyebrow, maxLines = 1)
			Spacer(Modifier.height(10.dp))
			KText(program?.name ?: channel?.name.orEmpty(), style = KhyloType.Title, maxLines = 1)
			Spacer(Modifier.height(6.dp))
			val episode = listOfNotNull(program?.episodeTitle, "${formatClock(program?.startLocal())} – ${formatClock(program?.endLocal())}".takeIf { program?.startDate != null }).joinToString("  ·  ")
			if (episode.isNotEmpty()) KText(episode, style = KhyloType.Label, color = KhyloColors.TextSecondary, maxLines = 1)
			program?.overview?.let {
				Spacer(Modifier.height(10.dp))
				KText(it, style = KhyloType.Body, maxLines = 2)
			}
			if (airing) {
				val start = program?.startLocal()
				val end = program?.endLocal()
				if (start != null && end != null) {
					val total = Duration.between(start, end).toMinutes().coerceAtLeast(1)
					val done = Duration.between(start, now).toMinutes().coerceIn(0, total)
					Spacer(Modifier.height(14.dp))
					Box(Modifier.width(360.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
						Box(Modifier.fillMaxWidth(done.toFloat() / total).height(4.dp).background(KhyloColors.Accent))
					}
				}
			}
		}
		Box(
			Modifier.width(300.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(KhyloColors.Surface).border(1.dp, KhyloColors.Border, RoundedCornerShape(12.dp)),
			contentAlignment = Alignment.Center,
		) {
			val art = repository.imageUrl(program, maxWidth = 600) ?: repository.imageUrl(channel, maxWidth = 400)
			if (art != null) KImage(art, Modifier.fillMaxSize().padding(if (program?.imageTags.isNullOrEmpty()) 36.dp else 0.dp), if (program?.imageTags.isNullOrEmpty()) ContentScale.Fit else ContentScale.Crop)
		}
	}
}

@Composable
private fun TimeHeader(windowStart: LocalDateTime, timelineWidth: Dp, dpPerMinute: Dp) {
	Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
		Spacer(Modifier.width(CHANNEL_COLUMN))
		Box(Modifier.width(timelineWidth)) {
			for (slot in 0 until (WINDOW_MINUTES / 30).toInt()) {
				KText(
					formatClock(windowStart.plusMinutes(30L * slot)),
					style = KhyloType.Caption,
					modifier = Modifier.offset(x = dpPerMinute * (30f * slot)).padding(start = 8.dp),
				)
			}
		}
	}
}

@Composable
private fun GuideRow(
	channel: BaseItemDto,
	programs: List<BaseItemDto>,
	now: LocalDateTime,
	windowStart: LocalDateTime,
	windowEnd: LocalDateTime,
	dpPerMinute: Dp,
	repository: KhyloRepository,
	onFocusChannel: () -> Unit,
	onFocusProgram: (BaseItemDto) -> Unit,
	onWatch: () -> Unit,
	onOptions: (BaseItemDto) -> Unit,
) {
	Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 4.dp)) {
		// Channel: number, logo, name
		val (source, focused) = rememberFocusState()
		if (focused) LaunchedEffect(Unit) { onFocusChannel() }
		Row(
			Modifier
				.width(CHANNEL_COLUMN - 8.dp)
				.fillMaxHeight()
				.clip(RoundedCornerShape(10.dp))
				.background(if (focused) KhyloColors.Accent else KhyloColors.Surface)
				.clickable(interactionSource = source, indication = null, onClick = onWatch)
				.padding(horizontal = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			KText(channel.channelNumber.orEmpty(), style = KhyloType.Caption, color = if (focused) Color.White else KhyloColors.TextMuted, modifier = Modifier.width(44.dp), maxLines = 1)
			Box(Modifier.size(width = 52.dp, height = 34.dp), contentAlignment = Alignment.Center) {
				repository.imageUrl(channel, maxWidth = 160)?.let { KImage(it, Modifier.fillMaxSize(), ContentScale.Fit) }
			}
			KText(channel.name.orEmpty(), style = KhyloType.Label, color = if (focused) Color.White else KhyloColors.Text, maxLines = 1)
		}
		Spacer(Modifier.width(8.dp))

		// Programs in the window
		Box(Modifier.fillMaxHeight().weight(1f)) {
			if (programs.isEmpty()) {
				ProgramCell(
					title = channel.currentProgram?.name ?: "No listings",
					time = null,
					width = dpPerMinute * WINDOW_MINUTES.toFloat(),
					airing = channel.currentProgram != null,
					offset = 0.dp,
					onFocus = onFocusChannel,
					onClick = onWatch,
				)
			}
			programs.forEach { program ->
				val start = program.startLocal() ?: return@forEach
				val end = program.endLocal() ?: return@forEach
				val visibleStart = if (start.isBefore(windowStart)) windowStart else start
				val visibleEnd = if (end.isAfter(windowEnd)) windowEnd else end
				val minutes = Duration.between(visibleStart, visibleEnd).toMinutes()
				if (minutes <= 0) return@forEach
				val airing = program.isAiring(now)
				ProgramCell(
					title = program.name.orEmpty(),
					recording = program.timerId != null || program.seriesTimerId != null,
					onLongClick = { onOptions(program) },
					time = "${formatClock(start)} – ${formatClock(end)}",
					width = dpPerMinute * minutes.toFloat(),
					airing = airing,
					offset = dpPerMinute * Duration.between(windowStart, visibleStart).toMinutes().toFloat(),
					onFocus = { onFocusProgram(program) },
					// What is on now plays; anything later opens its record options
					onClick = if (airing) onWatch else ({ onOptions(program) }),
				)
			}
		}
	}
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ProgramCell(
	title: String,
	time: String?,
	width: Dp,
	airing: Boolean,
	offset: Dp,
	onFocus: () -> Unit,
	onClick: () -> Unit,
	recording: Boolean = false,
	onLongClick: (() -> Unit)? = null,
) {
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
					focused -> KhyloColors.Accent
					airing -> KhyloColors.SurfaceRaised
					else -> KhyloColors.Surface
				}
			)
			.border(1.dp, if (airing && !focused) KhyloColors.BorderStrong else Color.Transparent, RoundedCornerShape(8.dp))
			.combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalArrangement = Arrangement.Center,
	) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			if (recording) Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(KhyloColors.Live))
			KText(title, style = KhyloType.Label, color = if (focused) Color.White else KhyloColors.Text, maxLines = 1)
		}
		if (time != null && width > 120.dp) KText(time, style = KhyloType.Caption, color = if (focused) Color(0xDDFFFFFF) else KhyloColors.TextMuted, maxLines = 1)
	}
}

@Composable
private fun NotSetUp() {
	Column(Modifier.fillMaxSize().padding(KhyloSpacing.Gutter), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
		KText("Live TV isn't set up yet", style = KhyloType.Title)
		Spacer(Modifier.height(10.dp))
		KText("Add an IPTV service or HDHomeRun tuner in the Khylo admin dashboard to watch live TV here.", style = KhyloType.Body)
	}
}
