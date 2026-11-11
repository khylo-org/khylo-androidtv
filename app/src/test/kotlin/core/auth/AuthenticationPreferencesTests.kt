package org.khylo.tv.core.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.khylo.tv.core.auth.store.AuthenticationPreferences
import org.khylo.tv.core.preference.constant.UserSelectBehavior

// How the TV opens: on "Who's watching?", unless the owner chose to open straight into the last profile
class AuthenticationPreferencesTests : FunSpec({
	test("opening Khylo asks who is watching first, by default") {
		AuthenticationPreferences.autoLoginUserBehavior.defaultValue shouldBe UserSelectBehavior.DISABLED
	}

	test("a TV that was told to open straight into the last profile still can") {
		// The choice is stored under the same key as before, so nobody who picked it loses it
		AuthenticationPreferences.autoLoginUserBehavior.key shouldBe "auto_login_user_behavior"
		UserSelectBehavior.valueOf("LAST_USER") shouldBe UserSelectBehavior.LAST_USER
	}
})
