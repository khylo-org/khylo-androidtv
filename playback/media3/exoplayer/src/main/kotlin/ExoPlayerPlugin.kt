package org.khylo.tv.playback.media3.exoplayer

import android.content.Context
import org.khylo.tv.playback.core.plugin.playbackPlugin

fun exoPlayerPlugin(
	androidContext: Context,
	exoPlayerOptions: ExoPlayerOptions = ExoPlayerOptions(),
) = playbackPlugin {
	provide(ExoPlayerBackend(androidContext, exoPlayerOptions))
}
