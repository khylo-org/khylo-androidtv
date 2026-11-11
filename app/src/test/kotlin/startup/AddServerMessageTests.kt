package org.khylo.tv.startup

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.khylo.sdk.api.client.exception.SecureConnectionException
import org.khylo.sdk.api.client.exception.TimeoutException
import org.khylo.sdk.discovery.RecommendedServerIssue
import org.khylo.sdk.model.ServerVersion
import org.khylo.tv.core.auth.model.UnableToConnectState

class AddServerMessageTests : FunSpec({
	fun failed(vararg issues: RecommendedServerIssue) = UnableToConnectState(mapOf("http://192.168.1.5:8096" to issues.toList()))

	test("an address that answered as something else says so") {
		addServerFailureMessage(failed(RecommendedServerIssue.InvalidProductName("Other Server")), "192.168.1.5") shouldContain "isn't a Khylo server"
	}

	test("a server that is too old says to update it") {
		addServerFailureMessage(failed(RecommendedServerIssue.UnsupportedServerVersion(ServerVersion(10, 1, 0))), "192.168.1.5") shouldContain "too old"
	}

	test("a typed https address whose secure connection failed names the certificate") {
		addServerFailureMessage(failed(RecommendedServerIssue.SecureConnectionFailed(SecureConnectionException("bad"))), "https://khylo.example.com") shouldContain "certificate"
	}

	test("an address nothing answered at gets the general sentence, with the https hint for a name") {
		val timedOut = RecommendedServerIssue.ServerUnreachable(TimeoutException("slow"))
		addServerFailureMessage(failed(timedOut), "192.168.1.5") shouldContain "Couldn't reach a Khylo server there"
		addServerFailureMessage(failed(timedOut), "192.168.1.5") shouldNotContain "https://"
		addServerFailureMessage(failed(timedOut), "khylo.example.com") shouldContain "needs https://"
		addServerFailureMessage(failed(timedOut), "https://khylo.example.com") shouldNotContain "needs https://"
	}
})
