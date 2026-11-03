package org.khylo.tv.core.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import org.khylo.tv.core.auth.model.ApiClientErrorLoginState
import org.khylo.tv.core.auth.model.AuthenticateMethod
import org.khylo.tv.core.auth.model.AuthenticatedState
import org.khylo.tv.core.auth.model.AuthenticatingState
import org.khylo.tv.core.auth.model.AuthenticationStoreUser
import org.khylo.tv.core.auth.model.AutomaticAuthenticateMethod
import org.khylo.tv.core.auth.model.CredentialAuthenticateMethod
import org.khylo.tv.core.auth.model.LoginState
import org.khylo.tv.core.auth.model.PrivateUser
import org.khylo.tv.core.auth.model.QuickConnectAuthenticateMethod
import org.khylo.tv.core.auth.model.RequireSignInState
import org.khylo.tv.core.auth.model.Server
import org.khylo.tv.core.auth.model.ServerUnavailableState
import org.khylo.tv.core.auth.model.ServerVersionNotSupported
import org.khylo.tv.core.auth.model.User
import org.khylo.tv.core.auth.store.AuthenticationPreferences
import org.khylo.tv.core.auth.store.AuthenticationStore
import org.khylo.tv.core.util.apiclient.KhyloImage
import org.khylo.tv.core.util.apiclient.KhyloImageSource
import org.khylo.tv.core.util.apiclient.getUrl
import org.khylo.tv.core.util.apiclient.primaryImage
import org.khylo.tv.core.util.sdk.forUser
import org.khylo.sdk.KhyloSdk
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.exception.ApiClientException
import org.khylo.sdk.api.client.exception.TimeoutException
import org.khylo.sdk.api.client.extensions.authenticateUserByName
import org.khylo.sdk.api.client.extensions.authenticateWithQuickConnect
import org.khylo.sdk.api.client.extensions.userApi
import org.khylo.sdk.model.DeviceInfo
import org.khylo.sdk.model.api.AuthenticationResult
import org.khylo.sdk.model.api.ImageType
import org.khylo.sdk.model.api.UserDto
import timber.log.Timber
import java.time.Instant

/**
 * Repository to manage authentication of the user in the app.
 */
interface AuthenticationRepository {
	fun authenticate(server: Server, method: AuthenticateMethod): Flow<LoginState>
	fun logout(user: User): Boolean
	fun getUserImageUrl(server: Server, user: User): String?
}

class AuthenticationRepositoryImpl(
	private val sdk: KhyloSdk,
	private val sessionRepository: SessionRepository,
	private val authenticationStore: AuthenticationStore,
	private val userApiClient: ApiClient,
	private val authenticationPreferences: AuthenticationPreferences,
	private val defaultDeviceInfo: DeviceInfo,
) : AuthenticationRepository {
	override fun authenticate(server: Server, method: AuthenticateMethod): Flow<LoginState> {
		return when (method) {
			is AutomaticAuthenticateMethod -> authenticateAutomatic(server, method.user)
			is CredentialAuthenticateMethod -> authenticateCredential(server, method.username, method.password)
			is QuickConnectAuthenticateMethod -> authenticateQuickConnect(server, method.secret)
			is org.khylo.tv.core.auth.model.AuthenticationResultMethod -> authenticateAuthenticationResult(server, method.result)
		}
	}

	private fun authenticateAutomatic(server: Server, user: User): Flow<LoginState> {
		Timber.i("Authenticating user ${user.id}")

		// Automatic logic is disabled when the always authenticate preference is enabled
		if (authenticationPreferences[AuthenticationPreferences.alwaysAuthenticate]) return flowOf(RequireSignInState)

		val authStoreUser = authenticationStore.getUser(server.id, user.id)
		// Try login with access token
		return if (authStoreUser?.accessToken != null) authenticateToken(server, user.withToken(authStoreUser.accessToken))
		// Require login
		else flowOf(RequireSignInState)
	}

	private fun authenticateCredential(server: Server, username: String, password: String) = flow {
		val api = sdk.createApi(server.address, deviceInfo = defaultDeviceInfo.forUser(username))
		val result = try {
			val response = api.userApi.authenticateUserByName(username, password)
			response.content
		} catch (err: TimeoutException) {
			Timber.e(err, "Failed to connect to server trying to sign in $username")
			emit(ServerUnavailableState)
			return@flow
		} catch (err: ApiClientException) {
			Timber.e(err, "Unable to sign in as $username")
			emit(ApiClientErrorLoginState(err))
			return@flow
		}

		emitAll(authenticateAuthenticationResult(server, result))
	}.flowOn(Dispatchers.IO)

	private fun authenticateQuickConnect(server: Server, secret: String) = flow {
		val api = sdk.createApi(server.address, deviceInfo = defaultDeviceInfo)
		val result = try {
			val response = api.userApi.authenticateWithQuickConnect(secret)
			response.content
		} catch (err: TimeoutException) {
			Timber.e(err, "Failed to connect to server")
			emit(ServerUnavailableState)
			return@flow
		} catch (err: ApiClientException) {
			Timber.e(err, "Unable to sign in with Quick Connect secret")
			emit(ApiClientErrorLoginState(err))
			return@flow
		}

		emitAll(authenticateAuthenticationResult(server, result))
	}.flowOn(Dispatchers.IO)

	private fun authenticateAuthenticationResult(server: Server, result: AuthenticationResult) = flow {
		val accessToken = result.accessToken ?: return@flow emit(RequireSignInState)
		val userInfo = result.user ?: return@flow emit(RequireSignInState)
		val user = PrivateUser(
			id = userInfo.id,
			serverId = server.id,
			name = userInfo.name!!,
			accessToken = result.accessToken,
			imageTag = userInfo.primaryImage?.tag,
			lastUsed = Instant.now().toEpochMilli(),
		)

		authenticateFinish(server, userInfo, accessToken)
		val success = setActiveSession(user, server)
		if (success) {
			emit(AuthenticatedState)
		} else {
			Timber.w("Failed to set active session after authenticating")
			if (!server.versionSupported) emit(ServerVersionNotSupported(server))
			else emit(RequireSignInState)
		}
	}.flowOn(Dispatchers.IO)

	private fun authenticateToken(server: Server, user: User) = flow {
		emit(AuthenticatingState)

		val success = setActiveSession(user, server)
		if (!success) {
			if (!server.versionSupported) emit(ServerVersionNotSupported(server))
			else emit(RequireSignInState)
		} else try {
			// Update user info
			val userInfo by userApiClient.userApi.getCurrentUser()
			authenticateFinish(server, userInfo, user.accessToken.orEmpty())
			emit(AuthenticatedState)
		} catch (err: TimeoutException) {
			Timber.e(err, "Failed to connect to server")
			emit(ServerUnavailableState)
			return@flow
		} catch (err: ApiClientException) {
			Timber.e(err, "Unable to get current user data")
			emit(ApiClientErrorLoginState(err))
		}
	}.flowOn(Dispatchers.IO)

	private suspend fun authenticateFinish(server: Server, userInfo: UserDto, accessToken: String) {
		val currentUser = authenticationStore.getUser(server.id, userInfo.id)

		val updatedUser = currentUser?.copy(
			name = userInfo.name!!,
			lastUsed = Instant.now().toEpochMilli(),
			imageTag = userInfo.primaryImage?.tag,
			accessToken = accessToken,
		) ?: AuthenticationStoreUser(
			name = userInfo.name!!,
			imageTag = userInfo.primaryImage?.tag,
			accessToken = accessToken,
		)
		authenticationStore.putUser(server.id, userInfo.id, updatedUser)
	}

	private suspend fun setActiveSession(user: User, server: Server): Boolean {
		val authenticated = sessionRepository.switchCurrentSession(server.id, user.id)

		if (authenticated) {
			// Update last use in store
			authenticationStore.getServer(server.id)?.let { storedServer ->
				authenticationStore.putServer(server.id, storedServer.copy(lastUsed = Instant.now().toEpochMilli()))
			}

			authenticationStore.getUser(server.id, user.id)?.let { storedUser ->
				authenticationStore.putUser(server.id, user.id, storedUser.copy(lastUsed = Instant.now().toEpochMilli()))
			}
		}

		return authenticated
	}

	override fun logout(user: User): Boolean {
		val authStoreUser = authenticationStore
			.getUser(user.serverId, user.id)
			?.copy(accessToken = null)

		return if (authStoreUser != null) authenticationStore.putUser(user.serverId, user.id, authStoreUser)
		else false
	}

	override fun getUserImageUrl(server: Server, user: User): String? = user.imageTag?.let { primaryImageTag ->
		KhyloImage(
			item = user.id,
			source = KhyloImageSource.USER,
			type = ImageType.PRIMARY,
			tag = primaryImageTag,
			blurHash = null,
			aspectRatio = null,
			index = null
		)
	}?.getUrl(sdk.createApi(server.address))
}
