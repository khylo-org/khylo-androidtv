package org.khylo.tv.data

import android.content.Context
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Looks up server names as usual, and remembers the addresses that worked. When a name can't be looked
 * up later, the remembered address is used instead. Home-only names like khylo.mydomain.internal are
 * answered by the router, and a TV box using its own DNS (Google's, say) or a phone with Private DNS
 * can't see them; this keeps Khylo connecting anyway once it has reached the server by name.
 */
class RememberingDns(context: Context) : Dns {
	private val prefs = context.applicationContext.getSharedPreferences("khylo_dns", Context.MODE_PRIVATE)

	override fun lookup(hostname: String): List<InetAddress> {
		try {
			val found = Dns.SYSTEM.lookup(hostname)
			// Only names are worth remembering; plain IP addresses always "resolve"
			if (found.isNotEmpty() && !isIpAddress(hostname)) {
				val addresses = found.joinToString(",") { it.hostAddress.orEmpty() }
				if (prefs.getString(hostname, null) != addresses) prefs.edit().putString(hostname, addresses).apply()
			}
			return found
		} catch (failure: UnknownHostException) {
			val remembered = prefs.getString(hostname, null)
				?.split(',')
				?.filter { it.isNotBlank() }
				?.mapNotNull { runCatching { InetAddress.getByAddress(hostname, InetAddress.getByName(it).address) }.getOrNull() }
				.orEmpty()
			if (remembered.isEmpty()) throw failure
			android.util.Log.i("KhyloDns", "$hostname can't be looked up here; using the address it had last time")
			return remembered
		}
	}

	private fun isIpAddress(hostname: String) = hostname.all { it.isDigit() || it == '.' } || hostname.contains(':')
}
