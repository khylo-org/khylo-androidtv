package org.khylo.tv.core.auth.model

import org.khylo.tv.core.R
import org.khylo.tv.preference.PreferenceEnum

enum class AuthenticationSortBy(
	override val nameRes: Int
) : PreferenceEnum {
	LAST_USE(R.string.last_use),
	ALPHABETICAL(R.string.alphabetical);
}
