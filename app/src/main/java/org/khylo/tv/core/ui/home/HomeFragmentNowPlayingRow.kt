package org.khylo.tv.core.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import androidx.lifecycle.LifecycleCoroutineScope
import org.khylo.tv.core.R
import org.khylo.tv.core.ui.playback.AudioQueueBaseRowAdapter
import org.khylo.tv.core.ui.playback.MediaManager
import org.khylo.tv.core.ui.presentation.CardPresenter
import org.khylo.tv.core.ui.presentation.MutableObjectAdapter
import org.khylo.tv.playback.core.PlaybackManager

class HomeFragmentNowPlayingRow(
	private val lifecycleScope: LifecycleCoroutineScope,
	private val playbackManager: PlaybackManager,
	private val mediaManager: MediaManager,
) : HomeFragmentRow {
	private var row: ListRow? = null

	override fun addToRowsAdapter(
		context: Context,
		cardPresenter: CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>
	) {
		update(context, rowsAdapter)
	}

	fun update(context: Context, rowsAdapter: MutableObjectAdapter<Row>) {
		if (mediaManager.hasAudioQueueItems()) {
			// Ensure row exists
			if (row == null) row = ListRow(
				HeaderItem(context.getString(R.string.lbl_now_playing)),
				AudioQueueBaseRowAdapter(playbackManager, lifecycleScope)
			)
			// Add row if it wasn't added already
			if (!rowsAdapter.contains(row!!)) rowsAdapter.add(0, row!!)
		} else if (row != null) {
			rowsAdapter.remove(row!!)
			row = null
		}
	}
}
