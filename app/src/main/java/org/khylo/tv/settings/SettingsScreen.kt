package org.khylo.tv.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.khylo.tv.core.BuildConfig
import org.khylo.tv.core.auth.repository.AuthenticationRepository
import org.khylo.tv.core.auth.repository.ServerRepository
import org.khylo.tv.core.auth.repository.ServerUserRepository
import org.khylo.tv.core.auth.repository.SessionRepository
import org.khylo.tv.core.auth.repository.UserRepository
import org.khylo.sdk.model.api.SubtitlePlaybackMode
import org.khylo.sdk.model.api.UserConfiguration
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloPrefs
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.koin.compose.koinInject

private enum class Section(val title: String, val icon: ImageVector) {
	Account("Profile & account", KIcons.SwitchProfile),
	Playback("Playback", KIcons.Play),
	Language("Audio & subtitles", KIcons.Sound),
	Home("Home screen", KIcons.Home),
	Device("Server & device", KIcons.Tv),
	About("About", KIcons.Info),
}

/** Languages offered for audio and subtitles, as ISO 639-2 codes the server matches on. */
private val languages: List<Pair<String, String?>> = listOf(
	"No preference" to null,
	"English" to "eng",
	"Spanish" to "spa",
	"French" to "fre",
	"German" to "ger",
	"Italian" to "ita",
	"Portuguese" to "por",
	"Japanese" to "jpn",
	"Korean" to "kor",
	"Chinese" to "chi",
	"Hindi" to "hin",
	"Arabic" to "ara",
	"Russian" to "rus",
)

private val subtitleModes: List<Pair<String, SubtitlePlaybackMode>> = listOf(
	"Use the file's default" to SubtitlePlaybackMode.DEFAULT,
	"Smart (on for other languages)" to SubtitlePlaybackMode.SMART,
	"Always on" to SubtitlePlaybackMode.ALWAYS,
	"Only forced (signs, foreign lines)" to SubtitlePlaybackMode.ONLY_FORCED,
	"Off" to SubtitlePlaybackMode.NONE,
)

/**
 * Settings, the TV way: sections down the left, their options on the right. Profile preferences
 * (languages, auto-play, ...) are saved on the server so they follow the profile to every screen;
 * streaming quality belongs to this TV.
 */
@Composable
fun SettingsScreen() {
	var section by remember { mutableStateOf(Section.Account) }

	Row(Modifier.fillMaxSize().padding(start = KhyloSpacing.Gutter, end = KhyloSpacing.Gutter, top = 24.dp, bottom = 32.dp)) {
		Column(Modifier.width(280.dp).fillMaxHeight()) {
			KText("Settings", style = KhyloType.Display)
			Spacer(Modifier.height(24.dp))
			Section.entries.forEach { entry ->
				SectionTab(entry, entry == section) { section = entry }
				Spacer(Modifier.height(6.dp))
			}
		}
		Spacer(Modifier.width(32.dp))
		// A hairline between the sections and their options
		Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 70.dp).background(Color(0x1FFFFFFF)))
		Spacer(Modifier.width(32.dp))
		Column(
			Modifier
				.weight(1f)
				.fillMaxHeight()
				.verticalScroll(rememberScrollState())
				.padding(top = 70.dp, start = 8.dp, end = 8.dp, bottom = 32.dp),
		) {
			KText(section.title.uppercase(), style = KhyloType.Eyebrow)
			Spacer(Modifier.height(16.dp))
			when (section) {
				Section.Account -> AccountSettings()
				Section.Playback -> ProfileSettings { config, save -> PlaybackSettings(config, save) }
				Section.Language -> ProfileSettings { config, save -> LanguageSettings(config, save) }
				Section.Home -> ProfileSettings { config, save -> HomeSettings(config, save) }
				Section.Device -> DeviceSettings()
				Section.About -> AboutSettings()
			}
		}
	}
}

/** Loads the profile's preferences, and saves each change straight back to the server. */
@Composable
private fun ProfileSettings(content: @Composable (UserConfiguration, (UserConfiguration) -> Unit) -> Unit) {
	val repository = koinInject<KhyloRepository>()
	val scope = rememberCoroutineScope()
	var config by remember { mutableStateOf<UserConfiguration?>(null) }
	var failed by remember { mutableStateOf(false) }
	LaunchedEffect(Unit) {
		runCatching { repository.userConfiguration() }.onSuccess { config = it }.onFailure { failed = true }
	}
	val current = config
	when {
		current != null -> content(current) { updated ->
			config = updated
			scope.launch { runCatching { repository.saveUserConfiguration(updated) } }
		}
		failed -> KText("Couldn't reach the server to load your preferences.", style = KhyloType.Body)
		else -> KSpinner()
	}
}

@Composable
private fun AccountSettings() {
	val navigator = navigator()
	val sessionRepository = koinInject<SessionRepository>()
	val userRepository = koinInject<UserRepository>()
	val serverRepository = koinInject<ServerRepository>()
	val serverUsers = koinInject<ServerUserRepository>()
	val authentication = koinInject<AuthenticationRepository>()
	val user by userRepository.currentUser.collectAsState()
	val server by serverRepository.currentServer.collectAsState()

	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
		Box(Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(KhyloColors.Accent), contentAlignment = Alignment.Center) {
			KText(user?.name?.take(1)?.uppercase().orEmpty(), style = KhyloType.Title, color = Color.White)
		}
		Column {
			KText(user?.name.orEmpty(), style = KhyloType.Heading)
			KText(if (user?.policy?.isAdministrator == true) "Server admin" else "Home profile", style = KhyloType.Caption)
		}
	}
	Spacer(Modifier.height(28.dp))
	SettingRow("Switch profile", "Pick who's watching, or add someone to this home", "Open") { navigator.go(Route.Profiles) }
	SettingRow("Sign in another device", "Show a QR code your phone scans to sign in as you", "Show code") { navigator.go(Route.SignInAnotherDevice) }
	SettingRow("Sign out of this TV", "You'll need to sign in again with a code or password", "Sign out") {
		val current = server
		val stored = current?.let { serverUsers.getStoredServerUsers(it) }?.firstOrNull { it.id == user?.id }
		if (stored != null) authentication.logout(stored)
		sessionRepository.destroyCurrentSession()
	}
}

@Composable
private fun PlaybackSettings(config: UserConfiguration, save: (UserConfiguration) -> Unit) {
	val context = LocalContext.current
	val prefs = remember { KhyloPrefs(context) }
	var bitrate by remember { mutableStateOf(prefs.maxBitrate) }
	val qualityIndex = KhyloPrefs.qualityOptions.indexOfFirst { it.second == bitrate }.coerceAtLeast(0)

	SettingRow(
		"Streaming quality",
		"Lower this if video buffers on this TV's connection",
		KhyloPrefs.qualityOptions[qualityIndex].first,
	) {
		val next = KhyloPrefs.qualityOptions[(qualityIndex + 1) % KhyloPrefs.qualityOptions.size].second
		prefs.maxBitrate = next
		bitrate = next
	}
	var skipIntro by remember { mutableStateOf(prefs.autoSkipIntro) }
	var skipCredits by remember { mutableStateOf(prefs.autoSkipCredits) }
	ToggleRow("Auto-skip intros", "Jump past theme songs and recaps without pressing Skip", skipIntro) {
		prefs.autoSkipIntro = it
		skipIntro = it
	}
	ToggleRow("Auto-skip credits", "At the end credits, go straight to the next episode", skipCredits) {
		prefs.autoSkipCredits = it
		skipCredits = it
	}
	ToggleRow("Auto-play next episode", "Roll straight into the next episode when one ends", config.enableNextEpisodeAutoPlay) {
		save(config.copy(enableNextEpisodeAutoPlay = it))
	}
	ToggleRow("Play the default audio track", "Use the track marked default, even if it's not your language", config.playDefaultAudioTrack) {
		save(config.copy(playDefaultAudioTrack = it))
	}
}

@Composable
private fun LanguageSettings(config: UserConfiguration, save: (UserConfiguration) -> Unit) {
	CycleRow("Audio language", "Preferred language for the soundtrack", languages, config.audioLanguagePreference?.ifBlank { null }) {
		save(config.copy(audioLanguagePreference = it))
	}
	CycleRow("Subtitles", "When subtitles turn on by themselves", subtitleModes, config.subtitleMode) {
		save(config.copy(subtitleMode = it))
	}
	CycleRow("Subtitle language", "Preferred language for subtitles", languages, config.subtitleLanguagePreference?.ifBlank { null }) {
		save(config.copy(subtitleLanguagePreference = it))
	}
	ToggleRow("Remember audio choices", "Keep the track you picked for the rest of a show", config.rememberAudioSelections) {
		save(config.copy(rememberAudioSelections = it))
	}
	ToggleRow("Remember subtitle choices", "Keep the subtitles you picked for the rest of a show", config.rememberSubtitleSelections) {
		save(config.copy(rememberSubtitleSelections = it))
	}
}

@Composable
private fun HomeSettings(config: UserConfiguration, save: (UserConfiguration) -> Unit) {
	ToggleRow("Hide watched in Recently added", "Only show new things you haven't seen yet", config.hidePlayedInLatest) {
		save(config.copy(hidePlayedInLatest = it))
	}
	ToggleRow("Show missing episodes", "List episodes you don't have yet inside each season", config.displayMissingEpisodes) {
		save(config.copy(displayMissingEpisodes = it))
	}
	ToggleRow("Show collections", "Group movie series (like trilogies) into collections", config.displayCollectionsView) {
		save(config.copy(displayCollectionsView = it))
	}
}

@Composable
private fun DeviceSettings() {
	val repository = koinInject<KhyloRepository>()
	val serverRepository = koinInject<ServerRepository>()
	val server by serverRepository.currentServer.collectAsState()
	val version by produceState<String?>(null) { value = repository.serverVersion() }

	InfoRow("Server", server?.name.orEmpty())
	InfoRow("Address", server?.address.orEmpty())
	InfoRow("Server version", version ?: "…")
	Spacer(Modifier.height(16.dp))
	InfoRow("This TV", listOfNotNull(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).joinToString(" "))
	InfoRow("Android", "Android ${Build.VERSION.RELEASE}")
	InfoRow("App", "Khylo TV ${BuildConfig.VERSION_NAME}")
}

@Composable
private fun AboutSettings() {
	val user by koinInject<UserRepository>().currentUser.collectAsState()
	// Kids profiles (limited by rating) don't get the alien
	val grownUp = user?.policy?.maxParentalRating == null
	val klyrp = remember { KlyrpCounter() }

	KText("Khylo TV", style = KhyloType.Heading)
	val (source, focused) = rememberFocusState()
	KText(
		"Build ${BuildConfig.KHYLO_BUILD}",
		style = KhyloType.Caption,
		color = if (focused) KhyloColors.OnFocus else KhyloColors.TextMuted,
		modifier = Modifier
			.padding(top = 2.dp)
			.clip(RoundedCornerShape(6.dp))
			.background(if (focused) KhyloColors.Focus else Color.Transparent)
			.clickable(interactionSource = source, indication = null) { if (grownUp) klyrp.tap() }
			.padding(horizontal = 6.dp, vertical = 2.dp),
	)
	klyrp.hint?.let { hint ->
		LaunchedEffect(hint) {
			kotlinx.coroutines.delay(1800)
			klyrp.clearHint()
		}
		KText(hint, style = KhyloType.Caption, color = KhyloColors.Accent, modifier = Modifier.padding(top = 6.dp))
	}
	KlyrpVisit(klyrp)
	Spacer(Modifier.height(16.dp))
	KText(
		"Khylo is free and open-source software for your own media: your movies, shows, music and live TV, " +
			"streamed from your server to every screen at home.",
		style = KhyloType.Body,
	)
	Spacer(Modifier.height(16.dp))
	KText("Licensed under the GNU General Public License v2. Built on open-source components whose licenses travel with the source code.", style = KhyloType.Caption)
	// A focus target so the panel can be reached and scrolled with the remote
	Spacer(Modifier.height(1.dp).focusable())
}

// ---- Rows ---------------------------------------------------------------------------------

@Composable
private fun SectionTab(section: Section, selected: Boolean, onSelect: () -> Unit) {
	val (source, focused) = rememberFocusState()
	// Moving through the list previews each section, like a TV's own settings
	LaunchedEffect(focused) { if (focused) onSelect() }
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.background(
				when {
					focused -> KhyloColors.Focus
					else -> Color.Transparent
				}
			)
			.clickable(interactionSource = source, indication = null, onClick = onSelect)
			.padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		KIcon(section.icon, if (focused) KhyloColors.OnFocus else if (selected) KhyloColors.Accent else KhyloColors.TextMuted, Modifier.size(22.dp))
		KText(
			section.title,
			style = KhyloType.Label.copy(fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium),
			color = if (focused) KhyloColors.OnFocus else if (selected) KhyloColors.Text else Color(0x99FFFFFF),
			maxLines = 1,
		)
	}
}

/** One option: what it is on the left, its current value on the right. OK changes it. */
@Composable
private fun SettingRow(label: String, description: String?, value: String, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		Modifier
			.fillMaxWidth()
			.padding(bottom = 2.dp)
			.clip(RoundedCornerShape(12.dp))
			.background(if (focused) KhyloColors.Focus else Color.Transparent)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 20.dp, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(Modifier.weight(1f)) {
			KText(label, style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), color = if (focused) KhyloColors.OnFocus else KhyloColors.Text)
			description?.let { KText(it, style = KhyloType.Caption, color = if (focused) Color(0xFF555555) else KhyloColors.TextMuted, maxLines = 1) }
		}
		Spacer(Modifier.width(16.dp))
		KText(value, style = KhyloType.Label, color = if (focused) KhyloColors.OnFocus else KhyloColors.TextSecondary, maxLines = 1)
		KIcon(KIcons.Later, if (focused) KhyloColors.OnFocus else KhyloColors.TextMuted, Modifier.size(22.dp))
	}
}

@Composable
internal fun ToggleRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
	val (source, focused) = rememberFocusState()
	Row(
		Modifier
			.fillMaxWidth()
			.padding(bottom = 2.dp)
			.clip(RoundedCornerShape(12.dp))
			.background(if (focused) KhyloColors.Focus else Color.Transparent)
			.clickable(interactionSource = source, indication = null) { onChange(!checked) }
			.padding(horizontal = 20.dp, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(Modifier.weight(1f)) {
			KText(label, style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold), color = if (focused) KhyloColors.OnFocus else KhyloColors.Text)
			KText(description, style = KhyloType.Caption, color = if (focused) Color(0xFF555555) else KhyloColors.TextMuted, maxLines = 1)
		}
		Spacer(Modifier.width(16.dp))
		// A small switch
		Box(
			Modifier
				.width(46.dp)
				.height(26.dp)
				.clip(RoundedCornerShape(50))
				.background(if (checked) KhyloColors.Accent else if (focused) Color(0xFFBBBBBB) else Color(0x40FFFFFF))
				.padding(3.dp),
			contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
		) {
			Box(Modifier.size(20.dp).clip(RoundedCornerShape(50)).background(Color.White))
		}
	}
}

@Composable
private fun <T> CycleRow(label: String, description: String, options: List<Pair<String, T>>, value: T, onChange: (T) -> Unit) {
	val index = options.indexOfFirst { it.second == value }.coerceAtLeast(0)
	SettingRow(label, description, options[index].first) { onChange(options[(index + 1) % options.size].second) }
}

@Composable
private fun InfoRow(label: String, value: String) {
	Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
		KText(label, style = KhyloType.Meta, color = KhyloColors.TextMuted, modifier = Modifier.width(180.dp))
		KText(value, style = KhyloType.Meta, color = KhyloColors.Text, maxLines = 1)
	}
}

