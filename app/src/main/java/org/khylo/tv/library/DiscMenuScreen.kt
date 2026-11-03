package org.khylo.tv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.design.KButton
import org.khylo.tv.design.KButtonStyle
import org.khylo.tv.design.KCard
import org.khylo.tv.design.KCardShape
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloType
import org.koin.compose.koinInject

@Serializable
private data class DiscChapterDto(
	@SerialName("Number") val number: Int,
	@SerialName("StartSeconds") val startSeconds: Double,
	@SerialName("StartPositionTicks") val startPositionTicks: Long,
)

@Serializable
private data class DiscAudioDto(
	@SerialName("Index") val index: Int,
	@SerialName("Codec") val codec: String = "",
	@SerialName("Channels") val channels: Int = 2,
	@SerialName("LanguageName") val languageName: String? = null,
	@SerialName("Note") val note: String? = null,
)

@Serializable
private data class DiscSubtitleDto(
	@SerialName("Index") val index: Int,
	@SerialName("LanguageName") val languageName: String? = null,
	@SerialName("Note") val note: String? = null,
)

@Serializable
private data class DiscTitleDto(
	@SerialName("Number") val number: Int,
	@SerialName("IsMain") val isMain: Boolean = false,
	@SerialName("MediaSourceId") val mediaSourceId: String,
	@SerialName("Seconds") val seconds: Double = 0.0,
	@SerialName("Chapters") val chapters: List<DiscChapterDto> = emptyList(),
	@SerialName("Audio") val audio: List<DiscAudioDto> = emptyList(),
	@SerialName("Subtitles") val subtitles: List<DiscSubtitleDto> = emptyList(),
)

@Serializable
private data class DiscDto(
	@SerialName("IsScrambled") val isScrambled: Boolean = false,
	@SerialName("HasMenuImage") val hasMenuImage: Boolean = false,
	@SerialName("MainTitle") val mainTitle: Int = 0,
	@SerialName("Titles") val titles: List<DiscTitleDto> = emptyList(),
)

private sealed interface DiscState {
	data object Loading : DiscState
	data object Failed : DiscState
	data class Ready(val disc: DiscDto) : DiscState
}

private enum class DiscSection(val label: String) { Play("Play"), Scenes("Scenes"), Tracks("Audio & Subtitles"), Extras("Extras") }

private val json = Json { ignoreUnknownKeys = true }

private fun clock(seconds: Double): String {
	val total = seconds.toLong().coerceAtLeast(0)
	val h = total / 3600
	val m = (total % 3600) / 60
	val s = total % 60
	return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * A DVD's menu, made from what's on the disc: play, jump to a scene, play an extra. The disc's own menu
 * picture sits softly behind it. Sound and subtitle choices are in the player, as for any film.
 */
@Composable
fun DiscMenuScreen(item: BaseItemDto) {
	val api = koinInject<ApiClient>()
	val http = koinInject<OkHttpClient>()
	val navigator = navigator()

	val state by produceState<DiscState>(DiscState.Loading, item.id) {
		value = withContext(Dispatchers.IO) {
			runCatching {
				val request = Request.Builder()
					.url("${api.baseUrl}/Khylo/Disc/${item.id}")
					.header("Authorization", "MediaBrowser Token=\"${api.accessToken}\"")
					.get()
					.build()
				http.newCall(request).execute().use { response ->
					check(response.isSuccessful) { "HTTP ${response.code}" }
					DiscState.Ready(json.decodeFromString(DiscDto.serializer(), response.body?.string().orEmpty()))
				}
			}.getOrElse { DiscState.Failed }
		}
	}

	fun picture(query: String) = "${api.baseUrl}/Khylo/Disc/${item.id}/Image?$query&api_key=${api.accessToken}"

	val disc = (state as? DiscState.Ready)?.disc
	var section by remember { mutableStateOf(DiscSection.Play) }
	// The disc's tracks in its own order; the feature's streams on the server follow the same order
	var audioPick by remember { mutableStateOf(0) }
	var subtitlePick by remember { mutableStateOf(-1) }
	val streams = item.mediaSources?.firstOrNull()?.mediaStreams.orEmpty()
	val audioStreamIndexes = streams.filter { it.type == org.khylo.sdk.model.api.MediaStreamType.AUDIO }.map { it.index }
	val subtitleStreamIndexes = streams.filter { it.type == org.khylo.sdk.model.api.MediaStreamType.SUBTITLE }.map { it.index }
	fun playFeature(startMs: Long) {
		navigator.go(
			Route.Player(
				item,
				startMs,
				audioStreamIndex = audioStreamIndexes.getOrNull(audioPick)?.takeIf { audioPick > 0 },
				subtitleStreamIndex = if (subtitlePick >= 0) subtitleStreamIndexes.getOrNull(subtitlePick) else null,
			)
		)
	}
	val main = disc?.titles?.firstOrNull { it.isMain } ?: disc?.titles?.firstOrNull()
	val extras = disc?.titles?.filter { it != main }.orEmpty()
	val resumeMs = (item.userData?.playbackPositionTicks ?: 0L) / 10_000
	val playFocus = remember { FocusRequester() }

	Box(Modifier.fillMaxSize().background(KhyloColors.Background)) {
		if (disc?.hasMenuImage == true) {
			KImage(picture("menu=true"), Modifier.fillMaxSize().blur(60.dp), ContentScale.Crop)
		}
		Box(
			Modifier.fillMaxSize().background(
				Brush.horizontalGradient(
					0f to KhyloColors.Background.copy(alpha = 0.95f),
					0.45f to KhyloColors.Background.copy(alpha = 0.78f),
					1f to KhyloColors.Background.copy(alpha = 0.6f),
				)
			)
		)

		when {
			state is DiscState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KSpinner() }
			state is DiscState.Failed || disc == null || main == null || disc.isScrambled -> Column(Modifier.padding(80.dp)) {
				KText(if (disc?.isScrambled == true) "This disc is copy-protected" else "This disc can't be read", style = KhyloType.Title)
				Spacer(Modifier.height(12.dp))
				KText(
					if (disc?.isScrambled == true) {
						"Discs copied straight from a store-bought DVD are scrambled, and Khylo can't play them. Use a copy made without the protection."
					} else {
						"Khylo couldn't read this disc's contents. The file may be damaged or not a DVD."
					},
					style = KhyloType.Body,
					maxLines = 4,
				)
				Spacer(Modifier.height(24.dp))
				KButton("Back", onClick = { navigator.back() }, modifier = Modifier.focusRequester(playFocus))
			}
			else -> Row(Modifier.fillMaxSize().padding(start = 80.dp, end = 64.dp, top = 72.dp, bottom = 40.dp)) {
				Column(Modifier.width(340.dp)) {
					KText("DVD", style = KhyloType.Eyebrow, color = KhyloColors.Accent)
					Spacer(Modifier.height(10.dp))
					KText(item.name.orEmpty(), style = KhyloType.Display, maxLines = 3)
					Spacer(Modifier.height(32.dp))
					Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
						val sections = buildList {
							add(DiscSection.Play)
							if (main.chapters.size > 1) add(DiscSection.Scenes)
							if (main.audio.size > 1 || main.subtitles.isNotEmpty()) add(DiscSection.Tracks)
							if (extras.isNotEmpty()) add(DiscSection.Extras)
						}
						sections.forEach { s ->
							KButton(
								s.label,
								onClick = { section = s },
								icon = when (s) { DiscSection.Play -> KIcons.Play; DiscSection.Scenes -> KIcons.Grid; DiscSection.Tracks -> KIcons.Sound; DiscSection.Extras -> KIcons.Collection },
								style = if (section == s) KButtonStyle.Primary else KButtonStyle.Secondary,
								modifier = if (s == DiscSection.Play) Modifier.focusRequester(playFocus) else Modifier,
							)
						}
					}
				}
				Spacer(Modifier.width(56.dp))
				Box(Modifier.fillMaxSize()) {
					when (section) {
						DiscSection.Play -> Column(Modifier.padding(top = 12.dp)) {
							KText("${clock(main.seconds)}  ·  ${main.chapters.size} chapters", style = KhyloType.Body)
							Spacer(Modifier.height(24.dp))
							Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
								KButton(
									if (resumeMs > 0) "Resume" else "Play movie",
									onClick = { playFeature(if (resumeMs > 0) resumeMs else 0L) },
									icon = KIcons.Play,
									style = KButtonStyle.Primary,
								)
								if (resumeMs > 0) KButton("From the beginning", onClick = { playFeature(0L) }, icon = KIcons.Retry)
							}
						}
						DiscSection.Scenes -> LazyVerticalGrid(
							columns = GridCells.Adaptive(250.dp),
							contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
							horizontalArrangement = Arrangement.spacedBy(20.dp),
							verticalArrangement = Arrangement.spacedBy(20.dp),
						) {
							items(main.chapters) { chapter ->
								KCard(
									imageUrl = picture("seconds=${chapter.startSeconds.toLong() + 3}"),
									title = "Scene ${chapter.number}",
									subtitle = clock(chapter.startSeconds),
									shape = KCardShape.Wide,
									width = 250.dp,
									showTitle = true,
									onClick = { playFeature(chapter.startPositionTicks / 10_000) },
								)
							}
						}
						DiscSection.Tracks -> androidx.compose.foundation.lazy.LazyColumn(
							contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							item { KText("Audio", style = KhyloType.Eyebrow) }
							items(main.audio.size) { i ->
								val a = main.audio[i]
								val codec = when (a.codec) { "ac3" -> "Dolby Digital"; "dts" -> "DTS"; else -> a.codec.uppercase() }
								val channels = if (a.channels >= 6) "5.1" else if (a.channels == 1) "Mono" else "Stereo"
								KButton(
									listOfNotNull(a.languageName ?: "Track ${i + 1}", "$codec $channels", a.note).joinToString(" · "),
									onClick = { audioPick = i },
									style = if (audioPick == i) KButtonStyle.Primary else KButtonStyle.Secondary,
								)
							}
							item { Spacer(Modifier.height(12.dp)); KText("Subtitles", style = KhyloType.Eyebrow) }
							item {
								KButton("Off", onClick = { subtitlePick = -1 }, style = if (subtitlePick == -1) KButtonStyle.Primary else KButtonStyle.Secondary)
							}
							items(main.subtitles.size) { i ->
								val s = main.subtitles[i]
								KButton(
									listOfNotNull(s.languageName ?: "Subtitles ${i + 1}", s.note).joinToString(" · "),
									onClick = { subtitlePick = i },
									style = if (subtitlePick == i) KButtonStyle.Primary else KButtonStyle.Secondary,
								)
							}
						}
						DiscSection.Extras -> LazyVerticalGrid(
							columns = GridCells.Adaptive(250.dp),
							contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
							horizontalArrangement = Arrangement.spacedBy(20.dp),
							verticalArrangement = Arrangement.spacedBy(20.dp),
						) {
							items(extras) { title ->
								KCard(
									imageUrl = picture("title=${title.number}&seconds=${minOf(title.seconds * 0.1, 30.0).toLong()}"),
									title = "Extra ${title.number}",
									subtitle = clock(title.seconds),
									shape = KCardShape.Wide,
									width = 250.dp,
									showTitle = true,
									onClick = { navigator.go(Route.Player(item, 0L, mediaSourceId = title.mediaSourceId)) },
								)
							}
						}
					}
				}
			}
		}
	}

	androidx.compose.runtime.LaunchedEffect(disc != null) {
		if (disc == null) return@LaunchedEffect
		repeat(20) {
			delay(50)
			if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
		}
	}
}
