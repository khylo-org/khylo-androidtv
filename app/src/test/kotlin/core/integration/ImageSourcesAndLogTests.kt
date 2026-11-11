package org.khylo.tv.core.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.khylo.tv.core.LogPolicy
import org.khylo.tv.core.integration.provider.ImageSources

private const val WARN = 5
private const val ERROR = 6
private const val INFO = 4
private const val DEBUG = 3
private const val VERBOSE = 2

class ImageSourcesAndLogTests : FunSpec({
	val server = "http://10.1.2.3:8096".toHttpUrl()

	context("the pictures the provider hands out") {
		test("the signed-in server's pictures and Khylo's own are allowed") {
			ImageSources.isAllowed("http://10.1.2.3:8096/Items/1/Images/Primary?maxWidth=300", server, "org.khylo.tv") shouldBe true
			ImageSources.isAllowed("android.resource://org.khylo.tv/drawable/tile_land_tv", server, "org.khylo.tv") shouldBe true
			ImageSources.isAllowed("android.resource://org.khylo.tv/drawable/tile_land_tv", null, "org.khylo.tv") shouldBe true
		}

		test("anything else is refused") {
			listOf(
				"http://10.1.2.4:8096/x.png",
				"http://10.1.2.3:9999/x.png",
				"https://10.1.2.3:8096/x.png",
				"https://evil.example/x.png",
				"file:///data/data/org.khylo.tv/files/authentication_store.json",
				"content://com.android.contacts/contacts/1/photo",
				"android.resource://com.other.app/drawable/x",
				"android.resource://org.khylo.tv.evil/drawable/x",
				"",
				"not an address",
			).forEach { ImageSources.isAllowed(it, server, "org.khylo.tv") shouldBe false }
			ImageSources.isAllowed("http://10.1.2.3:8096/x.png", null, "org.khylo.tv") shouldBe false
		}
	}

	context("the release log") {
		test("warnings and errors are always written") {
			LogPolicy.shouldLog(WARN, false) shouldBe true
			LogPolicy.shouldLog(ERROR, false) shouldBe true
		}

		test("the rest only when the developer's switch is on") {
			listOf(INFO, DEBUG, VERBOSE).forEach {
				LogPolicy.shouldLog(it, false) shouldBe false
				LogPolicy.shouldLog(it, true) shouldBe true
			}
		}
	}
})
