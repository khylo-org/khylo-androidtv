package org.khylo.tv.core.auth.store

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

/** Stands in for the secure storage: reverses and flips the bytes, and refuses anything it didn't make. */
private class FakeBox(private val fails: Boolean = false) : SecretBox {
	override fun seal(plain: ByteArray): ByteArray {
		check(!fails) { "secure storage unavailable" }
		return byteArrayOf(7) + plain.reversedArray().map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
	}

	override fun open(sealed: ByteArray): ByteArray {
		check(!fails) { "secure storage unavailable" }
		require(sealed.isNotEmpty() && sealed[0] == 7.toByte()) { "not sealed by this box" }
		return sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray().reversedArray()
	}
}

class SealedTextTests : FunSpec({
	val json = """{"version":2,"servers":{"x":{"users":{"u":{"access_token":"super-secret-token"}}}}}"""

	test("sealed text starts with the marker, hides the text, and opens again") {
		val text = SealedText(FakeBox())

		val sealed = text.seal(json)

		sealed shouldStartWith SealedText.MARKER
		sealed shouldNotContain "super-secret-token"
		text.isSealed(sealed) shouldBe true
		text.open(sealed) shouldBe json
	}

	test("text saved before sealing existed is read as it is") {
		val text = SealedText(FakeBox())

		text.isSealed(json) shouldBe false
		text.open(json) shouldBe json
	}

	test("sealed text that can't be opened reads as missing") {
		val text = SealedText(FakeBox())
		val sealed = text.seal(json)

		SealedText(FakeBox(fails = true)).open(sealed) shouldBe null
		text.open(SealedText.MARKER + "not-hex") shouldBe null
		text.open(SealedText.MARKER + "ff00ff") shouldBe null
		text.open(SealedText.MARKER) shouldBe null
	}

	test("when the secure storage can't be used the text is kept, not lost") {
		val text = SealedText(FakeBox(fails = true))

		text.seal(json) shouldBe json
	}
})
