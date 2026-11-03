package org.khylo.tv.core.auth.store

import android.content.Context
import org.khylo.tv.core.auth.model.AuthenticationSortBy
import org.khylo.tv.core.preference.constant.UserSelectBehavior
import org.khylo.tv.preference.booleanPreference
import org.khylo.tv.preference.enumPreference
import org.khylo.tv.preference.store.SharedPreferenceStore
import org.khylo.tv.preference.stringPreference

class AuthenticationPreferences(context: Context) : SharedPreferenceStore(
	sharedPreferences = context.getSharedPreferences("authentication", Context.MODE_PRIVATE)
) {
	companion object {
		// Preferences
		val autoLoginUserBehavior = enumPreference("auto_login_user_behavior", UserSelectBehavior.LAST_USER)
		val autoLoginServerId = stringPreference("auto_login_server_id", "")
		val autoLoginUserId = stringPreference("auto_login_user_id", "")

		val sortBy = enumPreference("sort_by", AuthenticationSortBy.LAST_USE)
		val alwaysAuthenticate = booleanPreference("always_authenticate", false)

		// Persistent state
		val lastServerId = stringPreference("last_server_id", "")
		val lastUserId = stringPreference("last_user_id", "")
	}

	init {
		runMigrations {
			// v0.15.4 to v0.15.5
			migration(toVersion = 2) {
				// Unfortunately we cannot migrate the "specific user" login option
				// so we'll reset the preference to disabled if it was used
				if (it.getString("auto_login_user_behavior", null) === UserSelectBehavior.SPECIFIC_USER.name) {
					putString("auto_login_user_id", "")
					putString("auto_login_user_behavior", UserSelectBehavior.DISABLED.name)
				}

				putString("last_user_id", "")
			}
		}
	}
}
