package org.khylo.tv.data

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/** Real certificates and a real secure server for the tests, made with the JDK's own keytool. */
object TestTls {
	private const val PASSWORD = "changeit"

	class Identity(val file: File, val alias: String) {
		val keyStore: KeyStore get() = KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, PASSWORD.toCharArray()) } }
		val certificate: X509Certificate get() = keyStore.getCertificate(alias) as X509Certificate
	}

	/** A self-signed certificate made for [name] only (so an address such as 127.0.0.1 doesn't match it). */
	fun selfSigned(dir: Path, file: String, name: String = "khylo-test"): Identity {
		val store = dir.resolve("$file.p12").toFile()
		keytool("-genkeypair", "-alias", "server", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=$name", "-validity", "5",
			"-storetype", "PKCS12", "-keystore", store.path, "-storepass", PASSWORD, "-keypass", PASSWORD, "-ext", "san=dns:$name")
		return Identity(store, "server")
	}

	/** The same key with a new certificate, as when a certificate is renewed. */
	fun renew(identity: Identity): Identity {
		keytool("-selfcert", "-alias", identity.alias, "-validity", "9", "-storetype", "PKCS12", "-keystore", identity.file.path,
			"-storepass", PASSWORD, "-keypass", PASSWORD)
		return identity
	}

	fun server(identity: Identity, answer: (path: String) -> String?): HttpsServer {
		val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(identity.keyStore, PASSWORD.toCharArray()) }
		val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
		val server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
		server.httpsConfigurator = HttpsConfigurator(context)
		server.createContext("/") { exchange ->
			val body = answer(exchange.requestURI.path)
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
		// Connections are handled side by side, so one that never finishes (a probe that stops after the handshake) holds nothing up
		server.executor = Executors.newCachedThreadPool { task -> Thread(task).apply { isDaemon = true } }
		server.start()
		return server
	}

	private fun keytool(vararg arguments: String) {
		val exe = File(System.getProperty("java.home"), "bin/keytool" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
		val process = ProcessBuilder(listOf(exe.path) + arguments).redirectErrorStream(true).start()
		val output = process.inputStream.bufferedReader().readText()
		check(process.waitFor() == 0) { "keytool failed: $output" }
	}
}
