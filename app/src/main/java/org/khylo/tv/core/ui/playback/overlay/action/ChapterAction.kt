package org.khylo.tv.core.ui.playback.overlay.action

import android.content.Context
import android.view.View
import org.khylo.tv.core.R
import org.khylo.tv.core.ui.playback.PlaybackController
import org.khylo.tv.core.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.khylo.tv.core.ui.playback.overlay.LeanbackOverlayFragment
import org.khylo.tv.core.ui.playback.overlay.VideoPlayerAdapter

class ChapterAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	init {
		initializeWithIcon(R.drawable.ic_select_chapter)
	}

	@Override
	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		videoPlayerAdapter.leanbackOverlayFragment.hideOverlay()
		videoPlayerAdapter.masterOverlayFragment.showChapterSelector();
	}
}
