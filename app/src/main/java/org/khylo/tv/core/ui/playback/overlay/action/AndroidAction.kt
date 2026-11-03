package org.khylo.tv.core.ui.playback.overlay.action

import org.khylo.tv.core.ui.playback.overlay.VideoPlayerAdapter

interface AndroidAction {
	fun onActionClicked(
		videoPlayerAdapter: VideoPlayerAdapter
	)
}
