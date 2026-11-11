package org.khylo.tv.core.auth

import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import org.khylo.sdk.KhyloSdk
import org.khylo.sdk.KhyloSdkOptions
import org.khylo.sdk.api.okhttp.OkHttpFactory
import org.khylo.sdk.discovery.RecommendedServerIssue
import org.khylo.sdk.model.ClientInfo
import org.khylo.sdk.model.DeviceInfo
import org.khylo.sdk.model.ServerVersion
import org.khylo.tv.core.auth.model.ConnectedState
import org.khylo.tv.core.auth.model.UnableToConnectState
import org.khylo.tv.core.auth.repository.ServerRepository
import org.khylo.tv.core.auth.repository.ServerRepositoryImpl
import org.khylo.tv.core.auth.store.AuthenticationStore
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

// Typing a server's address goes through the real check of what answers there; a stand-in server answers the way a Khylo server does
class AddServerTests : FunSpec({
	// What a Khylo server answers on /System/Info/Public: its version is its build name, not a number
	fun publicInfo(version: String, product: String = "Khylo Server") =
		"""{"LocalAddress":"http://127.0.0.1:8096","HomeAddress":"http://10.1.2.3:8096","ServerName":"Khylo","Version":"$version","KhyloBuild":"$version","KhyloVersion":"$version","ProductName":"$product","OperatingSystem":"","Id":"b50bd8aa27b241c2a358d7375b8b45e4","StartupWizardCompleted":true}"""

	suspend fun typedAddress(info: String): List<org.khylo.tv.core.auth.model.ServerAdditionState> {
		val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
		server.createContext("/") { exchange ->
			val body = when (exchange.requestURI.path) {
				"/System/Info/Public" -> info
				"/Branding/Configuration" -> """{"LoginDisclaimer":null,"CustomCss":null,"SplashscreenEnabled":false}"""
				else -> null
			}
			if (body == null) {
				exchange.sendResponseHeaders(404, -1)
			} else {
				val bytes = body.toByteArray()
				exchange.responseHeaders.add("Content-Type", "application/json")
				exchange.sendResponseHeaders(200, bytes.size.toLong())
				exchange.responseBody.use { it.write(bytes) }
			}
			exchange.close()
		}
		// One connection must not hold up the others: the app also tries a secure connection to the same port, which a plain server never answers
		val pool = Executors.newCachedThreadPool()
		server.executor = pool
		server.start()
		try {
			val store = mockk<AuthenticationStore>(relaxed = true)
			every { store.getServer(any()) } returns null
			every { store.getServers() } returns emptyMap()
			val factory = OkHttpFactory()
			val sdk = KhyloSdk(
				KhyloSdkOptions(
					context = mockk(relaxed = true),
					clientInfo = ClientInfo("Khylo TV test", "1"),
					deviceInfo = DeviceInfo("test-device", "Test device"),
					apiClientFactory = factory,
					socketConnectionFactory = factory,
					minimumServerVersion = ServerRepository.minimumServerVersion,
				)
			)
			return ServerRepositoryImpl(sdk, store).addServer("127.0.0.1:${server.address.port}").toList()
		} finally {
			server.stop(0)
			pool.shutdownNow()
		}
	}

	context("adding a server by typing its address") {
		test("a server that reports its build name is added") {
			typedAddress(publicInfo("DEV-V26.11.08.11")).last().shouldBeInstanceOf<ConnectedState>()
		}

		test("a server that reports a release build name is added") {
			typedAddress(publicInfo("V26.12.01.01")).last().shouldBeInstanceOf<ConnectedState>()
		}

		test("a server that reports an ordinary version number is added") {
			typedAddress(publicInfo("10.11.5")).last().shouldBeInstanceOf<ConnectedState>()
		}

		test("a Khylo server is added whatever its version looks like") {
			typedAddress(publicInfo("something-new")).last().shouldBeInstanceOf<ConnectedState>()
		}

		test("something that isn't a Khylo server is refused, and says why") {
			val last = typedAddress(publicInfo("DEV-V26.11.08.11", product = "Some Other Server")).last()

			last.shouldBeInstanceOf<UnableToConnectState>()
			last.addressCandidates.values.flatten().map { it.javaClass } shouldContain RecommendedServerIssue.InvalidProductName::class.java
		}
	}

	context("what a server says its version is") {
		test("a Khylo build name counts as the version the API is built on") {
			ServerVersion.fromServerReport("DEV-V26.11.08.11") shouldBe ServerVersion(13, 0, 0)
			ServerVersion.fromServerReport("V26.12.01.01") shouldBe ServerVersion(13, 0, 0)
			ServerVersion.fromServerReport(" dev-v26.11.08.11 ") shouldBe ServerVersion(13, 0, 0)
		}

		test("an ordinary number is read as one") {
			ServerVersion.fromServerReport("10.11.5") shouldBe ServerVersion(10, 11, 5)
			ServerVersion.fromServerReport("10.11.5.0") shouldBe ServerVersion(10, 11, 5, 0)
		}

		test("anything else is not a version") {
			ServerVersion.fromServerReport("banana") shouldBe null
			ServerVersion.fromServerReport("") shouldBe null
		}
	}
})
