package org.khylo.tv.core.preference.constant

import org.khylo.tv.core.R
import org.khylo.tv.preference.PreferenceEnum

enum class AudioBehavior(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * Directly stream audio without any changes
	 */
	DIRECT_STREAM(R.string.pref_audio_direct),

	/**
	 * Downnmix audio to stereo. Disables the AC3, EAC3 and AAC_LATM audio codecs.
	 */
	DOWNMIX_TO_STEREO(R.string.pref_audio_compat),
}
