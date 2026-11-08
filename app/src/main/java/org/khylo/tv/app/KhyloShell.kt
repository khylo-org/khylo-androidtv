package org.khylo.tv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.khylo.tv.core.R
import org.khylo.tv.core.auth.repository.UserRepository
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.sdk.model.api.CollectionType
import org.khylo.sdk.model.api.ImageType
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.autoFocusContent
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.home.HomeScreen
import org.khylo.tv.library.DetailsScreen
import org.khylo.tv.library.LibraryScreen
import org.khylo.tv.library.SearchScreen
import org.khylo.tv.livetv.LiveTvScreen
import org.khylo.tv.livetv.MultiViewScreen
import org.khylo.tv.player.PlayerScreen
import org.khylo.tv.settings.SettingsScreen
import org.khylo.tv.livechannels.toItemId
import org.koin.compose.koinInject

/** Audiobook libraries (the server's own type, not one the app's SDK knows by name). */
fun isAudiobookLibrary(view: BaseItemDto) = "audiobook" in view.name.orEmpty().lowercase()

fun libraryIcon(view: BaseItemDto): ImageVector {
	val name = view.name.orEmpty().lowercase()
	return when {
		view.collectionType == CollectionType.MOVIES -> KIcons.Movie
		view.collectionType == CollectionType.TVSHOWS -> KIcons.Tv
		view.collectionType == CollectionType.MUSIC -> KIcons.Music
		view.collectionType == CollectionType.BOXSETS -> KIcons.Collection
		view.collectionType == CollectionType.HOMEVIDEOS || view.collectionType == CollectionType.PHOTOS -> KIcons.Photos
		"audiobook" in name -> KIcons.Audiobook
		"podcast" in name -> KIcons.Podcast
		view.collectionType == CollectionType.BOOKS || "book" in name -> KIcons.Book
		else -> KIcons.Collection
	}
}

private data class Tab(val label: String, val route: Route)

/** The signed-in app: screens fill the TV, with the navigation bar across the top. */
@Composable
fun KhyloShell(onExit: () -> Unit) {
	val navigator = remember { Navigator() }
	ReturnHomeAfterSleep(navigator)
	val repository = koinInject<KhyloRepository>()
	// Handoff: another of the viewer's devices sent something here, so it starts right where they were
	LaunchedEffect(Unit) {
		org.khylo.tv.handoff.HandoffInbox.incomingPlays.collect { incoming ->
			val item = runCatching { repository.item(incoming.itemId) }.getOrNull() ?: return@collect
			val route = Route.Player(item, incoming.startPositionMs, null, incoming.mediaSourceId, incoming.audioStreamIndex, incoming.subtitleStreamIndex)
			// Replace whatever is on screen if it is already a player, so Back doesn't return to the old one
			if (navigator.current is Route.Player) navigator.replace(route) else navigator.go(route)
		}
	}
	// Mirror: the TV being followed switched to a Khylo TV channel, so this one tunes to it
	val tuneApi = koinInject<org.khylo.tv.livechannels.LiveChannelsApi>()
	LaunchedEffect(Unit) {
		org.khylo.tv.handoff.HandoffInbox.tuneRequests.collect { channelId ->
			runCatching {
				val tune = tuneApi.tune(channelId)
				val current = requireNotNull(tune.current)
				val item = repository.item(current.itemId.toItemId())
				val route = Route.Player(item, tune.startPositionTicks / 10_000, liveChannelId = channelId)
				if (navigator.current is Route.Player) navigator.replace(route) else navigator.go(route)
			}
		}
	}
	val libraries by produceState(emptyList<BaseItemDto>()) {
		value = runCatching { repository.libraries() }.getOrDefault(emptyList())
	}

	// Back on the first screen asks once before leaving the app, so a stray press doesn't close it
	var exitArmed by remember { mutableStateOf(false) }
	LaunchedEffect(exitArmed) {
		if (exitArmed) {
			kotlinx.coroutines.delay(2500)
			exitArmed = false
		}
	}
	BackHandler {
		when {
			navigator.back() -> exitArmed = false
			exitArmed -> onExit()
			else -> exitArmed = true
		}
	}

	val channelsApi = koinInject<org.khylo.tv.livechannels.LiveChannelsApi>()
	val liveChannelsEnabled by produceState(false) { value = channelsApi.isEnabled() }

	// The bar stays short: Home, the live sections, Movies and TV. Everything else
	// (music, books, audiobooks, collections, ...) lives under More.
	val mainLibraries = libraries.filter { it.collectionType == CollectionType.MOVIES || it.collectionType == CollectionType.TVSHOWS }
		.sortedBy { if (it.collectionType == CollectionType.MOVIES) 0 else 1 }
	val otherLibraries = libraries.filter {
		it !in mainLibraries && it.collectionType != CollectionType.LIVETV && it.collectionType != CollectionType.PLAYLISTS
	}
	val currentUser by koinInject<UserRepository>().currentUser.collectAsState()
	val liveTvAllowed = currentUser?.policy?.enableLiveTvAccess != false
	val tabs = buildList {
		add(Tab("Home", Route.Home))
		// Kids profiles may not be allowed Live TV
		if (liveTvAllowed) add(Tab("Live TV", Route.LiveTv))
		if (liveChannelsEnabled) add(Tab("Khylo TV", Route.LiveChannels))
		mainLibraries.forEach { add(Tab(it.name.orEmpty(), Route.Library(it))) }
		if (otherLibraries.isNotEmpty()) add(Tab("More", Route.Libraries))
	}

	// The screen below the bar; Down from the bar always comes back to it
	val contentFocus = remember { FocusRequester() }

	val ambient = remember { org.khylo.tv.design.AmbientState() }
	// Each screen in the back stack keeps its state (scroll position, loaded titles, the focused
	// card) while you're on a screen above it, so Back returns to exactly where you were
	val screens = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
	val screenKeys = remember { mutableSetOf<String>() }
	val liveKeys = navigator.stack.mapIndexed { depth, entry -> "$depth:${entry.hashCode()}" }.toSet()
	LaunchedEffect(liveKeys) {
		(screenKeys - liveKeys).forEach { screens.removeState(it) }
		screenKeys.retainAll(liveKeys)
	}
	CompositionLocalProvider(LocalNavigator provides navigator, org.khylo.tv.design.LocalAmbient provides ambient) {
		val route = navigator.current
		// Each page starts on the resting backdrop until something is focused
		LaunchedEffect(route) { ambient.clear() }
		Box(Modifier.fillMaxSize().background(KhyloColors.Background)) {
			// Home, title pages and Live Channels bring their own full artwork
			val ownBackdrop = route == Route.Home || route is Route.Details || route == Route.LiveChannels || route == Route.Profiles
			if (!route.fullScreen && !ownBackdrop) org.khylo.tv.design.AmbientBackground(ambient)
			val screenKey = "${navigator.stack.lastIndex}:${route.hashCode()}"
			screenKeys += screenKey
			val content: @Composable () -> Unit = {
				screens.SaveableStateProvider(screenKey) {
					val memory = androidx.compose.runtime.saveable.rememberSaveable(saver = org.khylo.tv.design.FocusMemorySaver) { org.khylo.tv.design.FocusMemory() }
					CompositionLocalProvider(org.khylo.tv.design.LocalFocusMemory provides memory) {
						RouteContent(route, otherLibraries)
					}
				}
			}
			if (route.fullScreen) {
				content()
			} else {
				// Home and title pages run their artwork under the bar; others start below it
				val fullBleed = route == Route.Home || route is Route.Details || route == Route.LiveChannels ||
				(route is Route.Library && route.view.collectionType in listOf(CollectionType.MOVIES, CollectionType.TVSHOWS))
				Box(Modifier.fillMaxSize().padding(top = if (fullBleed) 0.dp else KhyloSpacing.NavHeight).autoFocusContent(route, contentFocus)) {
					content()
				}
				TopNav(tabs, route, onSelect = { navigator.openSection(it) }, onProfiles = { navigator.go(Route.Profiles) }, onDown = { runCatching { contentFocus.requestFocus() } })
			}
			androidx.compose.animation.AnimatedVisibility(
				visible = exitArmed,
				enter = androidx.compose.animation.fadeIn(),
				exit = androidx.compose.animation.fadeOut(),
				modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
			) {
				org.khylo.tv.design.KText(
					"Press Back again to exit Khylo",
					style = org.khylo.tv.design.KhyloType.Label,
					color = Color.White,
					modifier = Modifier
						.background(Color(0xE6141516), androidx.compose.foundation.shape.RoundedCornerShape(50))
						.padding(horizontal = 22.dp, vertical = 12.dp),
				)
			}
		}
	}
}

@Composable
private fun RouteContent(route: Route, otherLibraries: List<BaseItemDto>) {
	when (route) {
		Route.Home -> HomeScreen()
		Route.Search -> SearchScreen()
		Route.LiveTv -> LiveTvScreen()
		Route.Settings -> SettingsScreen()
		Route.Libraries -> LibrariesScreen(otherLibraries)
		Route.LiveChannels -> org.khylo.tv.livechannels.LiveChannelsScreen()
		Route.Profiles -> org.khylo.tv.profiles.ProfilesScreen()
		Route.SignInAnotherDevice -> org.khylo.tv.settings.SignInAnotherDeviceScreen()
		is Route.Library -> when (route.view.collectionType) {
			CollectionType.MOVIES, CollectionType.TVSHOWS -> org.khylo.tv.library.MediaLibraryScreen(route.view)
			CollectionType.MUSIC -> org.khylo.tv.listen.MusicLibraryScreen(route.view)
			else -> LibraryScreen(route.view)
		}
		is Route.Genre -> LibraryScreen(route.view, route.genre)
		is Route.Details -> DetailsScreen(route.itemId)
		is Route.Person -> org.khylo.tv.library.PersonScreen(route.personId, route.name)
		is Route.Player -> PlayerScreen(route.item, route.startPositionMs, route.liveChannelId, route.mediaSourceId, route.audioStreamIndex, route.subtitleStreamIndex)
		is Route.DiscMenu -> org.khylo.tv.library.DiscMenuScreen(route.item)
		is Route.MultiView -> MultiViewScreen(route.channels)
		is Route.Music -> org.khylo.tv.listen.MusicPlayerScreen(route.item, route.shuffle)
		is Route.Audiobook -> org.khylo.tv.listen.AudiobookPlayerScreen(route.item)
		is Route.Reader -> org.khylo.tv.reader.ReaderScreen(route.item)
	}
}

private fun Route.matches(other: Route): Boolean = this == other ||
	(this is Route.Library && other is Route.Library && view.id == other.view.id) ||
	(this is Route.Genre && other is Route.Library && view.id == other.view.id)

/** Khylo logo, section tabs, then search, settings and profile. Press Up from a screen to reach it. */
@Composable
private fun TopNav(tabs: List<Tab>, current: Route, onSelect: (Route) -> Unit, onProfiles: () -> Unit, onDown: () -> Unit) {
	val userRepository = koinInject<UserRepository>()
	val user by userRepository.currentUser.collectAsState()
	val selectedFocus = remember { FocusRequester() }

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(KhyloSpacing.NavHeight)
			// Deep black fading out, so the bar reads over any artwork
			.background(Brush.verticalGradient(0f to Color(0xF2000000), 0.55f to Color(0x99000000), 1f to Color.Transparent))
			// Down always goes back into the screen, even when what you were on is scrolled
			// partly under the bar (full-bleed pages run beneath it)
			.onPreviewKeyEvent { event ->
				if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && event.key == androidx.compose.ui.input.key.Key.DirectionDown) {
					onDown()
					true
				} else {
					false
				}
			}
			.focusRestorer(selectedFocus)
			.focusGroup()
			.padding(horizontal = KhyloSpacing.Gutter),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Image(painterResource(R.drawable.app_logo), contentDescription = "Khylo", modifier = Modifier.height(24.dp))
		Spacer(Modifier.weight(1f))

		// The sections sit in one clean, see-through oval, centred
		Row(
			Modifier.ovalOutline().padding(horizontal = 6.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			NavIcon(KIcons.Search, "Search", current == Route.Search, if (current == Route.Search) Modifier.focusRequester(selectedFocus) else Modifier) { onSelect(Route.Search) }
			tabs.forEach { tab ->
				val selected = current.matches(tab.route)
				NavTab(tab.label, selected, if (selected) Modifier.focusRequester(selectedFocus) else Modifier) { onSelect(tab.route) }
			}
		}

		Spacer(Modifier.weight(1f))
		Row(
			Modifier.ovalOutline().padding(6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			NavIcon(KIcons.Settings, "Settings", current == Route.Settings, if (current == Route.Settings) Modifier.focusRequester(selectedFocus) else Modifier) { onSelect(Route.Settings) }
			// Profile: opens "Who's watching?" to switch or add home profiles
			val (profileSource, profileFocused) = rememberFocusState()
			Box(
				Modifier
					.size(40.dp)
					.clip(CircleShape)
					.background(KhyloColors.Accent)
					.border(3.dp, if (profileFocused) KhyloColors.Focus else Color.Transparent, CircleShape)
					.clickable(interactionSource = profileSource, indication = null, onClick = onProfiles),
				contentAlignment = Alignment.Center,
			) {
				KText(user?.name?.take(1)?.uppercase().orEmpty(), style = KhyloType.Label.copy(fontWeight = FontWeight.Bold), color = Color.White)
			}
		}
	}
}

/** A fully transparent oval with a hairline edge, holding a group of controls. */
private fun Modifier.ovalOutline(): Modifier = this
	.clip(RoundedCornerShape(50))
	.border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(50))

@Composable
private fun NavTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
	org.khylo.tv.design.KTab(label, selected, onClick, modifier.padding(horizontal = 2.dp))
}

@Composable
private fun NavIcon(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Box(
		modifier
			.size(40.dp)
			.clip(CircleShape)
			.background(
				when {
					focused -> KhyloColors.Focus
					else -> Color.Transparent
				}
			)
			.clickable(interactionSource = source, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		KIcon(icon, if (focused) KhyloColors.OnFocus else if (selected) KhyloColors.Accent else Color(0xE6FFFFFF), Modifier.size(22.dp), label)
	}
}

/** Every library on the server as a wide tile. */
@Composable
private fun LibrariesScreen(libraries: List<BaseItemDto>) {
	val navigator = navigator()
	val repository = koinInject<KhyloRepository>()
	Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
		KText("More", style = KhyloType.Title, modifier = Modifier.padding(start = KhyloSpacing.Gutter, bottom = 20.dp))
		LazyVerticalGrid(
			columns = GridCells.Fixed(4),
			contentPadding = PaddingValues(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, bottom = 48.dp),
			horizontalArrangement = Arrangement.spacedBy(20.dp),
			verticalArrangement = Arrangement.spacedBy(20.dp),
		) {
			gridItems(libraries, key = { it.id }) { library ->
				val imageUrl = repository.imageUrl(library, ImageType.PRIMARY, 640)
				// A library without artwork of its own shows the covers of its newest titles
				val covers by produceState(emptyList<String>(), library.id, imageUrl) {
					if (imageUrl == null) value = repository.libraryCovers(library).mapNotNull { repository.imageUrl(it, ImageType.PRIMARY, 400) }
				}
				LibraryTile(library, imageUrl, covers) { navigator.go(Route.Library(library)) }
			}
		}
	}
}

@Composable
private fun LibraryTile(library: BaseItemDto, imageUrl: String?, covers: List<String>, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Box(
		Modifier
			.fillMaxWidth()
			.aspectRatio(16f / 9f)
			.scale(if (focused) 1.06f else 1f)
			.clip(RoundedCornerShape(10.dp))
			.background(KhyloColors.SurfaceRaised)
			.border(if (focused) 3.dp else 0.dp, if (focused) KhyloColors.Focus else Color.Transparent, RoundedCornerShape(10.dp))
			.clickable(interactionSource = source, indication = null, onClick = onClick),
	) {
		if (imageUrl != null) {
			KImage(imageUrl, Modifier.fillMaxSize())
		} else if (covers.isNotEmpty()) {
			// A soft wash of the first cover, with the covers themselves standing on it
			KImage(covers.first(), Modifier.fillMaxSize().blur(40.dp))
			Box(Modifier.fillMaxSize().background(Color(0x66000000)))
			Row(
				Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				covers.forEach { url ->
					KImage(
						url,
						Modifier
							.size(110.dp)
							.shadow(12.dp, RoundedCornerShape(6.dp))
							.clip(RoundedCornerShape(6.dp)),
					)
				}
			}
		}
		Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xD9000000))))
		Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			KIcon(libraryIcon(library), Color.White, Modifier.size(22.dp))
			KText(library.name.orEmpty(), style = KhyloType.Heading, maxLines = 1)
		}
	}
}
