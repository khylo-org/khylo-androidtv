package org.khylo.tv.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Connection
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

private fun chainFor(
	request: Request,
	reached: String? = null,
	onProceed: (Request) -> Unit = {},
): Interceptor.Chain {
	val chain = mockk<Interceptor.Chain>()
	every { chain.request() } returns request
	every { chain.proceed(any()) } answers {
		onProceed(firstArg())
		mockk<Response>()
	}
	every { chain.connection() } returns reached?.let { address ->
		val socket = mockk<Socket>()
		every { socket.inetAddress } returns InetAddress.getByName(address)
		val connection = mockk<Connection>()
		every { connection.socket() } returns socket
		connection
	}
	return chain
}

class NetworkSafetyTests : FunSpec({
	context("the home network") {
		test("private, local and VPN addresses are the home network") {
			listOf(
				"10.1.2.3", "172.16.5.5", "172.31.255.255", "192.168.1.20", "127.0.0.1", "169.254.1.1",
				"100.64.0.1", "100.127.255.255", "::1", "fc00::1", "fd12:3456::1", "fe80::1",
			).forEach { HomeNetwork.isHomeAddress(InetAddress.getByName(it)) shouldBe true }
		}

		test("anything else is the internet") {
			listOf(
				"8.8.8.8", "66.222.114.2", "172.32.0.1", "172.15.255.255", "100.128.0.1", "100.63.255.255", "11.0.0.1",
				"2001:4860:4860::8888", "2600::1",
			).forEach { HomeNetwork.isHomeAddress(InetAddress.getByName(it)) shouldBe false }
		}
	}

	context("the cleartext guard") {
		test("https is never held up, and nothing is checked") {
			var sent: Request? = null
			val request = Request.Builder().url("https://khylo.example.com/System/Info/Public").build()

			CleartextGuard().intercept(chainFor(request, reached = null) { sent = it })

			sent shouldBe request
		}

		test("plain http to the home network goes through") {
			var sent: Request? = null
			val request = Request.Builder().url("http://10.1.2.3:8096/System/Info/Public").build()

			CleartextGuard().intercept(chainFor(request, reached = "10.1.2.3") { sent = it })

			sent shouldBe request
		}

		test("plain http that reached the internet is refused before it is sent") {
			val chain = chainFor(Request.Builder().url("http://khylo.example.com:8096/Users/AuthenticateByName").build(), reached = "66.222.114.2")

			shouldThrow<IOException> { CleartextGuard().intercept(chain) }

			verify(exactly = 0) { chain.proceed(any()) }
		}

		test("plain http with no connection to check is refused") {
			val chain = chainFor(Request.Builder().url("http://10.0.0.5/x").build(), reached = null)

			shouldThrow<IOException> { CleartextGuard().intercept(chain) }

			verify(exactly = 0) { chain.proceed(any()) }
		}
	}

	context("signing requests to the server in") {
		val server = "http://10.1.2.3:8096".toHttpUrl()

		fun send(url: String, signedIn: () -> ServerSignIn?, existing: String? = null): Request? {
			var sent: Request? = null
			val request = Request.Builder().url(url).apply { existing?.let { header("Authorization", it) } }.build()
			ServerAuthInterceptor(signedIn).intercept(chainFor(request) { sent = it })
			return sent
		}

		test("requests to the server get the header") {
			val sent = send("http://10.1.2.3:8096/Khylo/Disc/1/Image?menu=true", { ServerSignIn(server, "MediaBrowser Token=\"abc\"") })

			sent?.header("Authorization") shouldBe "MediaBrowser Token=\"abc\""
			sent?.url?.toString() shouldBe "http://10.1.2.3:8096/Khylo/Disc/1/Image?menu=true"
		}

		test("another host, port or scheme never gets the key") {
			val signedIn = { ServerSignIn(server, "MediaBrowser Token=\"abc\"") }

			send("http://10.1.2.4:8096/x", signedIn)?.header("Authorization") shouldBe null
			send("http://10.1.2.3:8097/x", signedIn)?.header("Authorization") shouldBe null
			send("https://10.1.2.3:8096/x", signedIn)?.header("Authorization") shouldBe null
			send("https://image.tmdb.org/t/p/w500/poster.jpg", signedIn)?.header("Authorization") shouldBe null
		}

		test("a request that already has its own header keeps it") {
			val sent = send("http://10.1.2.3:8096/x", { ServerSignIn(server, "MediaBrowser Token=\"abc\"") }, existing = "MediaBrowser Token=\"own\"")

			sent?.header("Authorization") shouldBe "MediaBrowser Token=\"own\""
		}

		test("nobody signed in means no header, and the key is read for every request") {
			var current: ServerSignIn? = null
			val signedIn = { current }

			send("http://10.1.2.3:8096/x", signedIn)?.header("Authorization") shouldBe null

			current = ServerSignIn(server, "MediaBrowser Token=\"first\"")
			send("http://10.1.2.3:8096/x", signedIn)?.header("Authorization") shouldBe "MediaBrowser Token=\"first\""

			current = ServerSignIn(server, "MediaBrowser Token=\"second\"")
			send("http://10.1.2.3:8096/x", signedIn)?.header("Authorization") shouldBe "MediaBrowser Token=\"second\""
		}
	}

	context("stream addresses") {
		test("the sign-in key is taken out, whatever it is called") {
			StreamAddress.withoutAccessToken("http://10.0.0.5:8096/videos/1/master.m3u8?DeviceId=d&ApiKey=secret&MediaSourceId=m") shouldBe
				"http://10.0.0.5:8096/videos/1/master.m3u8?DeviceId=d&MediaSourceId=m"
			StreamAddress.withoutAccessToken("https://khylo.example.com/videos/1/master.m3u8?api_key=secret") shouldBe
				"https://khylo.example.com/videos/1/master.m3u8"
			StreamAddress.withoutAccessToken("http://10.0.0.5/v?APIKEY=a&x=1&apikey=b") shouldBe "http://10.0.0.5/v?x=1"
		}

		test("an address without a key, or that isn't an address, is left alone") {
			StreamAddress.withoutAccessToken("http://10.0.0.5:8096/videos/1/stream?static=true&mediaSourceId=m") shouldBe
				"http://10.0.0.5:8096/videos/1/stream?static=true&mediaSourceId=m"
			StreamAddress.withoutAccessToken("not an address") shouldBe "not an address"
		}

		test("it works on an address without its host too, and keeps the rest exactly as it was") {
			StreamAddress.withoutAccessToken("/videos/1/master.m3u8?ApiKey=secret") shouldBe "/videos/1/master.m3u8"
			StreamAddress.withoutAccessToken("/v?a=%2F&ApiKey=s&b=c#frag") shouldBe "/v?a=%2F&b=c#frag"
		}
	}
})
