package org.khylo.tv.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import org.khylo.sdk.model.api.BaseItemDto
import java.util.UUID

/** Every screen of Khylo TV once signed in. */
sealed interface Route {
	/** Screens that cover the whole TV (no nav rail). */
	val fullScreen: Boolean get() = false

	data object Home : Route
	data object Search : Route
	data object LiveTv : Route
	data object Settings : Route
	data object Libraries : Route
	data object LiveChannels : Route
	data object Profiles : Route {
		override val fullScreen = true
	}
	/** A QR code a phone scans to sign in as whoever's using this TV. */
	data object SignInAnotherDevice : Route {
		override val fullScreen = true
	}
	data class Library(val view: BaseItemDto) : Route
	data class Genre(val view: BaseItemDto, val genre: BaseItemDto) : Route
	data class Details(val itemId: UUID) : Route
	data class Person(val personId: UUID, val name: String) : Route
	data class Player(val item: BaseItemDto, val startPositionMs: Long? = null, val liveChannelId: String? = null, val mediaSourceId: String? = null, val audioStreamIndex: Int? = null, val subtitleStreamIndex: Int? = null) : Route {
		override val fullScreen = true
	}
	/** A DVD's menu: play, scenes, extras. */
	data class DiscMenu(val item: BaseItemDto) : Route {
		override val fullScreen = true
	}
	/** Music: an album, artist or song (the album plays on from that song). */
	data class Music(val item: BaseItemDto, val shuffle: Boolean = false) : Route {
		override val fullScreen = true
	}
	data class Audiobook(val item: BaseItemDto) : Route {
		override val fullScreen = true
	}
	data class Reader(val item: BaseItemDto) : Route {
		override val fullScreen = true
	}
	data class MultiView(val channels: List<BaseItemDto>) : Route {
		override val fullScreen = true
	}
}

/**
 * Searches in progress: kept while you open a result and come back, forgotten when you open a
 * section from the top bar or switch tabs.
 */
object SearchMemory {
	var global: String = ""
	val library = mutableMapOf<UUID, String>()

	fun clear() {
		global = ""
		library.clear()
	}
}

/** A simple back stack; the TV remote's Back button pops it. */
class Navigator {
	val stack = mutableStateListOf<Route>(Route.Home)
	val current: Route get() = stack.last()

	fun go(route: Route) {
		stack.add(route)
	}

	/** Opens a top-level section (from the nav rail), keeping Home underneath. */
	fun openSection(route: Route) {
		// Choosing a section from the bar starts it fresh, without an old search
		SearchMemory.clear()
		org.khylo.tv.library.LibraryPages.clear()
		org.khylo.tv.data.WatchStateChanges.clear()
		stack.clear()
		stack.add(Route.Home)
		if (route != Route.Home) stack.add(route)
	}

	fun replace(route: Route) {
		stack.removeAt(stack.lastIndex)
		stack.add(route)
	}

	/** @return false when there's nothing left to go back to. */
	fun back(): Boolean {
		if (stack.size <= 1) return false
		stack.removeAt(stack.lastIndex)
		return true
	}
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

@Composable
fun navigator(): Navigator = LocalNavigator.current
