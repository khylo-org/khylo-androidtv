package org.khylo.tv.core.di

import org.khylo.tv.core.preference.LiveTvPreferences
import org.khylo.tv.core.preference.PreferencesRepository
import org.khylo.tv.core.preference.SystemPreferences
import org.khylo.tv.core.preference.TelemetryPreferences
import org.khylo.tv.core.preference.UserPreferences
import org.khylo.tv.core.preference.UserSettingPreferences
import org.koin.dsl.module

val preferenceModule = module {
	single { PreferencesRepository(get(), get(), get()) }

	single { LiveTvPreferences(get()) }
	single { UserSettingPreferences(get()) }
	single { UserPreferences(get()) }
	single { SystemPreferences(get()) }
	single { TelemetryPreferences(get()) }
}
