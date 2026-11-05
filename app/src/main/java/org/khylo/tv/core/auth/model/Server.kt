package org.khylo.tv.core.auth.model

import org.khylo.tv.core.auth.repository.ServerRepository
import org.khylo.sdk.model.ServerVersion
import java.time.Instant
import java.util.UUID

/**
 * Server model to use locally in place of ServerInfo model in ApiClient.
 */
data class Server(
	var id: UUID,
	var name: String,
	var address: String,
	val version: String? = null,
	val loginDisclaimer: String? = null,
	val splashscreenEnabled: Boolean = false,
	val setupCompleted: Boolean = true,
	var dateLastAccessed: Instant = Instant.MIN,
) {
	// Khylo servers report their build name (DEV-V26.11.05.05), not a number; they count as the version the API is built on
	val serverVersion = version?.let { if (it.startsWith("DEV-", ignoreCase = true)) ServerVersion(13, 0, 0) else ServerVersion.fromString(it) }
	val versionSupported = serverVersion != null && serverVersion >= ServerRepository.minimumServerVersion

	operator fun compareTo(other: ServerVersion): Int = serverVersion?.compareTo(other) ?: -1

	override fun equals(other: Any?) = other is Server
		&& id == other.id
		&& address == other.address

	override fun hashCode(): Int {
		var result = id.hashCode()
		result = 31 * result + address.hashCode()
		return result
	}
}
