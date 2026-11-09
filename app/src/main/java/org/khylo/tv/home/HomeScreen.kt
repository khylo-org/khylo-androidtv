package org.khylo.tv.home

import org.khylo.tv.design.KIcons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.khylo.tv.core.R
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.data.KhyloRow
import org.khylo.tv.design.BottomScrim
import org.khylo.tv.design.HeroScrim
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KCard
import org.khylo.tv.design.KCardShape
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSectionTitle
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.koin.compose.koinInject

/** Home's rows and header titles from last time, shown straight away (coming back to Home) while fresh ones load. */
object HomeRowsCache {
	var rows: List<KhyloRow>? = null
	var featured: List<BaseItemDto> = emptyList()
}

/**
 * Who is playing on the viewer's other devices, as Home last saw it. Home is built again every time you come back to it, so
 * without this the "Bring to this TV" buttons vanish until the next answer arrives, and any answer that is missing or fails
 * would wipe them. A device that stops being listed stays for [KeepForMs], so one missed answer does not hide it.
 */
object HandoffBannerCache {
	private const val KeepForMs = 20_000L
	private val seen = LinkedHashMap<String, Pair<org.khylo.tv.handoff.HandoffDevice, Long>>()

	/** Folds in the server's latest answer (null when it could not be reached) and returns who is playing elsewhere now. */
	@Synchronized
	fun update(answer: List<org.khylo.tv.handoff.HandoffDevice>?, now: Long = System.currentTimeMillis()): List<org.khylo.tv.handoff.HandoffDevice> {
		answer?.forEach { device ->
			// A device that answered but is not playing has stopped: take it off straight away
			if (device.IsPlaying) seen[device.SessionId] = device to now else seen.remove(device.SessionId)
		}
		seen.entries.removeAll { now - it.value.second > KeepForMs }
		return seen.values.map { it.first }
	}
}

/** Where text starts on full-bleed screens: clear of the rail, plus breathing room. */
val ContentStart = KhyloSpacing.Gutter

@Composable
fun HomeScreen() {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	var rowsVersion by remember { mutableStateOf(0) }
	val rows by produceState(HomeRowsCache.rows, rowsVersion) {
		// A failed load keeps what was already shown rather than emptying Home
		value = runCatching { repository.homeRows() }.getOrNull()?.also { HomeRowsCache.rows = it } ?: HomeRowsCache.rows ?: emptyList()
	}
	// The tile whose options are open, and whether it came from Up next
	var menuFor by remember { mutableStateOf<Pair<BaseItemDto, Boolean>?>(null) }
	val scope = androidx.compose.runtime.rememberCoroutineScope()
	val featured by produceState(HomeRowsCache.featured) { value = repository.featured().also { HomeRowsCache.featured = it } }
	var focused by remember { mutableStateOf<BaseItemDto?>(null) }
	val hero = focused ?: featured.firstOrNull()
	// The row you're on always sits right under the hero, whole, like Netflix
	val listState = androidx.compose.foundation.lazy.rememberLazyListState()
	var focusedRow by remember { mutableStateOf(-1) }
	// Handoff: something playing on another of the viewer's devices can be brought to this TV
	val apiClient = koinInject<org.khylo.sdk.api.client.ApiClient>()
	val httpClient = koinInject<okhttp3.OkHttpClient>()
	val handoffApi = androidx.compose.runtime.remember { org.khylo.tv.handoff.HandoffApi(apiClient, httpClient) }
	val elsewhere by produceState(HandoffBannerCache.update(null)) {
		while (true) {
			value = HandoffBannerCache.update(runCatching { handoffApi.devices() }.getOrNull())
			kotlinx.coroutines.delay(6_000)
		}
	}
	val extraItems = if (elsewhere.isNotEmpty()) 1 else 0
	LaunchedEffect(focusedRow) { if (focusedRow >= 0) listState.animateScrollToItem(focusedRow + extraItems) }

	Box(Modifier.fillMaxSize()) {
		// The highlighted title's artwork fills the screen behind everything
		AnimatedContent(targetState = hero?.id, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "heroArt") { _ ->
			Box(Modifier.fillMaxSize()) {
				KImage(repository.backdropUrl(hero), Modifier.fillMaxSize(), ContentScale.Crop)
				Box(Modifier.fillMaxSize().background(HeroScrim))
				Box(Modifier.fillMaxSize().background(BottomScrim))
			}
		}

		Column(Modifier.fillMaxSize()) {
			HeroInfo(hero, repository, isFeatured = focused == null)

			org.khylo.tv.design.SnapRows {
			LazyColumn(
				state = listState,
				contentPadding = PaddingValues(top = 4.dp, bottom = 160.dp),
				verticalArrangement = Arrangement.spacedBy(30.dp),
			) {
				if (elsewhere.isNotEmpty()) {
					item(key = "handoff") {
						Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
							elsewhere.forEach { device ->
								org.khylo.tv.design.KButton(
									text = "Bring to this TV: ${device.playing ?: "what's playing"} · from ${device.title}",
									icon = KIcons.Tv,
									onClick = { scope.launch { runCatching { handoffApi.bringHere(device.SessionId) } } },
								)
							}
						}
					}
				}
				when (val loaded = rows) {
					null -> item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { KSpinner() } }
					else -> itemsIndexed(loaded, key = { _, row -> row.title }) { index, row ->
						ItemRow(row, repository, onFocus = { focused = it; focusedRow = index }, onOpen = { openItem(navigator, it) }, onOptions = if (row.wide) ({ menuFor = it to (row.title == "Up next") }) else null)
					}
				}
			}
			}
		}

		menuFor?.let { (item, fromUpNext) ->
			ItemOptions(
				item = item,
				fromUpNext = fromUpNext,
				repository = repository,
				onDismiss = { menuFor = null },
				onChanged = {
					menuFor = null
					// The header goes back to the featured titles rather than showing what was removed
					focused = null
					rowsVersion++
				},
			)
		}
	}
}

fun openItem(navigator: org.khylo.tv.app.Navigator, item: BaseItemDto) {
	when (item.type) {
		BaseItemKind.TV_CHANNEL -> navigator.go(Route.Player(item))
		BaseItemKind.COLLECTION_FOLDER, BaseItemKind.USER_VIEW -> navigator.go(Route.Library(item))
		else -> navigator.go(Route.Details(item.id))
	}
}

@Composable
private fun HeroInfo(item: BaseItemDto?, repository: KhyloRepository, isFeatured: Boolean) {
	val navigator = navigator()
	Column(
		Modifier
			.fillMaxWidth(0.52f)
			.height(368.dp)
			.padding(start = ContentStart, top = KhyloSpacing.NavHeight, bottom = 20.dp),
		verticalArrangement = Arrangement.Bottom,
	) {
		if (item == null) return@Column
		val logo = repository.logoUrl(item)
		if (logo != null) {
			KImage(logo, Modifier.height(84.dp).width(380.dp), ContentScale.Fit, Alignment.CenterStart)
		} else {
			KText(item.seriesName ?: item.name.orEmpty(), style = KhyloType.Display, maxLines = 1)
		}
		Spacer(Modifier.height(12.dp))
		MetaRow(item)
		item.overview?.let {
			Spacer(Modifier.height(12.dp))
			KText(it, style = KhyloType.Body.copy(color = KhyloColors.Text.copy(alpha = 0.85f)), maxLines = 2)
		}
		val cast = item.people?.filter { it.type == org.khylo.sdk.model.api.PersonKind.ACTOR }?.take(4)?.mapNotNull { it.name }
		if (!cast.isNullOrEmpty()) {
			Spacer(Modifier.height(8.dp))
			KText(cast.joinToString(", "), style = KhyloType.Meta, maxLines = 1)
		}
		if (isFeatured) {
			Spacer(Modifier.height(16.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				val scope = androidx.compose.runtime.rememberCoroutineScope()
				KButton("Play", onClick = {
					// A show plays its next episode; anything else plays itself
					if (item.type == BaseItemKind.SERIES) {
						scope.launch { repository.seriesPlayTarget(item)?.let { navigator.go(Route.Player(it)) } ?: navigator.go(Route.Details(item.id)) }
					} else {
						navigator.go(org.khylo.tv.library.playRoute(item))
					}
				}, icon = KIcons.Play, style = KButtonStyle.Primary)
				KButton("More info", onClick = { navigator.go(Route.Details(item.id)) }, icon = KIcons.Info)
			}
		}
	}
}

/** Rating chip, then year · runtime · genres, like Plex and Netflix. */
@Composable
fun MetaRow(item: BaseItemDto) {
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
		item.officialRating?.let {
			KText(
				it,
				style = KhyloType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
				color = KhyloColors.Text,
				modifier = Modifier
					.border(1.dp, Color(0x80FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
					.padding(horizontal = 6.dp, vertical = 2.dp),
			)
		}
		val parts = listOfNotNull(
			item.productionYear?.toString(),
			item.runTimeTicks?.let { ticks ->
				val minutes = ticks / 600_000_000
				if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m".takeIf { minutes > 0 }
			}?.takeIf { item.type != BaseItemKind.SERIES },
			item.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotEmpty() },
		)
		parts.forEach { KText(it, style = KhyloType.Label, color = KhyloColors.Text.copy(alpha = 0.9f), maxLines = 1) }
	}
}

@Composable
fun ItemRow(row: KhyloRow, repository: KhyloRepository, onFocus: (BaseItemDto) -> Unit, onOpen: (BaseItemDto) -> Unit, onOptions: ((BaseItemDto) -> Unit)? = null) {
	Column {
		Row(Modifier.padding(start = ContentStart), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			KSectionTitle(row.title)
			if (onOptions != null) KText("Hold OK for options", style = KhyloType.Caption, modifier = Modifier.padding(bottom = 14.dp))
		}
		org.khylo.tv.design.SnapRow {
		LazyRow(
			contentPadding = PaddingValues(start = ContentStart, end = 48.dp, top = 10.dp, bottom = 10.dp),
			horizontalArrangement = Arrangement.spacedBy(18.dp),
		) {
			items(row.items, key = { it.id }) { item ->
				val progress = item.userData?.playedPercentage?.toFloat()?.div(100f)
				KCard(
					watched = item.userData?.played == true,
					imageUrl = if (row.wide) repository.thumbUrl(item) else repository.imageUrl(item),
					title = if (item.type == BaseItemKind.EPISODE) item.seriesName ?: item.name.orEmpty() else item.name.orEmpty(),
					subtitle = subtitleFor(item),
					shape = if (row.wide) KCardShape.Wide else KCardShape.Poster,
					width = if (row.wide) 280.dp else 148.dp,
					showTitle = row.wide,
					progress = progress,
					onFocus = { onFocus(item) },
					onClick = { onOpen(item) },
					onLongClick = onOptions?.let { { it(item) } },
					logoUrl = if (row.wide) repository.showLogoUrl(item) else null,
					backdropUrl = repository.backdropUrl(item, 1280),
					focusKey = "${row.title}|${item.id}",
				)
			}
		}
		}
	}
}

fun typeLabel(item: BaseItemDto) = when (item.type) {
	BaseItemKind.MOVIE -> "MOVIE"
	BaseItemKind.SERIES -> "SERIES"
	BaseItemKind.EPISODE -> "EPISODE"
	BaseItemKind.TV_CHANNEL -> "LIVE"
	BaseItemKind.BOX_SET -> "COLLECTION"
	else -> ""
}

fun subtitleFor(item: BaseItemDto): String? = when (item.type) {
	BaseItemKind.EPISODE -> listOfNotNull(
		item.parentIndexNumber?.let { s -> item.indexNumber?.let { e -> "S$s · E$e" } },
		item.name,
	).joinToString(" · ")
	BaseItemKind.MOVIE, BaseItemKind.SERIES -> item.productionYear?.toString()
	else -> null
}

fun metaLine(item: BaseItemDto): String = listOfNotNull(
	item.productionYear?.toString(),
	item.officialRating,
	item.runTimeTicks?.let { ticks ->
		val minutes = ticks / 600_000_000
		if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
	}?.takeIf { item.type != BaseItemKind.SERIES },
	item.genres?.take(3)?.joinToString(" · ")?.takeIf { it.isNotEmpty() },
).joinToString("  ·  ")

/** Options for one title: hold OK on it to open. */
/**
 * What you can do with a Continue watching / Up next tile (hold OK). Opens above everything,
 * and ignores the OK press that opened it, so letting go doesn't pick an option by accident.
 */
@Composable
private fun ItemOptions(item: BaseItemDto, fromUpNext: Boolean, repository: KhyloRepository, onDismiss: () -> Unit, onChanged: () -> Unit) {
	val navigator = navigator()
	val scope = androidx.compose.runtime.rememberCoroutineScope()
	val first = remember { androidx.compose.ui.focus.FocusRequester() }
	// OK only counts once it has been pressed again inside the menu
	var armed by remember { mutableStateOf(false) }
	val hasProgress = (item.userData?.playbackPositionTicks ?: 0L) > 0
	val isEpisode = item.type == BaseItemKind.EPISODE
	val episodeLabel = if (isEpisode) " S${item.parentIndexNumber} E${item.indexNumber}" else ""

	data class Action(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val run: () -> Unit)

	fun change(block: suspend () -> Unit) {
		scope.launch {
			runCatching { block() }
			onChanged()
		}
	}

	val actions = buildList {
		if (hasProgress) {
			add(Action("Resume$episodeLabel", KIcons.Play) { onDismiss(); navigator.go(Route.Player(item)) })
			add(Action("Play from the beginning", KIcons.Retry) { onDismiss(); navigator.go(Route.Player(item, 0)) })
		} else {
			add(Action("Play$episodeLabel", KIcons.Play) { onDismiss(); navigator.go(Route.Player(item)) })
		}
		item.seriesId?.takeIf { isEpisode }?.let { seriesId -> add(Action("Go to show", KIcons.Tv) { onDismiss(); navigator.go(Route.Details(seriesId)) }) }
		add(Action(if (isEpisode) "Episode details" else "Details", KIcons.Info) { onDismiss(); navigator.go(Route.Details(item.id)) })
		add(Action("Mark as watched", KIcons.Check) { change { repository.markWatched(item) } })
		if (hasProgress) add(Action("Remove from Continue watching", KIcons.Remove) { change { repository.removeFromContinueWatching(item) } })
		if (fromUpNext) add(Action("Remove from Up next", KIcons.Remove) { change { repository.removeFromUpNext(item) } })
	}

	androidx.compose.ui.window.Dialog(
		onDismissRequest = onDismiss,
		properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
	) {
		LaunchedEffect(Unit) {
			kotlinx.coroutines.delay(50)
			runCatching { first.requestFocus() }
		}
		Box(
			Modifier
				.fillMaxSize()
				.background(Color(0xCC000000))
				.onPreviewKeyEvent { event ->
					val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
					when {
						!isOk -> false
						event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 && !armed -> {
							armed = true
							false
						}
						!armed -> true
						else -> false
					}
				},
			contentAlignment = Alignment.Center,
		) {
			Column(
				Modifier
					.width(520.dp)
					.clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
					.background(Color(0xFF111214))
					.border(1.dp, Color(0x1FFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
			) {
				// The tile's picture with the show's logo
				Box(Modifier.fillMaxWidth().height(200.dp)) {
					KImage(repository.thumbUrl(item, 960), Modifier.fillMaxSize())
					Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color(0xFF111214))))
					Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 12.dp)) {
						val logo = repository.showLogoUrl(item)
						if (logo != null) {
							KImage(logo, Modifier.height(52.dp).width(260.dp), ContentScale.Fit, Alignment.BottomStart)
						} else {
							KText(item.seriesName ?: item.name.orEmpty(), style = KhyloType.Title, maxLines = 1)
						}
						subtitleFor(item)?.let {
							Spacer(Modifier.height(6.dp))
							KText(it, style = KhyloType.Label, color = KhyloColors.TextSecondary, maxLines = 1)
						}
					}
				}
				Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp)) {
					actions.forEachIndexed { index, action ->
						OptionsRow(action.label, action.icon, if (index == 0) Modifier.focusRequester(first) else Modifier, action.run)
					}
				}
			}
		}
	}
}

@Composable
private fun OptionsRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
	val (source, focused) = org.khylo.tv.design.rememberFocusState()
	Row(
		modifier
			.fillMaxWidth()
			.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
			.background(if (focused) Color.White else Color.Transparent)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 14.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		org.khylo.tv.design.KIcon(icon, if (focused) KhyloColors.OnFocus else KhyloColors.TextSecondary, Modifier.size(22.dp))
		KText(
			label,
			style = KhyloType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold).merge(KhyloType.Centered),
			color = if (focused) KhyloColors.OnFocus else KhyloColors.Text,
			maxLines = 1,
		)
	}
}
