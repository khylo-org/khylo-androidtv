package org.khylo.tv.core.ui.playback.overlay.action

import android.content.Context
import androidx.leanback.widget.PlaybackControlsRow
import org.khylo.tv.core.ui.playback.overlay.VideoPlayerAdapter

class SkipPreviousAction(context: Context) : PlaybackControlsRow.SkipPreviousAction(context),
	AndroidAction {
	override fun onActionClicked(videoPlayerAdapter: VideoPlayerAdapter) =
		videoPlayerAdapter.previous()
}
