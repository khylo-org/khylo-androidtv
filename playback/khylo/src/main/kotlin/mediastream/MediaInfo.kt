package org.khylo.tv.playback.khylo.mediastream

import org.khylo.sdk.model.api.MediaSourceInfo

data class MediaInfo(
	val playSessionId: String,
	val mediaSource: MediaSourceInfo,
)
