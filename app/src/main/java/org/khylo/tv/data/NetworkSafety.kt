package org.khylo.tv.data

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress

/**
 * What counts as the home network: this TV itself, the private address ranges every router hands out, the link-local
 * range, and the 100.64.0.0/10 range VPNs such as Tailscale use. Anything else is the internet.
 */
object HomeNetwork {
	fun isHomeAddress(address: InetAddress): Boolean {
		if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) return true
		val bytes = address.address
		return when (address) {
			// fc00::/7, the private range of IPv6
			is Inet6Address -> (bytes[0].toInt() and 0xFE) == 0xFC
			// 100.64.0.0/10
			else -> bytes.size == 4 && (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xC0) == 0x40
		}
	}
}

/**
 * Plain http is only for the home network. Before a request that isn't https is sent, the address the connection
 * really reached is checked: anything outside the home network is refused, so a password or sign-in is never sent in
 * the clear over the internet (also after a redirect). Checked on the connection itself, so it doesn't matter what the
 * address looked like when it was typed.
 */
class CleartextGuard : Interceptor {
	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		if (!request.url.isHttps) {
			val reached = chain.connection()?.socket()?.inetAddress
			if (reached == null || !HomeNetwork.isHomeAddress(reached)) {
				throw IOException("Khylo only connects to a server outside your home network over https.")
			}
		}
		return chain.proceed(request)
	}
}

/** The server this TV is signed in to, and the Authorization header that signs requests to it in. */
class ServerSignIn(val server: HttpUrl, val authorization: String)

/**
 * Signs requests to the server this TV is signed in to, so a sign-in key never has to be put in an address (where it
 * would be kept in logs and could be handed to other apps). Only the same scheme, host and port get it, only when the
 * request doesn't already carry its own, and the key is read for each request, so switching profile is followed.
 */
class ServerAuthInterceptor(private val signedIn: () -> ServerSignIn?) : Interceptor {
	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val session = signedIn()
		if (session == null || request.header("Authorization") != null || !isSameServer(session.server, request.url)) {
			return chain.proceed(request)
		}
		return chain.proceed(request.newBuilder().header("Authorization", session.authorization).build())
	}

	companion object {
		fun isSameServer(server: HttpUrl, url: HttpUrl): Boolean =
			server.scheme == url.scheme && server.host.equals(url.host, ignoreCase = true) && server.port == url.port
	}
}

/** Addresses the server makes for playing something. */
object StreamAddress {
	private val tokenNames = setOf("apikey", "api_key")

	/**
	 * The address without the sign-in key the server puts in it. The players send the key in a header, which also covers
	 * every part of a stream the playlist points to, so the address doesn't need it.
	 */
	fun withoutAccessToken(address: String): String {
		val queryStart = address.indexOf('?')
		if (queryStart < 0) return address
		val queryEnd = address.indexOf('#', queryStart).let { if (it < 0) address.length else it }
		val parts = address.substring(queryStart + 1, queryEnd).split('&').filter { it.isNotEmpty() }
		val kept = parts.filter { it.substringBefore('=').lowercase() !in tokenNames }
		if (kept.size == parts.size) return address
		return address.substring(0, queryStart) + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + address.substring(queryEnd)
	}
}
