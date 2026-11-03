package org.khylo.tv.playback.core.backend

import org.khylo.tv.playback.core.mediastream.PlayableMediaStream
import org.khylo.tv.playback.core.model.PlayState

abstract class PlayerBackendEventListener {
	open fun onPlayStateChange(state: PlayState) = Unit
	open fun onVideoSizeChange(width: Int, height: Int) = Unit
	open fun onMediaStreamEnd(mediaStream: PlayableMediaStream) = Unit
}
