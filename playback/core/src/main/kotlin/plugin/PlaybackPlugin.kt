package org.khylo.tv.playback.core.plugin

import org.khylo.tv.playback.core.backend.PlayerBackend
import org.khylo.tv.playback.core.mediastream.MediaStreamResolver

fun interface PlaybackPlugin {
	fun install(context: InstallContext)

	interface InstallContext {
		fun provide(backend: PlayerBackend)
		fun provide(service: PlayerService)
		fun provide(mediaStreamResolver: MediaStreamResolver)
	}
}

fun playbackPlugin(init: PlaybackPlugin.InstallContext.() -> Unit) = PlaybackPlugin { context -> context.init() }
