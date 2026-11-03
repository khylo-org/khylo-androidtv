package org.khylo.tv.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import org.khylo.tv.settings.ToggleRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.khylo.tv.core.auth.model.AuthenticatedState
import org.khylo.tv.core.auth.model.AuthenticationResultMethod
import org.khylo.tv.core.auth.repository.AuthenticationRepository
import org.khylo.tv.core.auth.repository.ServerRepository
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.model.api.AuthenticationResult
import org.khylo.tv.app.navigator
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KTextField
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.autoFocusContent
import org.khylo.tv.design.rememberFocusState
import org.koin.compose.koinInject

@Serializable
private data class HomeProfile(
	@SerialName("Id") val id: String,
	@SerialName("Name") val name: String,
	@SerialName("IsOwner") val isOwner: Boolean = false,
	@SerialName("IsCurrent") val isCurrent: Boolean = false,
	@SerialName("HasPin") val hasPin: Boolean = false,
	@SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
	@SerialName("IsKids") val isKids: Boolean = false,
	@SerialName("RatingLimit") val ratingLimit: String? = null,
	@SerialName("NeedsGrownUp") val needsGrownUp: Boolean = false,
)

@Serializable
private data class KidsLevel(
	@SerialName("Score") val score: Int,
	@SerialName("Ratings") val ratings: List<String> = emptyList(),
)

@Serializable
private data class KidsLibrary(
	@SerialName("Id") val id: String,
	@SerialName("Name") val name: String,
	@SerialName("CollectionType") val collectionType: String? = null,
)

@Serializable
private data class KidsOptions(
	@SerialName("Levels") val levels: List<KidsLevel> = emptyList(),
	@SerialName("Libraries") val libraries: List<KidsLibrary> = emptyList(),
	@SerialName("OwnerHasPin") val ownerHasPin: Boolean = false,
	@SerialName("LiveTvAvailable") val liveTvAvailable: Boolean = true,
)

/** What a new kids profile is limited to. */
private data class KidsLimits(
	val maxRating: Int,
	val allowUnrated: Boolean,
	val allowLiveTv: Boolean,
	val libraries: List<String>,
	val ownerPin: String?,
)

@Serializable
private data class HomeProfiles(
	@SerialName("Profiles") val profiles: List<HomeProfile> = emptyList(),
	@SerialName("CanManage") val canManage: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }
private val jsonType = "application/json".toMediaType()

// Each profile gets its own color, the same every time (matches the web app)
private val profileColors = listOf(0xFFD97732, 0xFF3B82F6, 0xFF10B981, 0xFFA855F7, 0xFFEF4444, 0xFFEAB308, 0xFF14B8A6, 0xFFEC4899).map { Color(it) }
private fun colorFor(id: String) = profileColors[id.replace("-", "").takeLast(4).toInt(16) % profileColors.size]

/** Too many wrong PINs: the server pauses further tries for [minutes]. */
private class PinPaused(val minutes: Int) : Exception()

/** Talks to the server's home profiles API (Khylo/HomeProfiles). */
private class HomeProfilesApi(private val api: ApiClient, private val http: OkHttpClient) {
	private fun request(path: String) = Request.Builder()
		.url("${api.baseUrl}/Khylo/HomeProfiles$path")
		.header("Authorization", "MediaBrowser Token=\"${api.accessToken}\"")

	suspend fun list(): HomeProfiles = withContext(Dispatchers.IO) {
		http.newCall(request("").get().build()).execute().use { response ->
			check(response.isSuccessful) { "HTTP ${response.code}" }
			json.decodeFromString(HomeProfiles.serializer(), response.body?.string().orEmpty())
		}
	}

	/** @return the new sign-in, or null when the PIN was wrong. */
	suspend fun switchTo(id: String, pin: String?): AuthenticationResult? = withContext(Dispatchers.IO) {
		val body = (if (pin != null) """{"Pin":"$pin"}""" else "{}").toRequestBody(jsonType)
		http.newCall(request("/$id/Switch").post(body).build()).execute().use { response ->
			if (response.code == 403) return@use null
			// Too many wrong PINs: the server asks to wait a few minutes
			if (response.code == 429) throw PinPaused(((response.header("Retry-After")?.toIntOrNull() ?: 300) + 59) / 60)
			check(response.isSuccessful) { "HTTP ${response.code}" }
			json.decodeFromString(AuthenticationResult.serializer(), response.body?.string().orEmpty())
		}
	}

	suspend fun kidsOptions(): KidsOptions = withContext(Dispatchers.IO) {
		http.newCall(request("/KidsOptions").get().build()).execute().use { response ->
			check(response.isSuccessful) { "HTTP ${response.code}" }
			json.decodeFromString(KidsOptions.serializer(), response.body?.string().orEmpty())
		}
	}

	/** @return null on success, or the server's reason. */
	suspend fun create(name: String, pin: String?, kids: KidsLimits? = null): String? = withContext(Dispatchers.IO) {
		val fields = buildMap<String, kotlinx.serialization.json.JsonElement> {
			put("Name", kotlinx.serialization.json.JsonPrimitive(name))
			if (!pin.isNullOrEmpty()) put("Pin", kotlinx.serialization.json.JsonPrimitive(pin))
			if (kids != null) {
				put("IsKids", kotlinx.serialization.json.JsonPrimitive(true))
				put("MaxParentalRating", kotlinx.serialization.json.JsonPrimitive(kids.maxRating))
				put("AllowUnrated", kotlinx.serialization.json.JsonPrimitive(kids.allowUnrated))
				put("AllowLiveTv", kotlinx.serialization.json.JsonPrimitive(kids.allowLiveTv))
				put("Libraries", kotlinx.serialization.json.JsonArray(kids.libraries.map { kotlinx.serialization.json.JsonPrimitive(it) }))
				kids.ownerPin?.let { put("OwnerPin", kotlinx.serialization.json.JsonPrimitive(it)) }
			}
		}
		val body = kotlinx.serialization.json.JsonObject(fields).toString().toRequestBody(jsonType)
		http.newCall(request("").post(body).build()).execute().use { response ->
			if (response.isSuccessful) null else response.body?.string().orEmpty().trim('"').ifBlank { "Couldn't add the profile." }
		}
	}
}

private sealed interface Step {
	data object Pick : Step
	data class Pin(val profile: HomeProfile) : Step
	data object Add : Step
	data class Kids(val name: String, val pin: String) : Step
}

/** "Who's watching?": switch to another profile in this home, or add one. */
@Composable
fun ProfilesScreen() {
	val navigator = navigator()
	val apiClient = koinInject<ApiClient>()
	val http = koinInject<OkHttpClient>()
	val authentication = koinInject<AuthenticationRepository>()
	val servers = koinInject<ServerRepository>()
	val profilesApi = remember { HomeProfilesApi(apiClient, http) }
	val scope = rememberCoroutineScope()

	var data by remember { mutableStateOf<HomeProfiles?>(null) }
	var step by remember { mutableStateOf<Step>(Step.Pick) }
	var error by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }

	suspend fun reload() {
		data = runCatching { profilesApi.list() }.onFailure { error = "Couldn't load profiles." }.getOrNull()
	}
	LaunchedEffect(Unit) { reload() }

	fun switchTo(profile: HomeProfile, pin: String?) {
		if (profile.isCurrent) {
			navigator.back()
			return
		}
		busy = true
		error = null
		scope.launch {
			val attempt = runCatching { profilesApi.switchTo(profile.id, pin) }
			val paused = attempt.exceptionOrNull() as? PinPaused
			val result = attempt.getOrNull()
			val server = servers.currentServer.value
			if (result == null || server == null) {
				error = when {
					paused != null -> "Too many wrong PINs. Try again in ${paused.minutes} ${if (paused.minutes == 1) "minute" else "minutes"}."
					profile.hasPin -> "That PIN isn't right."
					else -> "Couldn't switch profiles."
				}
				busy = false
				return@launch
			}
			// The app restarts on the new profile as soon as its session is active. This screen
			// closes while that happens, so the switch must not be cancelled with it.
			withContext(kotlinx.coroutines.NonCancellable) {
				authentication.authenticate(server, AuthenticationResultMethod(result)).collect { state ->
					if (state != AuthenticatedState) busy = false
				}
			}
		}
	}

	Box(Modifier.fillMaxSize().background(KhyloColors.Background), contentAlignment = Alignment.Center) {
		when (val current = step) {
			Step.Pick -> Column(Modifier.autoFocusContent("pick"), horizontalAlignment = Alignment.CenterHorizontally) {
				KText("Who's watching?", style = KhyloType.Display)
				Spacer(Modifier.height(48.dp))
				val loaded = data
				if (loaded == null) {
					if (error == null) KSpinner()
				} else {
					Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
						loaded.profiles.forEach { profile ->
							ProfileTile(
								name = profile.name,
								color = colorFor(profile.id),
								imageUrl = profile.primaryImageTag?.let { "${apiClient.baseUrl}/Users/${profile.id}/Images/Primary?tag=$it&maxWidth=300" },
								locked = profile.hasPin || profile.needsGrownUp,
								current = profile.isCurrent,
								kidsLabel = if (profile.isKids) "Kids" else null,
								onClick = {
									if (busy) return@ProfileTile
									when {
										profile.needsGrownUp -> error = "Ask a grown-up to add a PIN to ${profile.name}'s profile first."
										profile.hasPin && !profile.isCurrent -> step = Step.Pin(profile)
										else -> switchTo(profile, null)
									}
								},
							)
						}
						if (loaded.canManage && loaded.profiles.size < 9) {
							ProfileTile(name = "Add profile", color = Color.Transparent, imageUrl = null, locked = false, current = false, isAdd = true, onClick = { error = null; step = Step.Add })
						}
					}
				}
				if (busy) {
					Spacer(Modifier.height(28.dp))
					KSpinner()
				}
				error?.let {
					Spacer(Modifier.height(24.dp))
					KText(it, style = KhyloType.Body, color = Color(0xFFEF8F8F))
				}
			}

			is Step.Pin -> {
				var pin by remember { mutableStateOf("") }
				Column(Modifier.width(420.dp).autoFocusContent("pin"), horizontalAlignment = Alignment.CenterHorizontally) {
					KText("Enter ${current.profile.name}'s PIN", style = KhyloType.Title, textAlign = TextAlign.Center)
					Spacer(Modifier.height(28.dp))
					KTextField(pin, { pin = it.filter(Char::isDigit).take(4) }, "PIN", isPassword = true, keyboardType = KeyboardType.NumberPassword, onDone = { if (pin.length == 4) switchTo(current.profile, pin) })
					Spacer(Modifier.height(20.dp))
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						KButton("Continue", onClick = { switchTo(current.profile, pin) }, style = KButtonStyle.Primary, enabled = pin.length == 4 && !busy)
						KButton("Back", onClick = { error = null; step = Step.Pick })
					}
					error?.let {
						Spacer(Modifier.height(18.dp))
						KText(it, style = KhyloType.Body, color = Color(0xFFEF8F8F))
					}
				}
			}

			Step.Add -> {
				var name by remember { mutableStateOf("") }
				var pin by remember { mutableStateOf("") }
				var kids by remember { mutableStateOf(false) }
				fun add() {
					if (name.isBlank() || busy || (pin.isNotEmpty() && pin.length != 4)) return
					if (kids) {
						error = null
						step = Step.Kids(name.trim(), pin)
						return
					}
					busy = true
					scope.launch {
						val failure = runCatching { profilesApi.create(name.trim(), pin.ifEmpty { null }) }.getOrElse { "Couldn't add the profile." }
						busy = false
						if (failure == null) {
							reload()
							step = Step.Pick
						} else {
							error = failure
						}
					}
				}
				Column(Modifier.width(520.dp).autoFocusContent("add")) {
					KText("Add a profile", style = KhyloType.Title)
					Spacer(Modifier.height(8.dp))
					KText("For someone in your home. They get their own Continue watching, history and collections.", style = KhyloType.Body)
					Spacer(Modifier.height(28.dp))
					KTextField(name, { name = it.take(40) }, "Name", placeholder = "Their name")
					Spacer(Modifier.height(18.dp))
					KTextField(pin, { pin = it.filter(Char::isDigit).take(4) }, "PIN (optional)", placeholder = "4 digits", keyboardType = KeyboardType.NumberPassword, onDone = ::add)
					Spacer(Modifier.height(14.dp))
					ToggleRow("Kids profile", "Only shows and movies with the ratings you pick", kids) { kids = it }
					Spacer(Modifier.height(20.dp))
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						KButton(if (busy) "Adding…" else if (kids) "Next" else "Add profile", onClick = ::add, style = KButtonStyle.Primary, enabled = name.isNotBlank() && !busy)
						KButton("Cancel", onClick = { error = null; step = Step.Pick })
					}
					error?.let {
						Spacer(Modifier.height(18.dp))
						KText(it, style = KhyloType.Body, color = Color(0xFFEF8F8F))
					}
				}
			}

			is Step.Kids -> KidsSetup(
				name = current.name,
				profilesApi = profilesApi,
				busy = busy,
				error = error,
				onBack = { error = null; step = Step.Add },
				onAdd = { limits ->
					busy = true
					error = null
					scope.launch {
						val failure = runCatching { profilesApi.create(current.name, current.pin.ifEmpty { null }, limits) }.getOrElse { "Couldn't add the profile." }
						busy = false
						if (failure == null) {
							reload()
							step = Step.Pick
						} else {
							error = failure
						}
					}
				},
			)
		}
	}
}

/**
 * Sets up a kids profile: the highest ratings it can watch, whether unrated titles and Live TV are
 * allowed, and which libraries it sees. Only an administrator can change these afterwards.
 */
@Composable
private fun KidsSetup(name: String, profilesApi: HomeProfilesApi, busy: Boolean, error: String?, onBack: () -> Unit, onAdd: (KidsLimits) -> Unit) {
	var options by remember { mutableStateOf<KidsOptions?>(null) }
	var loadFailed by remember { mutableStateOf(false) }
	LaunchedEffect(Unit) { options = runCatching { profilesApi.kidsOptions() }.onFailure { loadFailed = true }.getOrNull() }

	val loaded = options
	if (loaded == null) {
		if (loadFailed) KText("Couldn't load the rating options.", style = KhyloType.Body, color = Color(0xFFEF8F8F)) else KSpinner()
		return
	}

	// PG (or the closest level under it) to start with
	var maxRating by remember { mutableStateOf(loaded.levels.lastOrNull { it.score <= 10 }?.score ?: loaded.levels.firstOrNull()?.score ?: 0) }
	var allowUnrated by remember { mutableStateOf(false) }
	var allowLiveTv by remember { mutableStateOf(false) }
	var libraries by remember { mutableStateOf(loaded.libraries.map { it.id }.toSet()) }
	var ownerPin by remember { mutableStateOf("") }
	val needsOwnerPin = !loaded.ownerHasPin
	val ready = !busy && libraries.isNotEmpty() && (!needsOwnerPin || ownerPin.length == 4)

	Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 40.dp).autoFocusContent("kids")) {
		KText("What can $name watch?", style = KhyloType.Title)
		Spacer(Modifier.height(6.dp))
		KText("Only a server admin can change this later, from the dashboard.", style = KhyloType.Body)
		Spacer(Modifier.height(24.dp))

		Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
			Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
				KText("RATINGS", style = KhyloType.Caption, color = KhyloColors.TextMuted)
				Spacer(Modifier.height(8.dp))
				loaded.levels.forEach { level ->
					CheckRow(level.ratings.joinToString("  ·  "), checked = level.score <= maxRating) { maxRating = level.score }
				}
				Spacer(Modifier.height(10.dp))
				ToggleRow("Unrated (NR)", "Movies and shows with no rating", allowUnrated) { allowUnrated = it }
			}
			Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
				KText("LIBRARIES", style = KhyloType.Caption, color = KhyloColors.TextMuted)
				Spacer(Modifier.height(8.dp))
				loaded.libraries.forEach { library ->
					CheckRow(library.name, checked = library.id in libraries) {
						libraries = if (library.id in libraries) libraries - library.id else libraries + library.id
					}
				}
				Spacer(Modifier.height(10.dp))
				if (loaded.liveTvAvailable) ToggleRow("Live TV", "Channels from your TV service", allowLiveTv) { allowLiveTv = it }
				if (needsOwnerPin) {
					Spacer(Modifier.height(18.dp))
					KTextField(
						ownerPin,
						{ ownerPin = it.filter(Char::isDigit).take(4) },
						"A PIN for your profile",
						placeholder = "4 digits, so kids can't switch into it",
						isPassword = true,
						keyboardType = KeyboardType.NumberPassword,
					)
				}
			}
		}

		Spacer(Modifier.height(20.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
			KButton(
				if (busy) "Adding…" else "Add kids profile",
				onClick = {
					if (ready) onAdd(KidsLimits(maxRating, allowUnrated, allowLiveTv, libraries.toList(), ownerPin.ifEmpty { null }))
				},
				style = KButtonStyle.Primary,
				enabled = ready,
			)
			KButton("Back", onClick = onBack)
			error?.let {
				Spacer(Modifier.width(12.dp))
				KText(it, style = KhyloType.Body, color = Color(0xFFEF8F8F))
			}
		}
	}
}

/** A row with a round check mark, for picking ratings and libraries. */
@Composable
private fun CheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		Modifier
			.fillMaxWidth()
			.padding(bottom = 2.dp)
			.clip(RoundedCornerShape(12.dp))
			.background(if (focused) KhyloColors.Focus else Color.Transparent)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 20.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			Modifier
				.size(24.dp)
				.clip(CircleShape)
				.background(if (checked) KhyloColors.Accent else Color.Transparent)
				.border(2.dp, if (checked) KhyloColors.Accent else if (focused) Color(0xFF777777) else Color(0x66FFFFFF), CircleShape),
			contentAlignment = Alignment.Center,
		) {
			if (checked) KIcon(KIcons.Check, Color.White, Modifier.size(16.dp))
		}
		Spacer(Modifier.width(16.dp))
		KText(
			label,
			style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold),
			color = if (focused) KhyloColors.OnFocus else if (checked) KhyloColors.Text else KhyloColors.TextSecondary,
			maxLines = 1,
		)
	}
}

@Composable
private fun ProfileTile(name: String, color: Color, imageUrl: String?, locked: Boolean, current: Boolean, isAdd: Boolean = false, kidsLabel: String? = null, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
		Box(
			Modifier
				.size(150.dp)
				.scale(if (focused) 1.08f else 1f)
				.clip(RoundedCornerShape(16.dp))
				.background(color)
				.border(if (focused) 4.dp else if (isAdd) 2.dp else 0.dp, if (focused) KhyloColors.Focus else Color(0x4DFFFFFF), RoundedCornerShape(16.dp))
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			when {
				isAdd -> KIcon(KIcons.Add, Color(0xB3FFFFFF), Modifier.size(56.dp))
				imageUrl != null -> KImage(imageUrl, Modifier.fillMaxSize())
				else -> KText(name.take(1).uppercase(), style = KhyloType.Display, fontSize = 64.sp, color = Color.White)
			}
			// Kids profiles carry a small label on the picture
			kidsLabel?.let {
				KText(
					it.uppercase(),
					style = KhyloType.Caption.copy(fontWeight = FontWeight.Bold),
					color = Color.White,
					modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
						.clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 2.dp),
				)
			}
		}
		Spacer(Modifier.height(16.dp))
		KText(
			if (locked) "$name  🔒" else name,
			style = KhyloType.Label,
			color = if (focused) KhyloColors.Text else KhyloColors.TextSecondary,
			maxLines = 1,
		)
		if (current) KText("Watching now", style = KhyloType.Caption, color = KhyloColors.Accent)
	}
}
