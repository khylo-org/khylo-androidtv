package org.khylo.tv.core.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.khylo.sdk.model.api.ServerDiscoveryInfo
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Finds Khylo servers on the home network: a short broadcast ("who is KhyloServer?") that servers answer with
 * their name and address. Only Khylo servers answer it.
 */
object KhyloDiscovery {
	private const val PORT = 7360
	private const val MESSAGE = "who is KhyloServer?"
	private const val LISTEN_MS = 2_500L

	fun discoverLocalServers(): Flow<ServerDiscoveryInfo> = channelFlow {
		val socket = DatagramSocket().apply {
			broadcast = true
			soTimeout = 400
		}
		try {
			val payload = MESSAGE.toByteArray()
			broadcastAddresses().forEach { address ->
				runCatching { socket.send(DatagramPacket(payload, payload.size, address, PORT)) }
					.onFailure { Timber.d(it, "Couldn't broadcast to %s", address) }
			}

			val seen = mutableSetOf<String>()
			val end = System.currentTimeMillis() + LISTEN_MS
			val buffer = ByteArray(4096)
			while (isActive && System.currentTimeMillis() < end) {
				try {
					val packet = DatagramPacket(buffer, buffer.size)
					socket.receive(packet)
					val json = Json.parseToJsonElement(String(packet.data, 0, packet.length)).jsonObject
					val server = json.toServer() ?: continue
					if (seen.add(server.id)) launch { send(server) }
				} catch (_: SocketTimeoutException) {
					// keep listening until the time is up
				} catch (err: Exception) {
					Timber.d(err, "Ignoring an unreadable discovery reply")
				}
			}
		} finally {
			socket.close()
		}
	}.flowOn(Dispatchers.IO)

	private fun JsonObject.toServer(): ServerDiscoveryInfo? {
		val address = this["Address"]?.jsonPrimitive?.contentOrNull ?: return null
		val id = this["Id"]?.jsonPrimitive?.contentOrNull ?: return null
		val name = this["Name"]?.jsonPrimitive?.contentOrNull ?: "Khylo"
		return ServerDiscoveryInfo(address = address, id = id, name = name, endpointAddress = this["EndpointAddress"]?.jsonPrimitive?.contentOrNull)
	}

	private fun broadcastAddresses(): List<InetAddress> = buildList {
		add(InetAddress.getByName("255.255.255.255"))
		runCatching {
			NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
				.filter { it.isUp && !it.isLoopback }
				.flatMap { it.interfaceAddresses }
				.mapNotNull { it.broadcast }
				.forEach { if (it !in this) add(it) }
		}
	}
}
