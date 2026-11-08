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

/** What another of the viewer's devices asked the player to do (a mirror following along). */
sealed interface RemoteCommand {
	data object Pause : RemoteCommand

	data object Unpause : RemoteCommand

	data class SeekTo(val positionMs: Long) : RemoteCommand
}

/**
 * Messages from the server that need the screens to react: "play this here" and "stop playing" (the other half of
 * a handoff). The socket handler posts them; the shell and the player listen.
 */
object HandoffInbox {
	private val plays = MutableSharedFlow<IncomingPlay>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
	private val stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
	private val commandFlow = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

	val incomingPlays: SharedFlow<IncomingPlay> = plays.asSharedFlow()
	val stopRequests: SharedFlow<Unit> = stops.asSharedFlow()
	val commands: SharedFlow<RemoteCommand> = commandFlow.asSharedFlow()

	// A mirror is following a Khylo TV channel: this TV should tune to it
	private val channelTunes = MutableSharedFlow<String>(extraBufferCapacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
	val tuneRequests: SharedFlow<String> = channelTunes.asSharedFlow()

	fun tuneChannel(channelId: String) {
		channelTunes.tryEmit(channelId)
	}

	fun command(command: RemoteCommand) {
		commandFlow.tryEmit(command)
	}

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

/** A device that follows another in a mirror. */
@Serializable
data class MirrorTarget(val SessionId: String, val DeviceName: String? = null)

/** A running mirror: the device being followed and the devices following it (Khylo/Mirror). */
@Serializable
data class MirrorInfo(
	val SourceSessionId: String,
	val SourceName: String? = null,
	val Targets: List<MirrorTarget> = emptyList(),
)

/** Where the TV this one follows is right now (Khylo/Mirror/Reference). */
@Serializable
data class MirrorReference(val ItemId: String, val PositionTicks: Long, val IsPaused: Boolean = false)

/** Talks to the server's mirror API as the signed-in person. */
class MirrorApi(private val api: ApiClient, private val http: OkHttpClient) {
	private val json = Json { ignoreUnknownKeys = true }
	private val jsonType = "application/json".toMediaType()

	private fun request(query: String = "") = Request.Builder()
		.url("${api.baseUrl}/Khylo/Mirror$query")
		.header("Authorization", "MediaBrowser Token=\"${api.accessToken}\"")

	suspend fun mirrors(): List<MirrorInfo> = withContext(Dispatchers.IO) {
		http.newCall(request().get().build()).execute().use { response ->
			check(response.isSuccessful) { "HTTP ${response.code}" }
			json.decodeFromString<List<MirrorInfo>>(response.body?.string().orEmpty())
		}
	}

	/** Where the TV this one follows is, or null when this TV is not following something that is playing. */
	suspend fun reference(): MirrorReference? = withContext(Dispatchers.IO) {
		http.newCall(request("/Reference").get().build()).execute().use { response ->
			if (!response.isSuccessful) null else json.decodeFromString<MirrorReference>(response.body?.string().orEmpty())
		}
	}

	/**
	 * Makes [targetSessionId] follow this TV. Returns null when it worked, otherwise a plain sentence saying why not, so the
	 * person is told the real reason instead of a vague "couldn't reach".
	 */
	suspend fun mirrorTo(targetSessionId: String): String? = withContext(Dispatchers.IO) {
		val body = """{"TargetSessionIds":["$targetSessionId"]}""".toRequestBody(jsonType)
		runCatching {
			http.newCall(request().post(body).build()).execute().use { response ->
				when {
					response.isSuccessful -> null
					// An older server has no Mirror at all
					response.code == 404 -> "Your Khylo Server needs updating to use Mirror"
					response.code == 400 -> response.body?.string()?.trim('"')?.ifBlank { null } ?: "That device can't be mirrored right now"
					else -> "The server couldn't start the mirror (error ${response.code})"
				}
			}
		}.getOrElse { "Couldn't reach your Khylo Server" }
	}

	/** Says which Khylo TV channel this TV is watching (null when it leaves), so a mirror can follow it. */
	suspend fun channel(channelId: String?): Boolean = withContext(Dispatchers.IO) {
		val body = (if (channelId == null) "{}" else """{"ChannelId":"$channelId"}""").toRequestBody(jsonType)
		runCatching { http.newCall(request("/Channel").post(body).build()).execute().use { it.isSuccessful } }.getOrDefault(false)
	}

	/** Stops [sessionId] following; with no id, ends this TV's mirror. */
	suspend fun stop(sessionId: String? = null): Boolean = withContext(Dispatchers.IO) {
		val query = sessionId?.let { "?sessionId=$it" }.orEmpty()
		http.newCall(request(query).delete().build()).execute().use { it.isSuccessful }
	}
}
