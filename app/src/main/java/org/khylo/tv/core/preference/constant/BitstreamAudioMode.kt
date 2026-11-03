package org.khylo.tv.core.preference.constant

import org.khylo.tv.core.R
import org.khylo.tv.preference.PreferenceEnum

/**
 * How a bitstream audio format should be treated in the device profile.
 */
enum class BitstreamAudioMode(
	override val nameRes: Int,
) : PreferenceEnum {
	AUTO(R.string.hdr_override_auto),
	ENABLE(R.string.hdr_override_enable),
	DISABLE(R.string.hdr_override_disable),
}
