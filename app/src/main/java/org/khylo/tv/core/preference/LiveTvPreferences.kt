package org.khylo.tv.core.preference

import org.khylo.tv.core.preference.store.DisplayPreferencesStore
import org.khylo.tv.preference.booleanPreference
import org.khylo.tv.preference.stringPreference
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.model.api.ItemSortBy

class LiveTvPreferences(
	api: ApiClient,
) : DisplayPreferencesStore(
	displayPreferencesId = "livetv",
	api = api,
) {
	companion object {
		val channelOrder = stringPreference("livetv-channelorder", ItemSortBy.DATE_PLAYED.name)
		val colorCodeGuide = booleanPreference("guide-colorcodedbackgrounds", false)
		val favsAtTop = booleanPreference("livetv-favoritechannelsattop", true)
		val showHDIndicator = booleanPreference("guide-indicator-hd", false)
		val showLiveIndicator = booleanPreference("guide-indicator-live", true)
		val showNewIndicator = booleanPreference("guide-indicator-new", false)
		val showPremiereIndicator = booleanPreference("guide-indicator-premiere", true)
		val showRepeatIndicator = booleanPreference("guide-indicator-repeat", false)
	}
}
