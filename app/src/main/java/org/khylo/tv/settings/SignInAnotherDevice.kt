package org.khylo.tv.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.khylo.sdk.api.client.ApiClient
import org.khylo.tv.app.navigator
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloType
import org.koin.compose.koinInject
import java.time.Instant

@Serializable
private data class SignInCode(
	@SerialName("Code") val code: String,
	@SerialName("ExpiresAt") val expiresAt: String,
	@SerialName("QrUrl") val qrUrl: String,
)

@Serializable
private data class SignInCodeStatus(
	@SerialName("Used") val used: Boolean = false,
	@SerialName("Expired") val expired: Boolean = false,
	@SerialName("DeviceName") val deviceName: String? = null,
)

private sealed interface Stage {
	data object Loading : Stage
	data class Showing(val code: SignInCode, val qr: Bitmap) : Stage
	data class Used(val deviceName: String) : Stage
	data object Expired : Stage
	data object Failed : Stage
}

private val json = Json { ignoreUnknownKeys = true }

/** Talks to Khylo/SignInCodes as the signed-in person. */
private class SignInCodesApi(private val api: ApiClient, private val http: OkHttpClient) {
	private fun request(path: String) = Request.Builder()
		.url("${api.baseUrl}/Khylo/SignInCodes$path")
		.header("Authorization", "MediaBrowser Token=\"${api.accessToken}\"")

	suspend fun create(): SignInCode = withContext(Dispatchers.IO) {
		http.newCall(request("").post(ByteArray(0).toRequestBody()).build()).execute().use { response ->
			check(response.isSuccessful) { "HTTP ${response.code}" }
			json.decodeFromString(SignInCode.serializer(), response.body?.string().orEmpty())
		}
	}

	suspend fun status(code: String): SignInCodeStatus? = withContext(Dispatchers.IO) {
		runCatching {
			http.newCall(request("/$code").get().build()).execute().use { response ->
				if (!response.isSuccessful) return@use null
				json.decodeFromString(SignInCodeStatus.serializer(), response.body?.string().orEmpty())
			}
		}.getOrNull()
	}

	suspend fun cancel(code: String) = withContext(Dispatchers.IO) {
		runCatching { http.newCall(request("/$code").delete().build()).execute().close() }
	}
}

/**
 * Shows a QR code a phone scans (Khylo app → Scan to Sign In) to sign in as whoever is using this TV,
 * with no server address, user name or password typed. Leaving the screen cancels the code.
 */
@Composable
fun SignInAnotherDeviceScreen() {
	val navigator = navigator()
	val api = koinInject<ApiClient>()
	val http = koinInject<OkHttpClient>()
	val codes = remember { SignInCodesApi(api, http) }
	var stage by remember { mutableStateOf<Stage>(Stage.Loading) }
	var generation by remember { mutableIntStateOf(0) }
	var secondsLeft by remember { mutableIntStateOf(0) }
	var activeCode by remember { mutableStateOf<String?>(null) }

	// A fresh code on opening, and on "New code"
	LaunchedEffect(generation) {
		stage = Stage.Loading
		stage = runCatching {
			val code = codes.create()
			activeCode = code.code
			Stage.Showing(code, withContext(Dispatchers.Default) { qrBitmap(code.qrUrl, 600) })
		}.getOrElse { Stage.Failed }
	}

	// Count down, and check every 2 seconds whether a phone has used it
	LaunchedEffect(stage) {
		val showing = stage as? Stage.Showing ?: return@LaunchedEffect
		val expiresAt = runCatching { Instant.parse(showing.code.expiresAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis() + 300_000)
		var tick = 0
		while (true) {
			secondsLeft = ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()
			if (secondsLeft == 0) {
				activeCode = null
				stage = Stage.Expired
				return@LaunchedEffect
			}
			if (tick % 2 == 0) {
				val status = codes.status(showing.code.code)
				when {
					status?.used == true -> {
						activeCode = null
						stage = Stage.Used(status.deviceName ?: "your phone")
						return@LaunchedEffect
					}
					status?.expired == true -> {
						activeCode = null
						stage = Stage.Expired
						return@LaunchedEffect
					}
				}
			}
			tick++
			delay(1000)
		}
	}

	// Leaving the screen cancels a code nobody has used
	DisposableEffect(Unit) {
		onDispose {
			activeCode?.let { code -> cancelLater(codes, code) }
		}
	}

	Box(Modifier.fillMaxSize().background(KhyloColors.Background), contentAlignment = Alignment.Center) {
		when (val current = stage) {
			Stage.Loading -> KSpinner()
			is Stage.Showing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(72.dp)) {
				Column(Modifier.width(520.dp)) {
					KText("Sign in another device", style = KhyloType.Title)
					Spacer(Modifier.height(16.dp))
					KText("On your phone, open the Khylo app and tap Scan to Sign In, then point the camera at this code.", style = KhyloType.Body, color = KhyloColors.TextSecondary)
					Spacer(Modifier.height(28.dp))
					KText("Works for ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}", style = KhyloType.Caption)
					Spacer(Modifier.height(32.dp))
					KButton("Done", onClick = { navigator.back() }, style = KButtonStyle.Secondary)
				}
				Box(Modifier.size(340.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)) {
					Image(current.qr.asImageBitmap(), contentDescription = "QR code to sign in another device", modifier = Modifier.fillMaxSize())
				}
			}
			is Stage.Used -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Box(Modifier.size(84.dp).clip(RoundedCornerShape(50)).background(Color(0xFF34D399)), contentAlignment = Alignment.Center) {
					KIcon(KIcons.Check, tint = Color.White, modifier = Modifier.size(48.dp))
				}
				Spacer(Modifier.height(24.dp))
				KText("Signed in on ${current.deviceName}", style = KhyloType.Title)
				Spacer(Modifier.height(32.dp))
				KButton("Done", onClick = { navigator.back() }, style = KButtonStyle.Primary)
			}
			Stage.Expired, Stage.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
				KText(if (current == Stage.Expired) "This code ran out" else "Couldn't make a code", style = KhyloType.Title)
				Spacer(Modifier.height(12.dp))
				KText(
					if (current == Stage.Expired) "Codes work for 5 minutes, once." else "Check that this TV can reach your Khylo server.",
					style = KhyloType.Body,
					color = KhyloColors.TextSecondary,
				)
				Spacer(Modifier.height(32.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
					KButton("New code", onClick = { generation++ }, style = KButtonStyle.Primary)
					KButton("Done", onClick = { navigator.back() })
				}
			}
		}
	}
}

// The screen is already gone, so the cancel runs on its own
@OptIn(DelicateCoroutinesApi::class)
private fun cancelLater(codes: SignInCodesApi, code: String) {
	GlobalScope.launch(Dispatchers.IO) { codes.cancel(code) }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
	val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
	val bitmap = createBitmap(size, size)
	for (x in 0 until size) for (y in 0 until size) {
		bitmap[x, y] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
	}
	return bitmap
}
