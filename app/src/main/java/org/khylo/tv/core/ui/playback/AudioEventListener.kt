package org.khylo.tv.core.ui.playback

import org.khylo.sdk.model.api.BaseItemDto

interface AudioEventListener {
	fun onPlaybackStateChange(newState: PlaybackController.PlaybackState, currentItem: BaseItemDto?) = Unit
	fun onProgress(pos: Long, duration: Long) = Unit
	fun onQueueStatusChanged(hasQueue: Boolean) = Unit
	fun onQueueReplaced() = Unit
}
