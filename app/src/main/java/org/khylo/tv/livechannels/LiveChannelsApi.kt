package org.khylo.tv.livechannels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.khylo.sdk.api.client.ApiClient
import java.time.Instant

@Serializable
data class LiveChannelProgram(
	@SerialName("ItemId") val itemId: String,
	@SerialName("Name") val name: String = "",
	@SerialName("SeriesName") val seriesName: String? = null,
	@SerialName("SeasonNumber") val seasonNumber: Int? = null,
	@SerialName("EpisodeNumber") val episodeNumber: Int? = null,
	@SerialName("ProductionYear") val productionYear: Int? = null,
	@SerialName("Overview") val overview: String? = null,
	@SerialName("StartUtc") val startUtc: String,
	@SerialName("EndUtc") val endUtc: String,
) {
	val start: Instant get() = parseUtc(startUtc)
	val end: Instant get() = parseUtc(endUtc)

	/** "Show · S2 E4 · Episode" or "Movie (1999)". */
	val title: String get() = seriesName ?: name
	val detail: String?
		get() = when {
			seriesName != null -> listOfNotNull(
				seasonNumber?.let { s -> episodeNumber?.let { e -> "S$s E$e" } },
				name,
			).joinToString(" · ")
			productionYear != null -> productionYear.toString()
			else -> null
		}
}

@Serializable
data class LiveChannel(
	@SerialName("Id") val id: String,
	@SerialName("Number") val number: Int = 0,
	@SerialName("Name") val name: String = "",
	@SerialName("Category") val category: String = "",
	@SerialName("Description") val description: String = "",
	@SerialName("Programs") val programs: List<LiveChannelProgram> = emptyList(),
)

@Serializable
data class LiveChannelTune(
	@SerialName("Current") val current: LiveChannelProgram? = null,
	@SerialName("StartPositionTicks") val startPositionTicks: Long = 0,
)

@Serializable
private data class LiveChannelStatus(@SerialName("Enabled") val enabled: Boolean = false)

// Server times are UTC; some come without the trailing Z
private fun parseUtc(value: String): Instant = Instant.parse(if (value.endsWith("Z") || value.contains('+')) value else value + "Z")

private val json = Json { ignoreUnknownKeys = true }

/** Khylo's Live Channels: always-on channels the server programs from your own library. */
class LiveChannelsApi(private val api: ApiClient, private val http: OkHttpClient) {
	private fun request(path: String) = Request.Builder()
		.url("${api.baseUrl}/LiveChannels$path")
		.header("Authorization", "MediaBrowser Token=\"${api.accessToken}\"")

	private suspend fun <T> call(request: Request, read: (String) -> T): T = withContext(Dispatchers.IO) {
		http.newCall(request).execute().use { response ->
			check(response.isSuccessful) { "HTTP ${response.code}" }
			read(response.body?.string().orEmpty())
		}
	}

	suspend fun isEnabled(): Boolean = runCatching {
		call(request("/Status").get().build()) { json.decodeFromString(LiveChannelStatus.serializer(), it).enabled }
	}.getOrDefault(false)

	suspend fun guide(hours: Int = 4): List<LiveChannel> =
		call(request("/Guide?hours=$hours").get().build()) { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LiveChannel.serializer()), it) }

	/** What's on now, and how far into it this TV should start. */
	suspend fun tune(channelId: String): LiveChannelTune =
		call(request("/$channelId/Tune").post(ByteArray(0).toRequestBody()).build()) { json.decodeFromString(LiveChannelTune.serializer(), it) }

	// Logos are open to everyone, and the image loader signs requests to the server in anyway: no key in the address
	fun logoUrl(channelId: String) = "${api.baseUrl}/LiveChannels/$channelId/Logo"
}

/** Item ids from the server come as 32 hex digits without dashes, which UUID.fromString rejects. */
fun String.toItemId(): java.util.UUID =
	if (length == 32 && !contains('-')) {
		java.util.UUID.fromString("${substring(0, 8)}-${substring(8, 12)}-${substring(12, 16)}-${substring(16, 20)}-${substring(20)}")
	} else {
		java.util.UUID.fromString(this)
	}
