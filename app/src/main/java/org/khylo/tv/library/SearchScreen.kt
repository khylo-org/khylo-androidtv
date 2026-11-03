package org.khylo.tv.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.tv.app.Route
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.data.SearchResults
import org.khylo.tv.design.KCard
import org.khylo.tv.design.KCardShape
import org.khylo.tv.design.KImage
import org.khylo.tv.design.KSectionTitle
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KText
import org.khylo.tv.design.KTextField
import org.khylo.tv.design.KhyloColors
import org.khylo.tv.design.KhyloSpacing
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.khylo.tv.home.openItem
import org.khylo.tv.home.subtitleFor
import org.koin.compose.koinInject


/**
 * Search the server. Results come in rows by kind (movies and shows first, then episodes, live
 * TV channels and people), each ranked so the closest matches lead.
 */
@Composable
fun SearchScreen() {
	val repository = koinInject<KhyloRepository>()
	val navigator = navigator()
	var query by remember { mutableStateOf(org.khylo.tv.app.SearchMemory.global) }
	LaunchedEffect(query) { org.khylo.tv.app.SearchMemory.global = query }
	val term = query.trim()
	val results by produceState<SearchResults?>(SearchResults(), term) {
		if (term.length < 2) {
			value = SearchResults()
			return@produceState
		}
		value = null
		delay(350)
		value = runCatching { repository.search(term) }.getOrDefault(SearchResults())
	}

	Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
		KTextField(
			value = query,
			onValueChange = { query = it },
			label = "Search",
			placeholder = "Movies, shows, episodes, channels, people",
			modifier = Modifier.padding(horizontal = KhyloSpacing.Gutter).width(700.dp),
		)
		Spacer(Modifier.height(20.dp))

		val found = results
		when {
			term.length < 2 -> KText("Type at least two letters.", style = KhyloType.Body, modifier = Modifier.padding(horizontal = KhyloSpacing.Gutter))
			found == null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) { KSpinner() }
			found.isEmpty -> KText("Nothing found for “$term”.", style = KhyloType.Body, modifier = Modifier.padding(horizontal = KhyloSpacing.Gutter))
			else -> LazyColumn(contentPadding = PaddingValues(bottom = 280.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
				if (found.titles.isNotEmpty()) item(key = "titles") {
					ResultRow("Movies & shows") {
						items(found.titles, key = { it.id }) { item ->
							KCard(repository.imageUrl(item), item.name.orEmpty(), onClick = { openItem(navigator, item) }, width = 148.dp, watched = item.userData?.played == true)
						}
					}
				}
				if (found.episodes.isNotEmpty()) item(key = "episodes") {
					ResultRow("Episodes") {
						items(found.episodes, key = { it.id }) { item ->
							KCard(
								watched = item.userData?.played == true,
								imageUrl = repository.thumbUrl(item),
								title = item.seriesName ?: item.name.orEmpty(),
								subtitle = subtitleFor(item),
								shape = KCardShape.Wide,
								width = 260.dp,
								showTitle = true,
								logoUrl = repository.showLogoUrl(item),
								onClick = { openItem(navigator, item) },
							)
						}
					}
				}
				if (found.channels.isNotEmpty()) item(key = "channels") {
					ResultRow("Live TV") {
						items(found.channels, key = { it.id }) { channel ->
							KCard(
								imageUrl = repository.imageUrl(channel, maxWidth = 320),
								title = channel.name.orEmpty(),
								subtitle = channel.currentProgram?.name,
								shape = KCardShape.Wide,
								width = 200.dp,
								showTitle = true,
								onClick = { navigator.go(Route.Player(channel)) },
							)
						}
					}
				}
				if (found.music.isNotEmpty()) item(key = "music") {
					ResultRow("Music") {
						items(found.music, key = { it.id }) { item ->
							KCard(
								imageUrl = repository.imageUrl(item),
								title = item.name.orEmpty(),
								subtitle = item.albumArtist ?: if (item.type == org.khylo.sdk.model.api.BaseItemKind.MUSIC_ARTIST) "Artist" else null,
								shape = KCardShape.Square,
								width = 160.dp,
								showTitle = true,
								onClick = { openItem(navigator, item) },
							)
						}
					}
				}
				if (found.books.isNotEmpty()) item(key = "books") {
					ResultRow("Books & audiobooks") {
						items(found.books, key = { it.id }) { item ->
							KCard(
								imageUrl = repository.imageUrl(item),
								title = item.album ?: item.name.orEmpty(),
								subtitle = if (item.type == org.khylo.sdk.model.api.BaseItemKind.AUDIO_BOOK) "Audiobook" else "Book",
								width = 140.dp,
								showTitle = true,
								onClick = { openItem(navigator, item) },
							)
						}
					}
				}
				if (found.people.isNotEmpty()) item(key = "people") {
					ResultRow("People") {
						items(found.people, key = { it.id }) { person -> PersonResult(person, repository) { navigator.go(Route.Person(person.id, person.name.orEmpty())) } }
					}
				}
			}
		}
	}
}

@Composable
private fun ResultRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
	Column {
		KSectionTitle(title, Modifier.padding(start = KhyloSpacing.Gutter))
		LazyRow(
			contentPadding = PaddingValues(horizontal = KhyloSpacing.Gutter, vertical = 10.dp),
			horizontalArrangement = Arrangement.spacedBy(18.dp),
			content = content,
		)
	}
}

@Composable
private fun PersonResult(person: BaseItemDto, repository: KhyloRepository, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "personScale")
	Column(Modifier.width(118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		Box(
			Modifier
				.size(108.dp)
				.scale(scale)
				.clip(CircleShape)
				.background(KhyloColors.SurfaceRaised)
				.border(if (focused) 3.dp else 0.dp, if (focused) KhyloColors.Focus else Color.Transparent, CircleShape)
				.clickable(interactionSource = source, indication = null, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			val image = repository.imageUrl(person, maxWidth = 300)
			if (image != null) {
				KImage(image, Modifier.fillMaxSize())
			} else {
				KText(person.name.orEmpty().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase(), style = KhyloType.Title, color = KhyloColors.TextSecondary)
			}
		}
		Spacer(Modifier.height(10.dp))
		KText(person.name.orEmpty(), style = KhyloType.Label, color = if (focused) KhyloColors.Text else KhyloColors.TextSecondary, maxLines = 1, textAlign = TextAlign.Center)
	}
}

