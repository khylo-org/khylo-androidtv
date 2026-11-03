package org.khylo.tv.playback.core

import kotlin.time.Duration

class PlaybackManagerOptions(
	val playerVolumeState: PlayerVolumeState,

	val defaultRewindAmount: () -> Duration,
	val defaultFastForwardAmount: () -> Duration,
)
