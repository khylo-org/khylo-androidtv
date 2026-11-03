package org.khylo.tv.data

import org.khylo.sdk.model.api.BaseItemDto
import java.util.UUID

/**
 * Titles whose watched state changed (played, or marked watched / not watched) since screens
 * loaded them. Screens that keep their titles while you're away (library grids) refresh these
 * when you come back, so their check marks are right.
 */
object WatchStateChanges {
	private val ids = mutableSetOf<UUID>()

	fun changed(item: BaseItemDto) {
		synchronized(ids) {
			ids += item.id
			item.seriesId?.let { ids += it }
			item.seasonId?.let { ids += it }
		}
	}

	fun contains(id: UUID): Boolean = synchronized(ids) { id in ids }

	fun clear() = synchronized(ids) { ids.clear() }
}
