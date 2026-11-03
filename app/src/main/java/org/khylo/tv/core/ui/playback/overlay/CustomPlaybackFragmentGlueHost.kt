package org.khylo.tv.core.ui.playback.overlay

import androidx.leanback.app.PlaybackSupportFragment
import androidx.leanback.app.PlaybackSupportFragmentGlueHost
import androidx.leanback.widget.Action
import androidx.leanback.widget.OnActionClickedListener
import org.khylo.tv.core.ui.playback.overlay.action.CustomAction

class CustomPlaybackFragmentGlueHost(
	private val fragment: PlaybackSupportFragment,
) : PlaybackSupportFragmentGlueHost(fragment) {
	override fun setOnActionClickedListener(listener: OnActionClickedListener?) {
		if (listener == null) {
			fragment.setOnPlaybackItemViewClickedListener(null)
			return
		}

		fragment.setOnPlaybackItemViewClickedListener { itemViewHolder, item, _, _ ->
			// Call our custom function and pass the view instance
			if (item is CustomAction) item.onCustomActionClicked(itemViewHolder.view)
			if (item is Action) listener.onActionClicked(item)
		}
	}
}
