package org.khylo.tv.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.khylo.sdk.model.api.BaseItemDto
import org.khylo.tv.app.navigator
import org.khylo.tv.data.KhyloRepository
import org.khylo.tv.design.KIcon
import org.khylo.tv.design.KIcons
import org.khylo.tv.design.KSpinner
import org.khylo.tv.design.KTab
import org.khylo.tv.design.KText
import org.khylo.tv.design.KhyloType
import org.khylo.tv.design.rememberFocusState
import org.koin.compose.koinInject
import java.io.File

/** Reading themes: the page colour and the ink. */
private enum class PageTheme(val label: String, val page: Color, val ink: Color, val muted: Color) {
	Night("Night", Color(0xFF121110), Color(0xFFE9E3D8), Color(0xFF8E877C)),
	Sepia("Sepia", Color(0xFFF1E5CC), Color(0xFF3A2E20), Color(0xFF8A7656)),
	Paper("Paper", Color(0xFFFAF8F3), Color(0xFF1D1D1B), Color(0xFF85837D)),
}

private val textSizes = listOf(20, 23, 26, 30, 34)

/**
 * Reading a book on the TV: one calm column of large serif text. Right / Down turn the page,
 * Left / Up go back, OK opens chapters, text size and theme. Your place is remembered.
 */
@Composable
fun ReaderScreen(item: BaseItemDto) {
	val context = LocalContext.current
	val repository = koinInject<KhyloRepository>()
	val http = koinInject<OkHttpClient>()
	val navigator = navigator()
	val prefs = remember { context.getSharedPreferences("khylo_reader", android.content.Context.MODE_PRIVATE) }
	val key = item.id.toString()

	val extension = (item.container ?: item.path?.substringAfterLast('.', ""))?.lowercase().orEmpty()
	val book by produceState<Result<EpubBook>?>(null, item.id) {
		value = if (extension.isNotEmpty() && extension != "epub") {
			Result.failure(UnsupportedOperationException(extension))
		} else {
			withContext(Dispatchers.IO) {
				runCatching {
					val file = File(context.cacheDir, "books/${item.id}.epub").also { it.parentFile?.mkdirs() }
					repository.downloadBook(item, file, http)
					EpubBook(file)
				}
			}
		}
	}

	var theme by remember { mutableStateOf(PageTheme.entries.getOrElse(prefs.getInt("theme", 0)) { PageTheme.Night }) }
	var sizeIndex by remember { mutableIntStateOf(prefs.getInt("size", 1).coerceIn(textSizes.indices)) }
	var chapter by remember { mutableIntStateOf(prefs.getInt("$key.chapter", 0)) }
	var showMenu by remember { mutableStateOf(false) }

	BackHandler { if (showMenu) showMenu = false else navigator.back() }

	Box(Modifier.fillMaxSize().background(theme.page)) {
		when (val loaded = book) {
			null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
				KSpinner(size = 48.dp)
				Spacer(Modifier.height(16.dp))
				KText("Opening ${item.name.orEmpty()}…", style = KhyloType.Label, color = theme.muted)
			}
			else -> loaded.fold(
				onSuccess = { epub ->
					Page(
						epub = epub,
						chapter = chapter.coerceIn(0, (epub.chapters.size - 1).coerceAtLeast(0)),
						startParagraph = if (chapter == prefs.getInt("$key.chapter", 0)) prefs.getInt("$key.paragraph", 0) else 0,
						theme = theme,
						textSize = textSizes[sizeIndex],
						menuOpen = showMenu,
						onChapter = { next ->
							chapter = next
							prefs.edit().putInt("$key.chapter", next).putInt("$key.paragraph", 0).apply()
						},
						onPosition = { paragraph -> prefs.edit().putInt("$key.chapter", chapter).putInt("$key.paragraph", paragraph).apply() },
						onMenu = { showMenu = true },
					)
					AnimatedVisibility(
						visible = showMenu,
						enter = fadeIn() + slideInHorizontally { it / 3 },
						exit = fadeOut() + slideOutHorizontally { it / 3 },
						modifier = Modifier.align(Alignment.CenterEnd),
					) {
						ReaderMenu(
							epub = epub,
							chapter = chapter,
							theme = theme,
							sizeIndex = sizeIndex,
							onChapter = {
								chapter = it
								prefs.edit().putInt("$key.chapter", it).putInt("$key.paragraph", 0).apply()
								showMenu = false
							},
							onTheme = {
								theme = it
								prefs.edit().putInt("theme", it.ordinal).apply()
							},
							onSize = {
								sizeIndex = it
								prefs.edit().putInt("size", it).apply()
							},
						)
					}
				},
				onFailure = { error ->
					Column(Modifier.align(Alignment.Center).width(640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
						KIcon(KIcons.Book, theme.muted, Modifier.width(64.dp).height(64.dp))
						Spacer(Modifier.height(16.dp))
						KText(item.name.orEmpty(), style = KhyloType.Title, color = theme.ink)
						Spacer(Modifier.height(10.dp))
						KText(
							if (error is UnsupportedOperationException) {
								"This book is a ${extension.uppercase()} file. Khylo on the TV reads EPUB books; this one can be read in the Khylo web app."
							} else {
								"This book couldn't be opened. Check that the server is reachable and try again."
							},
							style = KhyloType.Body,
							color = theme.muted,
						)
					}
				},
			)
		}
	}
}

@Composable
private fun Page(
	epub: EpubBook,
	chapter: Int,
	startParagraph: Int,
	theme: PageTheme,
	textSize: Int,
	menuOpen: Boolean,
	onChapter: (Int) -> Unit,
	onPosition: (Int) -> Unit,
	onMenu: () -> Unit,
) {
	val paragraphs by produceState<List<String>?>(null, chapter) {
		value = withContext(Dispatchers.IO) { runCatching { epub.paragraphs(epub.chapters[chapter]) }.getOrDefault(emptyList()) }
	}
	val listState = rememberLazyListState()
	val scope = rememberCoroutineScope()
	val focus = remember { FocusRequester() }
	val density = LocalDensity.current

	LaunchedEffect(paragraphs) {
		if (paragraphs != null) {
			listState.scrollToItem(startParagraph.coerceIn(0, (paragraphs!!.size - 1).coerceAtLeast(0)))
			delay(50)
			runCatching { focus.requestFocus() }
		}
	}
	LaunchedEffect(menuOpen) { if (!menuOpen) runCatching { focus.requestFocus() } }
	LaunchedEffect(listState.firstVisibleItemIndex) { onPosition(listState.firstVisibleItemIndex) }

	val atEnd by remember { derivedStateOf { !listState.canScrollForward } }
	val atStart by remember { derivedStateOf { !listState.canScrollBackward } }
	val chapterProgress by remember {
		derivedStateOf {
			val total = listState.layoutInfo.totalItemsCount
			if (total == 0) 0f else (listState.firstVisibleItemIndex + 1).toFloat() / total
		}
	}

	BoxWithConstraints(
		Modifier
			.fillMaxSize()
			.focusRequester(focus)
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown || menuOpen) return@onPreviewKeyEvent false
				// One screenful of text, less a line or two so the eye keeps its place
				val page = (listState.layoutInfo.viewportSize.height - with(density) { 64.dp.toPx() }).coerceAtLeast(200f)
				when (event.key) {
					Key.DirectionRight, Key.DirectionDown, Key.PageDown, Key.MediaFastForward -> {
						if (atEnd) {
							if (chapter < epub.chapters.size - 1) onChapter(chapter + 1)
						} else {
							scope.launch { listState.animateScrollBy(page) }
						}
						true
					}
					Key.DirectionLeft, Key.DirectionUp, Key.PageUp, Key.MediaRewind -> {
						if (atStart) {
							if (chapter > 0) onChapter(chapter - 1)
						} else {
							scope.launch { listState.animateScrollBy(-page) }
						}
						true
					}
					Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu -> {
						onMenu()
						true
					}
					else -> false
				}
			}
			.focusable(),
	) {
		val column = Modifier.width(minOf(maxWidth - 120.dp, 860.dp))
		// Book and chapter, quietly at the top
		Row(column.align(Alignment.TopCenter).padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
			KText(epub.title, style = KhyloType.Caption.copy(fontSize = 13.sp), color = theme.muted, maxLines = 1, modifier = Modifier.weight(1f))
			KText(epub.chapters.getOrNull(chapter)?.title.orEmpty(), style = KhyloType.Caption.copy(fontSize = 13.sp), color = theme.muted, maxLines = 1)
		}

		when (val text = paragraphs) {
			null -> KSpinner(Modifier.align(Alignment.Center))
			else -> LazyColumn(
				state = listState,
				userScrollEnabled = false,
				contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
				verticalArrangement = Arrangement.spacedBy((textSize * 0.9f).dp),
				// The text stays between the title line and the progress bar
				modifier = column.align(Alignment.TopCenter).fillMaxHeight().padding(top = 64.dp, bottom = 76.dp).clipToBounds(),
			) {
				itemsIndexed(text) { _, paragraph ->
					KText(
						paragraph,
						style = TextStyle(
							fontFamily = FontFamily.Serif,
							fontSize = textSize.sp,
							lineHeight = (textSize * 1.6f).sp,
							color = theme.ink,
						),
					)
				}
			}
		}

		// Where you are: this chapter's bar and the place in the book
		Column(column.align(Alignment.BottomCenter).padding(bottom = 28.dp)) {
			Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(theme.muted.copy(alpha = 0.25f))) {
				Box(Modifier.fillMaxWidth(chapterProgress.coerceIn(0f, 1f)).height(3.dp).background(theme.muted))
			}
			Spacer(Modifier.height(8.dp))
			Row {
				KText("Chapter ${chapter + 1} of ${epub.chapters.size}", style = KhyloType.Caption, color = theme.muted, modifier = Modifier.weight(1f))
				KText("◀ ▶ turn pages  ·  OK for chapters & settings", style = KhyloType.Caption, color = theme.muted)
			}
		}
	}
}

@Composable
private fun ReaderMenu(
	epub: EpubBook,
	chapter: Int,
	theme: PageTheme,
	sizeIndex: Int,
	onChapter: (Int) -> Unit,
	onTheme: (PageTheme) -> Unit,
	onSize: (Int) -> Unit,
) {
	val current = remember { FocusRequester() }
	LaunchedEffect(Unit) {
		delay(80)
		runCatching { current.requestFocus() }
	}
	Column(
		Modifier
			.fillMaxHeight()
			.width(440.dp)
			.background(Color(0xF2141414))
			.padding(start = 28.dp, end = 28.dp, top = 36.dp),
	) {
		KText("Reading", style = KhyloType.Title)
		Spacer(Modifier.height(14.dp))
		KText("THEME", style = KhyloType.Eyebrow)
		Row { PageTheme.entries.forEach { t -> KTab(t.label, t == theme, { onTheme(t) }) } }
		Spacer(Modifier.height(8.dp))
		KText("TEXT SIZE", style = KhyloType.Eyebrow)
		Row { listOf("S", "M", "L", "XL", "XXL").forEachIndexed { i, label -> KTab(label, i == sizeIndex, { onSize(i) }) } }
		Spacer(Modifier.height(10.dp))
		KText("CHAPTERS", style = KhyloType.Eyebrow)
		Spacer(Modifier.height(6.dp))
		LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
			itemsIndexed(epub.chapters) { index, ch ->
				ChapterRow(ch.title, index == chapter, if (index == chapter) Modifier.focusRequester(current) else Modifier) { onChapter(index) }
			}
		}
	}
}

@Composable
private fun ChapterRow(title: String, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
	val (source, focused) = rememberFocusState()
	KText(
		title,
		style = KhyloType.Label.copy(fontWeight = if (current) FontWeight.Bold else FontWeight.Medium),
		color = if (focused) Color(0xFF0B0B0C) else if (current) Color(0xFFE3A857) else Color.White,
		maxLines = 1,
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(if (focused) Color.White else Color.Transparent)
			.clickable(interactionSource = source, indication = null, onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 10.dp),
	)
}
