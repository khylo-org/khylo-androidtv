package org.khylo.tv.startup

import org.khylo.sdk.discovery.RecommendedServerIssue
import org.khylo.tv.core.auth.model.UnableToConnectState

/**
 * What to tell someone when a server couldn't be added: the reason the address gave (it answered but isn't a Khylo server, it
 * is too old, its secure connection failed), not one sentence for every kind of failure.
 */
internal fun addServerFailureMessage(state: UnableToConnectState, typed: String): String {
	val issues = state.addressCandidates.values.flatten()
	val address = typed.trim()
	val secure = address.startsWith("https://", ignoreCase = true)
	return when {
		issues.any { it is RecommendedServerIssue.InvalidProductName } ->
			"Something answered at that address, but it isn't a Khylo server. Check the address and the port (Khylo uses 8096)."
		issues.any { it is RecommendedServerIssue.UnsupportedServerVersion } ->
			"That Khylo server is too old for Khylo TV. Update the server, then try again."
		secure && issues.any { it is RecommendedServerIssue.SecureConnectionFailed } ->
			"The secure connection to that address failed. The TV only trusts certificates from a public authority, so a self-signed or home-made certificate won't work."
		else ->
			"Couldn't reach a Khylo server there. Check the address, e.g. 192.168.1.20:8096." +
				// Plain http is only for the home network, so a name that isn't https gets the reason too
				if (address.any { it.isLetter() } && !secure) {
					" A server outside your home network needs https://, for example https://khylo.example.com."
				} else {
					""
				}
	}
}
