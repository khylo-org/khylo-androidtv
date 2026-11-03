package org.khylo.tv.core.ui.playback

import org.khylo.tv.core.preference.UserPreferences;

class VideoQualityController(
	previousQualitySelection: String,
	private val userPreferences: UserPreferences,
) {
	var currentQuality = previousQualitySelection
		set(value) {
			userPreferences[UserPreferences.maxBitrate] = value
			field = value
		}
}
