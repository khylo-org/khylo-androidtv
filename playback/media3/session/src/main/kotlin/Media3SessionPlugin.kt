package org.khylo.tv.playback.media3.session

import android.content.Context
import org.khylo.tv.playback.core.plugin.playbackPlugin

fun media3SessionPlugin(
	androidContext: Context,
	options: MediaSessionOptions
) = playbackPlugin {
	provide(MediaSessionService(androidContext, options))
}
