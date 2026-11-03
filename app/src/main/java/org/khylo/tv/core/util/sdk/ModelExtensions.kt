@file:JvmName("ModelUtils")

package org.khylo.tv.core.util.sdk

import org.khylo.tv.core.auth.model.PublicUser
import org.khylo.tv.core.auth.model.Server
import org.khylo.tv.core.util.apiclient.primaryImage
import org.khylo.sdk.model.api.ServerDiscoveryInfo
import org.khylo.sdk.model.api.UserDto
import org.khylo.sdk.model.serializer.toUUID
import org.khylo.sdk.model.serializer.toUUIDOrNull

fun ServerDiscoveryInfo.toServer(): Server = Server(
	id = id.toUUID(),
	name = name,
	address = address,
)

fun UserDto.toPublicUser(): PublicUser? {
	return PublicUser(
		id = id,
		name = name ?: return null,
		serverId = serverId?.toUUIDOrNull() ?: return null,
		accessToken = null,
		imageTag = primaryImage?.tag
	)
}
