package org.khylo.tv.core.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.khylo.tv.core.auth.model.Server
import org.khylo.tv.core.auth.store.AuthenticationPreferences
import org.khylo.tv.core.auth.store.AuthenticationStore
import org.khylo.tv.core.preference.PreferencesRepository
import org.khylo.tv.core.preference.TelemetryPreferences
import org.khylo.tv.core.preference.constant.UserSelectBehavior.DISABLED
import org.khylo.tv.core.preference.constant.UserSelectBehavior.LAST_USER
import org.khylo.tv.core.preference.constant.UserSelectBehavior.SPECIFIC_USER
import org.khylo.tv.core.util.sdk.forUser
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.exception.ApiClientException
import org.khylo.sdk.api.client.extensions.clientLogApi
import org.khylo.sdk.api.client.extensions.userApi
import org.khylo.sdk.model.DeviceInfo
import org.khylo.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

data class Session(
	val userId: UUID,
	val serverId: UUID,
	val accessToken: String,
)

enum class SessionRepositoryState {
	READY,
	RESTORING_SESSION,
	SWITCHING_SESSION,
}

interface SessionRepository {
	val currentSession: StateFlow<Session?>
	val state: StateFlow<SessionRepositoryState>

	suspend fun restoreSession(destroyOnly: Boolean)
	suspend fun switchCurrentSession(serverId: UUID, userId: UUID): Boolean
	fun destroyCurrentSession()
}

class SessionRepositoryImpl(
	private val authenticationPreferences: AuthenticationPreferences,
	private val authenticationStore: AuthenticationStore,
	private val userApiClient: ApiClient,
	private val preferencesRepository: PreferencesRepository,
	private val defaultDeviceInfo: DeviceInfo,
	private val userRepository: UserRepository,
	private val serverRepository: ServerRepository,
	private val telemetryPreferences: TelemetryPreferences,
) : SessionRepository {
	private val currentSessionMutex = Mutex()
	private val _currentSession = MutableStateFlow<Session?>(null)
	override val currentSession = _currentSession.asStateFlow()
	private val _state = MutableStateFlow(SessionRepositoryState.READY)
	override val state = _state.asStateFlow()

	override suspend fun restoreSession(destroyOnly: Boolean): Unit = withContext(NonCancellable) {
		currentSessionMutex.withLock {
			Timber.i("Restoring session")

			_state.value = SessionRepositoryState.RESTORING_SESSION
			try {
				val alwaysAuthenticate = authenticationPreferences[AuthenticationPreferences.alwaysAuthenticate]
				val autoLoginBehavior = authenticationPreferences[AuthenticationPreferences.autoLoginUserBehavior]

				when {
					alwaysAuthenticate -> destroyCurrentSession()
					autoLoginBehavior == DISABLED -> destroyCurrentSession()
					autoLoginBehavior == LAST_USER && !destroyOnly -> setCurrentSession(createLastUserSession())
					autoLoginBehavior == SPECIFIC_USER && !destroyOnly -> {
						val serverId = authenticationPreferences[AuthenticationPreferences.autoLoginServerId].toUUIDOrNull()
						val userId = authenticationPreferences[AuthenticationPreferences.autoLoginUserId].toUUIDOrNull()
						if (serverId != null && userId != null) setCurrentSession(createUserSession(serverId, userId))
					}
				}
			} finally {
				_state.value = SessionRepositoryState.READY
			}
		}
	}

	// A switch always runs to the end: the screen that asked for it (e.g. Who's watching?) closes
	// while the app reloads, and a half-finished switch would leave the app on its loading screen
	override suspend fun switchCurrentSession(serverId: UUID, userId: UUID): Boolean = withContext(NonCancellable) {
		// No change in user - don't switch
		if (currentSession.value?.userId == userId) {
			Timber.d("Current session user is the same as the requested user")
			return@withContext false
		}

		_state.value = SessionRepositoryState.SWITCHING_SESSION
		Timber.i("Switching current session to user $userId")

		try {
			val session = createUserSession(serverId, userId)
			if (session == null) {
				Timber.w("Could not switch to non-existing session for user $userId")
				return@withContext false
			}

			setCurrentSession(session)
		} finally {
			_state.value = SessionRepositoryState.READY
		}
	}

	override fun destroyCurrentSession() {
		Timber.i("Destroying current session")

		userRepository.setCurrentUser(null)
		serverRepository.setCurrentServer(null)
		_currentSession.value = null
		_state.value = SessionRepositoryState.READY
	}

	private suspend fun setCurrentSession(session: Session?): Boolean {
		var server: Server? = null

		if (session != null) {
			// No change in session - don't switch
			if (currentSession.value?.userId == session.userId) return true

			// Update last active user
			authenticationPreferences[AuthenticationPreferences.lastServerId] = session.serverId.toString()
			authenticationPreferences[AuthenticationPreferences.lastUserId] = session.userId.toString()

			// Check if server version is supported
			server = serverRepository.getServer(session.serverId, true)
			if (server == null || !server.versionSupported) return false
		}

		// Update session after binding the apiclient settings
		val deviceInfo = session?.let { defaultDeviceInfo.forUser(it.userId) } ?: defaultDeviceInfo
		Timber.i("Updating current session. userId=${session?.userId} server=${server?.serverVersion}")

		val applied = userApiClient.applySession(session, deviceInfo)
		if (applied && session != null) {
			try {
				val user = withContext(Dispatchers.IO) {
					userApiClient.userApi.getCurrentUser().content
				}
				userRepository.setCurrentUser(user)
				serverRepository.setCurrentServer(server)
			} catch (err: ApiClientException) {
				Timber.e(err, "Unable to authenticate: bad response when getting user info")
				destroyCurrentSession()
				return false
			}

			// Update crash reporting URL
			val crashReportUrl = userApiClient.clientLogApi.logFileUrl()
			telemetryPreferences[TelemetryPreferences.crashReportUrl] = crashReportUrl
			telemetryPreferences[TelemetryPreferences.crashReportToken] = session.accessToken
		} else {
			userRepository.setCurrentUser(null)
			serverRepository.setCurrentServer(null)
		}
		preferencesRepository.onSessionChanged()
		_currentSession.value = session

		return true
	}

	private fun createLastUserSession(): Session? {
		val lastUserId = authenticationPreferences[AuthenticationPreferences.lastUserId].toUUIDOrNull()
		val lastServerId = authenticationPreferences[AuthenticationPreferences.lastServerId].toUUIDOrNull()

		return if (lastUserId != null && lastServerId != null) createUserSession(lastServerId, lastUserId)
		else null
	}

	private fun createUserSession(serverId: UUID, userId: UUID): Session? {
		val account = authenticationStore.getUser(serverId, userId)
		if (account?.accessToken == null) return null

		return Session(
			userId = userId,
			serverId = serverId,
			accessToken = account.accessToken
		)
	}

	private fun ApiClient.applySession(session: Session?, newDeviceInfo: DeviceInfo = defaultDeviceInfo): Boolean {
		if (session == null) {
			update(
				baseUrl = null,
				accessToken = null,
				deviceInfo = newDeviceInfo,
			)
		} else {
			val server = authenticationStore.getServer(session.serverId)
				?: return false

			update(
				baseUrl = server.address,
				accessToken = session.accessToken,
				deviceInfo = newDeviceInfo,
			)
		}

		return true
	}
}
