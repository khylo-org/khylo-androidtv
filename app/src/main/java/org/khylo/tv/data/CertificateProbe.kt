package org.khylo.tv.data

import android.annotation.SuppressLint
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.khylo.sdk.discovery.RecommendedServerIssue
import org.khylo.tv.core.auth.model.UnableToConnectState
import java.io.IOException
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/** A certificate a server showed, in the words the owner needs to decide whether it is theirs. */
class ServerCertificate(val certificate: X509Certificate) {
	val fingerprint: String = CertificatePin.fingerprint(certificate)
	val name: String = commonName(certificate.subjectX500Principal)
	val issuer: String = commonName(certificate.issuerX500Principal)
	val selfSigned: Boolean = certificate.subjectX500Principal == certificate.issuerX500Principal
	val validUntil: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(certificate.notAfter)

	private fun commonName(principal: X500Principal): String {
		val text = principal.getName(X500Principal.RFC2253)
		return Regex("(?:^|,)CN=([^,]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim() ?: text
	}
}

/**
 * Looks at the certificate a server shows, without sending anything to it: the handshake is stopped as soon as the certificate
 * arrives. Used to show the owner a certificate this TV doesn't know, so they can choose to trust it.
 */
object CertificateProbe {
	private const val TIMEOUT_MILLIS = 5_000
	private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

	/** The certificate of the first secure address that failed because of its certificate, or null when there is none to look at. */
	fun untrusted(state: UnableToConnectState): ServerCertificate? = state.addressCandidates
		.filterValues { issues -> issues.any { it is RecommendedServerIssue.SecureConnectionFailed } }
		.keys
		.firstNotNullOfOrNull { address ->
			address.toHttpUrlOrNull()?.takeIf { it.isHttps }?.let { fetch(it.host, it.port) }
		}

	@SuppressLint("NewApi")
	fun fetch(host: String, port: Int): ServerCertificate? {
		val seen = AtomicReference<X509Certificate?>()
		val onlyLooking = object : X509TrustManager {
			override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException("Not used")

			override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
				seen.set(chain.firstOrNull())
				throw CertificateException("Only looking at the certificate")
			}

			override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
		}
		val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(onlyLooking), null) }
		try {
			(context.socketFactory.createSocket() as SSLSocket).use { socket ->
				socket.soTimeout = TIMEOUT_MILLIS
				socket.connect(InetSocketAddress(host, port), TIMEOUT_MILLIS)
				// A proxy that serves several sites picks the certificate by the name asked for
				if (!ipv4.matches(host) && !host.contains(':')) {
					runCatching {
						socket.sslParameters = socket.sslParameters.apply { serverNames = listOf(SNIHostName(host)) }
					}
				}
				socket.startHandshake()
			}
		} catch (expected: IOException) {
			// The handshake is stopped on purpose once the certificate has been seen
		}
		return seen.get()?.let(::ServerCertificate)
	}
}
