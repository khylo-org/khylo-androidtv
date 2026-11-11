package org.khylo.tv.data

import android.content.Context
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * How Khylo TV decides whether to trust a server's certificate.
 *
 * A certificate from an authority this TV knows (a public one, or one installed on the TV) is trusted as usual. A certificate
 * that isn't, such as a self-signed one or one from a home-made authority, is trusted only after the owner has looked at it and
 * chosen to trust it; the TV then remembers that certificate's key. A different certificate for the same server is not trusted
 * until the owner chooses it too, so someone in the middle of the connection can't take its place.
 */
object CertificatePin {
	/** The SHA-256 of the certificate's public key. It stays the same when the certificate is renewed with the same key. */
	fun of(certificate: X509Certificate): String = hex(MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded))

	/** The SHA-256 of the whole certificate, written as most tools print it (AB:CD:...), for the owner to compare. */
	fun fingerprint(certificate: X509Certificate): String =
		MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }

	private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}

/** Where the keys the owner chose to trust are kept. They are not in the backup rules, so they never leave this TV. */
interface PinStore {
	fun load(): Set<String>
	fun save(pins: Set<String>)
}

class SharedPreferencesPinStore(context: Context) : PinStore {
	private val preferences = context.getSharedPreferences("trusted_certificates", Context.MODE_PRIVATE)

	override fun load(): Set<String> = preferences.getStringSet(KEY, emptySet()).orEmpty().toSet()

	override fun save(pins: Set<String>) {
		preferences.edit().putStringSet(KEY, pins).commit()
	}

	private companion object {
		const val KEY = "pins"
	}
}

class TrustedCertificates(private val store: PinStore) {
	@Volatile
	private var pins: Set<String> = store.load()

	fun isTrusted(certificate: X509Certificate): Boolean = CertificatePin.of(certificate) in pins

	@Synchronized
	fun trust(certificate: X509Certificate) {
		pins = pins + CertificatePin.of(certificate)
		store.save(pins)
	}
}

/** The authorities the TV already knows first; a certificate the owner chose to trust is accepted when they don't vouch for it. */
class PinnedTrustManager(
	private val delegate: X509TrustManager,
	private val trusted: TrustedCertificates,
) : X509TrustManager {
	override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = delegate.checkClientTrusted(chain, authType)

	override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
		try {
			delegate.checkServerTrusted(chain, authType)
		} catch (refused: CertificateException) {
			// The connection itself proves the server holds this certificate's key, so the key is what the owner trusted
			if (chain.isNotEmpty() && trusted.isTrusted(chain[0])) return
			throw refused
		}
	}

	override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}

/** A certificate the owner chose to trust is the right one for its server whatever name it was made with (an address, a home name). */
class PinnedHostnameVerifier(
	private val delegate: HostnameVerifier,
	private val trusted: TrustedCertificates,
) : HostnameVerifier {
	override fun verify(hostname: String, session: SSLSession): Boolean {
		if (delegate.verify(hostname, session)) return true
		val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull() ?: return false
		return trusted.isTrusted(leaf)
	}
}

/** The secure-connection settings that follow [TrustedCertificates]: what the TV knows, plus what the owner chose to trust. */
class ChosenCertificatesTls(
	val socketFactory: SSLSocketFactory,
	val trustManager: X509TrustManager,
	val hostnameVerifier: HostnameVerifier,
)

fun TrustedCertificates.tls(): ChosenCertificatesTls {
	val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
	val platform = factory.trustManagers.filterIsInstance<X509TrustManager>().first()
	val manager = PinnedTrustManager(platform, this)
	val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
	return ChosenCertificatesTls(context.socketFactory, manager, PinnedHostnameVerifier(OkHttpClient().hostnameVerifier, this))
}

/** Makes every connection from this client, to the server and for pictures, video and sockets, follow [TrustedCertificates]. */
fun OkHttpClient.Builder.trustingChosenCertificates(trusted: TrustedCertificates): OkHttpClient.Builder {
	val tls = trusted.tls()
	return sslSocketFactory(tls.socketFactory, tls.trustManager).hostnameVerifier(tls.hostnameVerifier)
}
