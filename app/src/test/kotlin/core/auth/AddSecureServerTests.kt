package org.khylo.tv.core.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import okhttp3.OkHttpClient
import org.khylo.sdk.KhyloSdk
import org.khylo.sdk.KhyloSdkOptions
import org.khylo.sdk.api.okhttp.OkHttpFactory
import org.khylo.sdk.model.ClientInfo
import org.khylo.sdk.model.DeviceInfo
import org.khylo.tv.core.auth.model.ConnectedState
import org.khylo.tv.core.auth.model.UnableToConnectState
import org.khylo.tv.core.auth.repository.ServerRepository
import org.khylo.tv.core.auth.repository.ServerRepositoryImpl
import org.khylo.tv.core.auth.store.AuthenticationStore
import org.khylo.tv.data.CertificateProbe
import org.khylo.tv.data.MemoryPinStore
import org.khylo.tv.data.TestTls
import org.khylo.tv.data.TrustedCertificates
import org.khylo.tv.data.trustingChosenCertificates
import java.nio.file.Files

// The whole way a server with a certificate the owner made is added: refused, shown to the owner, trusted, then it works
class AddSecureServerTests : FunSpec({
	val dir = Files.createTempDirectory("khylo-secure-add-tests")
	afterSpec { dir.toFile().deleteRecursively() }

	val publicInfo = """{"LocalAddress":"https://127.0.0.1","ServerName":"Khylo","Version":"DEV-V26.11.08.11","KhyloBuild":"DEV-V26.11.08.11","ProductName":"Khylo Server","Id":"b50bd8aa27b241c2a358d7375b8b45e4","StartupWizardCompleted":true}"""

	fun repository(trusted: TrustedCertificates): ServerRepositoryImpl {
		val store = mockk<AuthenticationStore>(relaxed = true)
		every { store.getServer(any()) } returns null
		every { store.getServers() } returns emptyMap()
		val factory = OkHttpFactory(OkHttpClient.Builder().trustingChosenCertificates(trusted).build())
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
		return ServerRepositoryImpl(sdk, store)
	}

	test("a server with a self-signed certificate is added once the owner has chosen to trust the certificate") {
		val identity = TestTls.selfSigned(dir, "owner-made")
		val server = TestTls.server(identity) { path ->
			when (path) {
				"/System/Info/Public" -> publicInfo
				"/Branding/Configuration" -> """{"LoginDisclaimer":null,"CustomCss":null,"SplashscreenEnabled":false}"""
				else -> null
			}
		}
		try {
			val trusted = TrustedCertificates(MemoryPinStore())
			val address = "https://127.0.0.1:${server.address.port}"

			// Refused at first, and the certificate is there for the owner to look at
			val refused = repository(trusted).addServer(address).toList().last()
			refused.shouldBeInstanceOf<UnableToConnectState>()
			val shown = CertificateProbe.untrusted(refused)!!
			shown.fingerprint shouldBe org.khylo.tv.data.CertificatePin.fingerprint(identity.certificate)

			// The owner chooses to trust it
			trusted.trust(shown.certificate)

			repository(trusted).addServer(address).toList().last().shouldBeInstanceOf<ConnectedState>()
		} finally {
			server.stop(0)
		}
	}

	test("a server that answers over a plain connection has no certificate to show") {
		// Nothing listens here, so there is nothing to look at and nothing is offered
		CertificateProbe.fetch("127.0.0.1", 1) shouldBe null
	}
})
