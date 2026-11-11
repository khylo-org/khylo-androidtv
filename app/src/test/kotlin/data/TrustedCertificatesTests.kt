package org.khylo.tv.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Files
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

class MemoryPinStore(var pins: Set<String> = emptySet()) : PinStore {
	override fun load() = pins

	override fun save(pins: Set<String>) {
		this.pins = pins
	}
}

// Real secure connections: a certificate made by the owner has to work once the owner has chosen to trust it, and only then
class TrustedCertificatesTests : FunSpec({
	val dir = Files.createTempDirectory("khylo-tls-tests")
	afterSpec { dir.toFile().deleteRecursively() }

	fun clientFor(trusted: TrustedCertificates) = OkHttpClient.Builder()
		.trustingChosenCertificates(trusted)
		.connectTimeout(5, TimeUnit.SECONDS)
		.readTimeout(5, TimeUnit.SECONDS)
		.build()

	fun get(client: OkHttpClient, port: Int): String =
		client.newCall(Request.Builder().url("https://127.0.0.1:$port/hello").build()).execute().use { it.body?.string().orEmpty() }

	test("a self-signed certificate nobody has chosen to trust is refused") {
		val identity = TestTls.selfSigned(dir, "refused")
		val server = TestTls.server(identity) { "hello" }
		try {
			shouldThrow<SSLException> { get(clientFor(TrustedCertificates(MemoryPinStore())), server.address.port) }
		} finally {
			server.stop(0)
		}
	}

	test("the certificate shown to the owner is the one the server really has") {
		val identity = TestTls.selfSigned(dir, "shown")
		val server = TestTls.server(identity) { "hello" }
		try {
			val shown = CertificateProbe.fetch("127.0.0.1", server.address.port)

			shown!!.fingerprint shouldBe CertificatePin.fingerprint(identity.certificate)
			shown.name shouldBe "khylo-test"
			shown.selfSigned shouldBe true
		} finally {
			server.stop(0)
		}
	}

	test("looking at a certificate sends nothing to the server") {
		val identity = TestTls.selfSigned(dir, "quiet")
		var asked = false
		val server = TestTls.server(identity) { asked = true; "hello" }
		try {
			CertificateProbe.fetch("127.0.0.1", server.address.port)
			Thread.sleep(300)

			asked shouldBe false
		} finally {
			server.stop(0)
		}
	}

	test("once the owner trusts it, it works, even by an address it wasn't made for") {
		val identity = TestTls.selfSigned(dir, "trusted")
		val server = TestTls.server(identity) { "hello" }
		try {
			val trusted = TrustedCertificates(MemoryPinStore())
			trusted.trust(identity.certificate)

			// The certificate was made for the name khylo-test; this is by 127.0.0.1
			get(clientFor(trusted), server.address.port) shouldBe "hello"
		} finally {
			server.stop(0)
		}
	}

	test("trusting one certificate doesn't trust another one") {
		val first = TestTls.selfSigned(dir, "first")
		val other = TestTls.selfSigned(dir, "other")
		val server = TestTls.server(other) { "hello" }
		try {
			val trusted = TrustedCertificates(MemoryPinStore())
			trusted.trust(first.certificate)

			shouldThrow<SSLException> { get(clientFor(trusted), server.address.port) }
		} finally {
			server.stop(0)
		}
	}

	test("a certificate renewed with the same key is still trusted") {
		val identity = TestTls.selfSigned(dir, "renewed")
		val before = identity.certificate
		val trusted = TrustedCertificates(MemoryPinStore())
		trusted.trust(before)
		TestTls.renew(identity)
		val server = TestTls.server(identity) { "hello" }
		try {
			(identity.certificate.encoded.contentEquals(before.encoded)) shouldBe false
			get(clientFor(trusted), server.address.port) shouldBe "hello"
		} finally {
			server.stop(0)
		}
	}

	test("what the owner trusted is remembered after the TV restarts") {
		val identity = TestTls.selfSigned(dir, "remembered")
		val store = MemoryPinStore()
		TrustedCertificates(store).trust(identity.certificate)

		TrustedCertificates(store).isTrusted(identity.certificate) shouldBe true
	}

	context("what the TV already trusts comes first") {
		val identity = TestTls.selfSigned(dir, "layers")

		fun manager(accepts: Boolean) = object : X509TrustManager {
			override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

			override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
				if (!accepts) throw CertificateException("not from an authority I know")
			}

			override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
		}

		test("a certificate an authority vouches for needs no choice") {
			PinnedTrustManager(manager(accepts = true), TrustedCertificates(MemoryPinStore())).checkServerTrusted(arrayOf(identity.certificate), "ECDHE_ECDSA")
		}

		test("a certificate no authority vouches for, that was not chosen, is refused") {
			shouldThrow<CertificateException> {
				PinnedTrustManager(manager(accepts = false), TrustedCertificates(MemoryPinStore())).checkServerTrusted(arrayOf(identity.certificate), "ECDHE_ECDSA")
			}
		}

		test("a certificate no authority vouches for, that was chosen, is accepted") {
			val trusted = TrustedCertificates(MemoryPinStore())
			trusted.trust(identity.certificate)

			PinnedTrustManager(manager(accepts = false), trusted).checkServerTrusted(arrayOf(identity.certificate), "ECDHE_ECDSA")
		}

		test("nothing at all is refused, not accepted") {
			shouldThrow<CertificateException> {
				PinnedTrustManager(manager(accepts = false), TrustedCertificates(MemoryPinStore())).checkServerTrusted(emptyArray(), "ECDHE_ECDSA")
			}
		}
	}
})
