package org.khylo.tv.data

import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.displayPreferencesApi
import java.util.UUID

/**
 * Shows the viewer took off Up next. Saved with their profile on the server (display preferences),
 * so it follows them to every TV. A show comes back on its own as soon as they play one of its
 * episodes again.
 */
internal object UpNextHidden {
	private const val PREFS_ID = "khylo"
	private const val CLIENT = "khylo"
	private const val KEY = "upNextHidden"

	suspend fun load(api: ApiClient): Set<UUID> = runCatching {
		api.displayPreferencesApi.getDisplayPreferences(displayPreferencesId = PREFS_ID, client = CLIENT).content
			.customPrefs[KEY].orEmpty()
			.split(',')
			.mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }
			.toSet()
	}.getOrDefault(emptySet())

	suspend fun hide(api: ApiClient, seriesId: UUID) = change(api) { it + seriesId }

	suspend fun show(api: ApiClient, seriesId: UUID) = change(api) { it - seriesId }

	private suspend fun change(api: ApiClient, update: (Set<UUID>) -> Set<UUID>) {
		val prefs = api.displayPreferencesApi.getDisplayPreferences(displayPreferencesId = PREFS_ID, client = CLIENT).content
		val current = prefs.customPrefs[KEY].orEmpty().split(',').mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }.toSet()
		val updated = update(current)
		if (updated == current) return
		api.displayPreferencesApi.updateDisplayPreferences(
			displayPreferencesId = PREFS_ID,
			client = CLIENT,
			data = prefs.copy(customPrefs = prefs.customPrefs + (KEY to updated.joinToString(","))),
		)
	}
}
