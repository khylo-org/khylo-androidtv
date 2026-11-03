package org.khylo.tv.core.ui.playback

import org.khylo.tv.core.ui.InteractionTrackerViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class PlaybackOverlayFragmentHelper(
	val fragment: CustomPlaybackOverlayFragment
) {
	private val interactionTrackerViewModel by fragment.activityViewModel<InteractionTrackerViewModel>()
	private var screensaverLock: (() -> Unit)? = null

	fun setScreensaverLock(enabled: Boolean) {
		if (enabled && screensaverLock == null) {
			screensaverLock = interactionTrackerViewModel.addLifecycleLock(fragment.lifecycle)
		} else if (!enabled) {
			screensaverLock?.invoke()
			screensaverLock = null
		}
	}
}
