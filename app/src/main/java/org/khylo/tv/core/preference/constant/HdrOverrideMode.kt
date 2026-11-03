package org.khylo.tv.core.preference.constant

import org.khylo.tv.core.R
import org.khylo.tv.preference.PreferenceEnum

/**
 * How an HDR format should be treated in the device profile, overriding device-reported capabilities.
 */
enum class HdrOverrideMode(
	override val nameRes: Int,
) : PreferenceEnum {
	AUTO(R.string.hdr_override_auto),
	ENABLE(R.string.hdr_override_enable),
	DISABLE(R.string.hdr_override_disable),
}
