package org.khylo.tv.core.auth.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Locks and unlocks bytes with a key that never leaves the TV's secure storage. */
interface SecretBox {
	fun seal(plain: ByteArray): ByteArray
	fun open(sealed: ByteArray): ByteArray
}

/**
 * Text kept encrypted on the TV, for the sign-in keys. Sealed text starts with a marker, so text that was saved
 * before this existed (plain) is still read, and is sealed the next time it is saved. If the TV's secure storage can't
 * be used, the text is saved plain rather than lost (the file is private to Khylo either way), and if sealed text can't
 * be opened (the key is gone, as after a restore onto another TV) it reads as missing, so the person signs in again.
 */
class SealedText(private val box: SecretBox) {
	fun isSealed(stored: String): Boolean = stored.startsWith(MARKER)

	fun seal(text: String): String = try {
		MARKER + toHex(box.seal(text.toByteArray(Charsets.UTF_8)))
	} catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
		Timber.w(failure, "Couldn't seal the stored sign-in, keeping it as it is")
		text
	}

	/** The text, or null when it was sealed and can't be opened. */
	fun open(stored: String): String? {
		if (!isSealed(stored)) return stored
		return try {
			String(box.open(fromHex(stored.removePrefix(MARKER))), Charsets.UTF_8)
		} catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
			Timber.w(failure, "Couldn't open the stored sign-in")
			null
		}
	}

	private fun toHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

	private fun fromHex(text: String): ByteArray {
		require(text.length % 2 == 0) { "Not a sealed value" }
		return ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
	}

	companion object {
		const val MARKER = "khylo-sealed-1:"
	}
}

/** AES-GCM with a key made and kept by the Android keystore, so the key can't be copied off the TV. */
class KeystoreSecretBox(private val alias: String = "khylo_sign_in_store") : SecretBox {
	override fun seal(plain: ByteArray): ByteArray {
		val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
		return cipher.iv + cipher.doFinal(plain)
	}

	override fun open(sealed: ByteArray): ByteArray {
		require(sealed.size > IV_BYTES) { "Not a sealed value" }
		val cipher = Cipher.getInstance(TRANSFORMATION).apply {
			init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed.copyOfRange(0, IV_BYTES)))
		}
		return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
	}

	private fun key(): SecretKey {
		val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
		(store.getKey(alias, null) as? SecretKey)?.let { return it }
		return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
			init(
				KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
					.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
					.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
					.setKeySize(KEY_BITS)
					.build()
			)
		}.generateKey()
	}

	private companion object {
		const val PROVIDER = "AndroidKeyStore"
		const val TRANSFORMATION = "AES/GCM/NoPadding"
		const val IV_BYTES = 12
		const val TAG_BITS = 128
		const val KEY_BITS = 256
	}
}

/** The one sign-in key kept apart for crash reports, sealed the same way. */
object TokenVault {
	private val sealed by lazy { SealedText(KeystoreSecretBox("khylo_crash_report_key")) }

	fun seal(token: String): String = sealed.seal(token)

	fun open(stored: String?): String? = stored?.takeIf { it.isNotEmpty() }?.let(sealed::open)
}
