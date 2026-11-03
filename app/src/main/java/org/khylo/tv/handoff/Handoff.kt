package org.khylo.tv.handoff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.khylo.sdk.api.client.ApiClient
import java.util.UUID

/** What another of the viewer's devices asked this one to play (a handoff coming in). */
data class IncomingPlay(
	val itemId: UUID,
	val startPositionMs: Long?,
	val mediaSourceId: String?,
	val audioStreamIndex: Int?,
	val subtitleStreamIndex: Int?,
)

/**
 * Messages from the server that need the screens to react: "play this here" and "stop playing" (the other half of
 * a handoff). The socket handler posts them; the shell and the player listen.
 */
object HandoffInbox {
	private val plays = MutableSharedFlow<IncomingPlay>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
	private val stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

	val incomingPlays: SharedFlow<IncomingPlay> = plays.asSharedFlow()
	val stopRequests: SharedFlow<Unit> = stops.asSharedFlow()

	fun play(request: IncomingPlay) {
		plays.tryEmit(request)
	}

	fun stop() {
		stops.tryEmit(Unit)
	}
}

/** One of the viewer's other devices, as the server lists it (Khylo/Handoff/Devices). */
@Serializable
data class HandoffDevice(
	val SessionId: String,
	val DeviceName: String? = null,
	val Client: String? = null,
	val DeviceType: String? = null,
	val IsPlaying: Boolean = false,
	val NowPlayingName: String? = null,
	val NowPlayingSeriesName: String? = null,
	val PositionTicks: Long? = null,
	val RunTimeTicks: Long? = null,
) {
	val title: String get() = DeviceName ?: Client ?: "Your device"

	val playing: String?
		get() = listOfNotNull(NowPlayingSeriesName, NowPlayingName).joinToString(" · ").ifEmpty { null }
}

/** Talks to the server's handoff API as the signed-in person. */
class HandoffApi(private val api: ApiClient, private val http: OkHttpClient) {
	private val json = Json { ignoreUnknownKeys = true }
	private val jsonType = "application/json".toMediaType()

	private fun request(path: String) = Request.Builder()
		.url("${api.baseUrl}/Khylo/Handoff$path")
		.header("Authorization", "MediaBrowser Token=\"${api.accessToken}\"")

	suspend fun devices(): List<HandoffDevice> = withContext(Dispatchers.IO) {
		http.newCall(request("/Devices").get().build()).execute().use { response ->
			check(response.isSuccessful) { "HTTP ${response.code}" }
			json.decodeFromString<List<HandoffDevice>>(response.body?.string().orEmpty())
		}
	}

	/** Sends what is playing on this TV to [toSessionId], continuing from [positionMs]. */
	suspend fun sendTo(toSessionId: String, positionMs: Long): Boolean = withContext(Dispatchers.IO) {
		val body = """{"ToSessionId":"$toSessionId","PositionTicks":${positionMs * 10_000}}""".toRequestBody(jsonType)
		http.newCall(request("").post(body).build()).execute().use { it.isSuccessful }
	}

	/** Brings what [fromSessionId] is playing to this TV. */
	suspend fun bringHere(fromSessionId: String): Boolean = withContext(Dispatchers.IO) {
		val body = """{"FromSessionId":"$fromSessionId"}""".toRequestBody(jsonType)
		http.newCall(request("").post(body).build()).execute().use { it.isSuccessful }
	}
}
