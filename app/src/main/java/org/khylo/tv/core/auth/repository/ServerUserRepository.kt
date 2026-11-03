package org.khylo.tv.core.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.khylo.tv.core.auth.model.PrivateUser
import org.khylo.tv.core.auth.model.PublicUser
import org.khylo.tv.core.auth.model.Server
import org.khylo.tv.core.auth.store.AuthenticationStore
import org.khylo.tv.core.util.sdk.toPublicUser
import org.khylo.sdk.KhyloSdk
import org.khylo.sdk.api.client.exception.ApiClientException
import org.khylo.sdk.api.client.extensions.userApi
import org.khylo.sdk.model.api.UserDto
import timber.log.Timber

/**
 * Repository to maintain users for servers.
 * Authentication is done using the [AuthenticationRepository].
 */
interface ServerUserRepository {
	//server
	fun getStoredServerUsers(server: Server): List<PrivateUser>
	suspend fun getPublicServerUsers(server: Server): List<PublicUser>

	fun deleteStoredUser(user: PrivateUser)
}

class ServerUserRepositoryImpl(
	private val sdk: KhyloSdk,
	private val authenticationStore: AuthenticationStore,
) : ServerUserRepository {
	override fun getStoredServerUsers(server: Server) = authenticationStore.getUsers(server.id)
		?.mapNotNull { (userId, userInfo) ->
			val authInfo = authenticationStore.getUser(server.id, userId)
			PrivateUser(
				id = userId,
				serverId = server.id,
				name = userInfo.name,
				accessToken = authInfo?.accessToken,
				imageTag = userInfo.imageTag,
				lastUsed = userInfo.lastUsed,
			)
		}
		?.sortedWith(compareByDescending<PrivateUser> { it.lastUsed }.thenBy { it.name })
		.orEmpty()

	override suspend fun getPublicServerUsers(server: Server): List<PublicUser> {
		// Create a fresh API because the shared one might be authenticated for a different server
		val api = sdk.createApi(server.address)

		return try {
			val users = withContext(Dispatchers.IO) {
				api.userApi.getPublicUsers().content
			}
			users.mapNotNull(UserDto::toPublicUser)
		} catch (err: ApiClientException) {
			Timber.e(err, "Unable to retrieve public users")

			emptyList()
		}
	}

	override fun deleteStoredUser(user: PrivateUser) {
		// Remove user info from store
		authenticationStore.removeUser(user.serverId, user.id)
	}
}
