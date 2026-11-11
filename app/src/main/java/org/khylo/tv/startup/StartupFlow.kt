package org.khylo.tv.startup

import org.khylo.tv.design.KIcons
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.khylo.tv.core.R
import org.khylo.tv.core.auth.model.ApiClientErrorLoginState
import org.khylo.tv.core.auth.model.AuthenticatedState
import org.khylo.tv.core.auth.model.AuthenticatingState
import org.khylo.tv.core.auth.model.ConnectedState
import org.khylo.tv.core.auth.model.ConnectingState
import org.khylo.tv.core.auth.model.LoginState
import org.khylo.tv.core.auth.model.PendingQuickConnectState
import org.khylo.tv.core.auth.model.RequireSignInState
import org.khylo.tv.core.auth.model.Server
import org.khylo.tv.core.auth.model.ServerUnavailableState
import org.khylo.tv.core.auth.model.ServerVersionNotSupported
import org.khylo.tv.core.auth.model.UnableToConnectState
import org.khylo.tv.core.auth.model.UnavailableQuickConnectState
import org.khylo.tv.core.auth.model.User
import org.khylo.tv.core.ui.startup.StartupViewModel
import org.khylo.tv.core.ui.startup.UserLoginViewModel
import org.khylo.tv.app.Splash
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KTextField
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.autoFocusContent
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.data.CertificateProbe
import org.khylo.tv.data.ServerCertificate
import org.khylo.tv.data.TrustedCertificates
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.UUID

private sealed interface Step {
	data object Loading : Step
	data object Servers : Step
	data object AddServer : Step
	data class Trust(val address: String, val certificate: ServerCertificate) : Step
	data class Profiles(val server: Server) : Step
	data class Password(val server: Server, val username: String) : Step
	data class Code(val server: Server) : Step
}

/** Connect to a Khylo server and sign in. Ends when the session starts (the root then shows the app). */
@Composable
fun StartupFlow(onExit: () -> Unit) {
	val startup = koinViewModel<StartupViewModel>()
	val scope = rememberCoroutineScope()
	var step by remember { mutableStateOf<Step>(Step.Loading) }
	var history by remember { mutableStateOf(listOf<Step>()) }
	var pickMessage by remember { mutableStateOf<String?>(null) }

	fun goTo(next: Step, remember: Boolean = true) {
		if (remember && step != Step.Loading) history = history + step
		step = next
	}

	LaunchedEffect(Unit) {
		val last = startup.getLastServer()
		step = if (last != null) Step.Profiles(last) else Step.Servers
		startup.loadDiscoveryServers()
	}

	fun goBack() {
		if (history.isEmpty()) onExit() else {
			step = history.last()
			history = history.dropLast(1)
		}
	}

	BackHandler { goBack() }

	Box(Modifier.fillMaxSize().background(KhyloColors.Background)) {
		when (val current = step) {
			Step.Loading -> Splash()
			Step.Servers -> ServersStep(
				startup,
				message = pickMessage,
				onPick = { picked ->
					// A server found on the network isn't saved yet; sign-in needs it saved, and it must be this server
					scope.launch {
						pickMessage = null
						var handled = false
						val saved = startup.getServer(picked.id) ?: when (val added = startup.addServer(picked.address).first { it is ConnectedState || it is UnableToConnectState }) {
							is ConnectedState -> startup.getServer(added.id)
							is UnableToConnectState -> {
								handled = true
								// A certificate this TV doesn't know is shown to the owner, who can choose to trust it
								val certificate = withContext(Dispatchers.IO) { CertificateProbe.untrusted(added) }
								if (certificate != null) goTo(Step.Trust(picked.address, certificate))
								else pickMessage = addServerFailureMessage(added, picked.address)
								null
							}
							else -> null
						}
						if (saved != null) goTo(Step.Profiles(saved))
						else if (!handled) pickMessage = "Couldn't save that server. Try again."
					}
				},
				onAdd = { goTo(Step.AddServer) },
			)
			Step.AddServer -> AddServerStep(
				startup,
				onAdded = { server -> goTo(Step.Profiles(server)) },
				onNeedsTrust = { address, certificate -> goTo(Step.Trust(address, certificate)) },
			)
			is Step.Trust -> TrustStep(
				startup,
				address = current.address,
				certificate = current.certificate,
				onTrusted = { server -> goTo(Step.Profiles(server)) },
				onCancel = ::goBack,
			)
			is Step.Profiles -> ProfilesStep(
				startup = startup,
				server = current.server,
				onNeedsTrust = { address, certificate -> goTo(Step.Trust(address, certificate)) },
				onPassword = { goTo(Step.Password(current.server, it)) },
				onCode = { goTo(Step.Code(current.server)) },
				onChangeServer = { goTo(Step.Servers) },
			)
			is Step.Password -> PasswordStep(current.server, current.username, onCode = { goTo(Step.Code(current.server)) })
			is Step.Code -> CodeStep(current.server)
		}
	}
}

@Composable
private fun StepFrame(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
	Column(
		Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp).autoFocusContent(title),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Image(painterResource(R.drawable.app_logo), contentDescription = "Khylo", modifier = Modifier.width(220.dp))
		Spacer(Modifier.height(36.dp))
		KText(title, style = KhyloType.Title)
		if (subtitle != null) {
			Spacer(Modifier.height(8.dp))
			KText(subtitle, style = KhyloType.Body, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
		}
		Spacer(Modifier.height(36.dp))
		content()
	}
}

// ---- Servers --------------------------------------------------------------------------------

@Composable
private fun ServersStep(startup: StartupViewModel, message: String?, onPick: (Server) -> Unit, onAdd: () -> Unit) {
	val stored by startup.storedServers.collectAsState()
	val discovered by startup.discoveredServers.collectAsState()
	LaunchedEffect(Unit) { startup.reloadStoredServers() }
	val servers = (stored + discovered.filter { d -> stored.none { it.id == d.id } })

	StepFrame("Choose a server", "Khylo servers you've used, and ones found on your network.") {
		if (servers.isEmpty()) {
			KText("No servers found yet.", style = KhyloType.Body)
			Spacer(Modifier.height(24.dp))
		}
		LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
			items(servers, key = { it.id.toString() + it.address }) { server ->
				// Anyone on the home network can answer the search for servers, so one this TV hasn't used before says so
				ServerTile(server, foundOnNetwork = stored.none { it.id == server.id }, onClick = { onPick(server) })
			}
		}
		Spacer(Modifier.height(32.dp))
		KButton("Add a server", onClick = onAdd, icon = KIcons.Add, style = if (servers.isEmpty()) KButtonStyle.Primary else KButtonStyle.Secondary)
		message?.let {
			Spacer(Modifier.height(18.dp))
			KText(it, style = KhyloType.Body, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
		}
	}
}

@Composable
private fun ServerTile(server: Server, foundOnNetwork: Boolean, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Column(
		Modifier
			.width(280.dp)
			.scale(if (focused) 1.05f else 1f)
			.clip(RoundedCornerShape(14.dp))
			.background(if (focused) KhyloColors.SurfaceHover else KhyloColors.Surface)
			.border(2.dp, if (focused) KhyloColors.Accent else KhyloColors.Border, RoundedCornerShape(14.dp))
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(22.dp),
	) {
		KText(server.name, style = KhyloType.Heading, maxLines = 1)
		Spacer(Modifier.height(6.dp))
		KText(server.address, style = KhyloType.Caption, maxLines = 1)
		if (foundOnNetwork) {
			Spacer(Modifier.height(8.dp))
			KText("Found on your network. Choose it only if this is your server's address.", style = KhyloType.Caption, maxLines = 3)
		}
	}
}

@Composable
private fun AddServerStep(startup: StartupViewModel, onAdded: (Server) -> Unit, onNeedsTrust: (String, ServerCertificate) -> Unit) {
	val scope = rememberCoroutineScope()
	var address by remember { mutableStateOf("") }
	var status by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }

	fun connect() {
		if (address.isBlank() || busy) return
		busy = true
		status = "Connecting…"
		scope.launch {
			startup.addServer(address.trim()).collect { state ->
				when (state) {
					is ConnectingState -> status = "Connecting to ${state.address}…"
					is UnableToConnectState -> {
						// A certificate this TV doesn't know is shown to the owner, who can choose to trust it
						val certificate = withContext(Dispatchers.IO) { CertificateProbe.untrusted(state) }
						busy = false
						if (certificate != null) onNeedsTrust(address.trim(), certificate)
						else status = addServerFailureMessage(state, address)
					}
					is ConnectedState -> {
						startup.reloadStoredServers()
						// This server, never another one this TV happens to know
						val server = startup.getServer(state.id)
						busy = false
						if (server != null) onAdded(server) else status = "Couldn't save that server. Try again."
					}
				}
			}
		}
	}

	StepFrame("Add a server", "Type the address of your Khylo server.") {
		KTextField(
			value = address,
			onValueChange = { address = it },
			label = "Server address",
			placeholder = "192.168.1.20:8096 or khylo.example.com",
			keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
			onDone = ::connect,
			modifier = Modifier.width(560.dp),
		)
		Spacer(Modifier.height(24.dp))
		KButton(if (busy) "Connecting…" else "Connect", onClick = ::connect, style = KButtonStyle.Primary, enabled = !busy)
		status?.let {
			Spacer(Modifier.height(18.dp))
			KText(it, style = KhyloType.Body)
		}
	}
}

// ---- Trusting a certificate -----------------------------------------------------------------

/**
 * The server's certificate isn't from an authority this TV knows (a self-signed one, or one from a home-made authority). The
 * owner looks at it and chooses whether to trust it; the TV then remembers that certificate's key.
 */
@Composable
private fun TrustStep(
	startup: StartupViewModel,
	address: String,
	certificate: ServerCertificate,
	onTrusted: (Server) -> Unit,
	onCancel: () -> Unit,
) {
	val scope = rememberCoroutineScope()
	val trusted = koinInject<TrustedCertificates>()
	var status by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }

	fun trustAndConnect() {
		if (busy) return
		busy = true
		status = "Connecting…"
		scope.launch {
			trusted.trust(certificate.certificate)
			startup.addServer(address).collect { state ->
				when (state) {
					is ConnectedState -> {
						startup.reloadStoredServers()
						val server = startup.getServer(state.id)
						busy = false
						if (server != null) onTrusted(server) else status = "Couldn't save that server. Try again."
					}
					is UnableToConnectState -> {
						status = addServerFailureMessage(state, address)
						busy = false
					}
					is ConnectingState -> Unit
				}
			}
		}
	}

	StepFrame(
		"Trust this server?",
		"This server's certificate isn't from an authority this TV knows. That's normal for a certificate you made yourself. " +
			"Check that it is yours, then choose Trust. If you trusted a certificate for this server before, it has been replaced.",
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			KText(address, style = KhyloType.Caption, maxLines = 1)
			Spacer(Modifier.height(12.dp))
			KText(certificate.name, style = KhyloType.Heading, maxLines = 1)
			KText(
				if (certificate.selfSigned) "Made by the server itself (self-signed)" else "Issued by ${certificate.issuer}",
				style = KhyloType.Body,
			)
			KText("Valid until ${certificate.validUntil}", style = KhyloType.Body)
			Spacer(Modifier.height(16.dp))
			KText("SHA-256 fingerprint", style = KhyloType.Caption)
			KText(certificate.fingerprint.chunked(48).joinToString("\n") { it.trimEnd(':') }, style = KhyloType.Body, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
			Spacer(Modifier.height(28.dp))
			// Cancel comes first, so it is the one the remote lands on
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				KButton("Cancel", onClick = onCancel, style = KButtonStyle.Secondary)
				KButton(if (busy) "Connecting…" else "Trust this server", onClick = ::trustAndConnect, style = KButtonStyle.Primary, enabled = !busy)
			}
			status?.let {
				Spacer(Modifier.height(18.dp))
				KText(it, style = KhyloType.Body, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
			}
		}
	}
}

// ---- Profiles -------------------------------------------------------------------------------

@Composable
private fun ProfilesStep(
	startup: StartupViewModel,
	server: Server,
	onPassword: (String) -> Unit,
	onCode: () -> Unit,
	onChangeServer: () -> Unit,
	onNeedsTrust: (String, ServerCertificate) -> Unit,
) {
	val users by startup.users.collectAsState()
	val scope = rememberCoroutineScope()
	var signingIn by remember { mutableStateOf<UUID?>(null) }
	var message by remember { mutableStateOf<String?>(null) }
	LaunchedEffect(server) { startup.loadUsers(server) }

	// A secure server whose profiles don't load may have a new certificate; if so, the owner is asked about it
	LaunchedEffect(server, users.isEmpty()) {
		if (users.isEmpty() && server.address.startsWith("https://", ignoreCase = true)) {
			delay(3_000)
			if (startup.users.value.isEmpty()) {
				val state = startup.addServer(server.address).first { it is ConnectedState || it is UnableToConnectState }
				if (state is UnableToConnectState) {
					withContext(Dispatchers.IO) { CertificateProbe.untrusted(state) }?.let { onNeedsTrust(server.address, it) }
				}
			}
		}
	}

	fun pick(user: User) {
		signingIn = user.id
		message = null
		scope.launch {
			startup.authenticate(server, user).collect { state ->
				when (state) {
					RequireSignInState -> {
						signingIn = null
						onPassword(user.name)
					}
					ServerUnavailableState -> {
						signingIn = null
						message = "The server isn't answering right now."
					}
					is ServerVersionNotSupported -> {
						signingIn = null
						message = "This server needs an update before Khylo TV can use it."
					}
					is ApiClientErrorLoginState -> {
						signingIn = null
						message = "Couldn't sign in. Try again."
					}
					AuthenticatingState, AuthenticatedState -> Unit
				}
			}
		}
	}

	StepFrame("Who's watching?", server.name) {
		LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
			items(users, key = { it.id }) { user ->
				ProfileTile(
					name = user.name,
					imageUrl = startup.getUserImage(server, user),
					busy = signingIn == user.id,
					onClick = { pick(user) },
				)
			}
		}
		message?.let {
			Spacer(Modifier.height(20.dp))
			KText(it, style = KhyloType.Body)
		}
		Spacer(Modifier.height(40.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
			KButton("Sign in with a code", onClick = onCode, icon = KIcons.Code, style = KButtonStyle.Primary)
			KButton("Other account", onClick = { onPassword("") }, icon = KIcons.AddPerson)
			KButton("Change server", onClick = onChangeServer, icon = KIcons.SwitchProfile, style = KButtonStyle.Ghost)
		}
	}
}

@Composable
private fun ProfileTile(name: String, imageUrl: String?, busy: Boolean, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
		Box(
			Modifier
				.size(132.dp)
				.scale(if (focused) 1.08f else 1f)
				.clip(CircleShape)
				.background(KhyloColors.SurfaceRaised)
				.border(4.dp, if (focused) KhyloColors.Accent else KhyloColors.Border, CircleShape)
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			if (imageUrl != null) KImage(imageUrl, Modifier.fillMaxSize())
			else KText(name.take(1).uppercase(), style = KhyloType.Display, color = KhyloColors.Accent)
			if (busy) KSpinner(size = 48.dp)
		}
		Spacer(Modifier.height(14.dp))
		KText(name, style = KhyloType.Label, color = if (focused) KhyloColors.Text else KhyloColors.TextSecondary, maxLines = 1)
	}
}

// ---- Password -------------------------------------------------------------------------------

@Composable
private fun PasswordStep(server: Server, initialUsername: String, onCode: () -> Unit) {
	val login = koinViewModel<UserLoginViewModel>()
	LaunchedEffect(server) { login.setServer(server.id) }
	val state by login.loginState.collectAsState()
	var username by remember { mutableStateOf(initialUsername) }
	var password by remember { mutableStateOf("") }
	val passwordFocus = remember { FocusRequester() }

	LaunchedEffect(Unit) { if (initialUsername.isNotEmpty()) runCatching { passwordFocus.requestFocus() } }

	val busy = state == AuthenticatingState
	val error = when (state) {
		is ApiClientErrorLoginState, RequireSignInState -> "That name or password didn't work."
		ServerUnavailableState -> "The server isn't answering right now."
		is ServerVersionNotSupported -> "This server needs an update before Khylo TV can use it."
		else -> null
	}

	StepFrame("Sign in", server.name) {
		Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
			KTextField(username, { username = it }, "Name", placeholder = "Your name")
			KTextField(
				password, { password = it }, "Password",
				isPassword = true,
				onDone = { login.login(username, password) },
				modifier = Modifier.focusRequester(passwordFocus),
			)
		}
		Spacer(Modifier.height(24.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
			KButton(if (busy) "Signing in…" else "Sign in", onClick = { login.login(username, password) }, style = KButtonStyle.Primary, enabled = !busy && username.isNotBlank())
			KButton("Use a code instead", onClick = onCode, icon = KIcons.Code)
		}
		// Plain http is only ever used on the home network, but the password isn't encrypted on the way
		if (server.address.startsWith("http://", ignoreCase = true)) {
			Spacer(Modifier.height(14.dp))
			KText("This connection isn't encrypted. Check that it's your own server.", style = KhyloType.Caption)
		}
		error?.let {
			Spacer(Modifier.height(18.dp))
			KText(it, style = KhyloType.Body, color = Color(0xFFEF8F8F))
		}
	}
}

// ---- Sign in with a code --------------------------------------------------------------------

/**
 * Shows a code and a QR code; approving it on a phone or computer that's signed in (Add a
 * device) signs this TV in.
 */
@Composable
private fun CodeStep(server: Server) {
	val login = koinViewModel<UserLoginViewModel>()
	val http = koinInject<OkHttpClient>()
	var missingServer by remember { mutableStateOf(false) }
	LaunchedEffect(server) {
		login.setServer(server.id)
		missingServer = login.server.value == null
		login.clearLoginState()
		login.initiateQuickconnect()
	}
	val state by login.quickConnectState.collectAsState()
	val code = (state as? PendingQuickConnectState)?.code

	// Where a phone goes to approve: the server's web app
	val webBase by produceState<String?>(null, server.address) { value = findWebApp(http, server.address) }

	StepFrame("Sign in with a code", "On your phone or computer, open Khylo → Add a device, and enter this code. Or scan the QR code.") {
		when {
			state is UnavailableQuickConnectState -> KText("Signing in with a code is turned off on this server.", style = KhyloType.Body)
			missingServer -> KText("Couldn't start sign-in for this server. Go back and add it with its address.", style = KhyloType.Body)
			code == null -> KSpinner()
			else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(64.dp)) {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						code.forEach { ch ->
							Box(
								Modifier
									.size(width = 72.dp, height = 92.dp)
									.clip(RoundedCornerShape(14.dp))
									.background(KhyloColors.SurfaceRaised)
									.border(1.dp, KhyloColors.BorderStrong, RoundedCornerShape(14.dp)),
								contentAlignment = Alignment.Center,
							) {
								KText(ch.toString(), style = KhyloType.Display)
							}
						}
					}
					Spacer(Modifier.height(28.dp))
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
						Box(Modifier.size(10.dp).clip(CircleShape).background(KhyloColors.Accent))
						KText("Waiting for approval. This screen signs in by itself.", style = KhyloType.Caption)
					}
					// The address to type when a QR code can't be scanned
					webBase?.let { base ->
						val uri = android.net.Uri.parse(base)
						val where = uri.host?.let { host -> if (uri.port != -1) "$host:${uri.port}" else host }
						if (where != null) {
							Spacer(Modifier.height(14.dp))
							KText("Or on any device, go to $where → Add a device", style = KhyloType.Caption)
						}
					}
				}
				webBase?.let { base ->
					val qr = remember(base, code) { qrBitmap("${base}#/quickconnect?code=$code", 600) }
					Box(Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)) {
						Image(qr.asImageBitmap(), contentDescription = "QR code to approve this TV", modifier = Modifier.fillMaxSize())
					}
				}
			}
		}
	}
}

/**
 * The address of the server's web app, for the QR code. The server's public web address comes first
 * (https://khylo.example.com), so a phone that isn't on the home network can still open it. Without one,
 * the address this TV uses: a normal install serves the web app at /web, and a development server
 * running without it may have it on its own port.
 */
private suspend fun findWebApp(http: OkHttpClient, serverAddress: String): String = withContext(Dispatchers.IO) {
	val base = serverAddress.trimEnd('/')
	publicWebAddress(http, base)?.let { return@withContext "$it/" }
	val candidates = buildList {
		add("$base/web/")
		val uri = android.net.Uri.parse(base)
		if (uri.port != -1 && uri.port != 8081) add("${uri.scheme}://${uri.host}:8081/") // dev web server
	}
	candidates.firstOrNull { url ->
		runCatching {
			http.newCall(Request.Builder().url(url).head().build()).execute().use { it.isSuccessful }
		}.getOrDefault(false)
	} ?: "$base/web/"
}

/** The server's public web address, if it has one (Khylo/WebAddress). */
private fun publicWebAddress(http: OkHttpClient, base: String): String? = runCatching {
	http.newCall(Request.Builder().url("$base/Khylo/WebAddress").build()).execute().use { response ->
		if (!response.isSuccessful) return@use null
		val body = response.body?.string().orEmpty()
		Regex("\"Address\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.trimEnd('/')?.takeIf { it.startsWith("http") }
	}
}.getOrNull()

private fun qrBitmap(text: String, size: Int): Bitmap {
	val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
	val bitmap = createBitmap(size, size)
	for (x in 0 until size) for (y in 0 until size) {
		bitmap[x, y] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
	}
	return bitmap
}

@Suppress("unused")
private fun LoginState.isDone() = this == AuthenticatedState
