package org.khylo.tv.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.crossfade
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** Focus state for a TV element; everything is reached with the D-pad. */
@Composable
fun rememberFocusState(): Pair<MutableInteractionSource, Boolean> {
	val source = remember { MutableInteractionSource() }
	val focused by source.collectIsFocusedAsState()
	return source to focused
}

@Composable
fun KIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
	Image(rememberVectorPainter(icon), contentDescription = contentDescription, colorFilter = ColorFilter.tint(tint), modifier = modifier)
}

enum class KButtonStyle { Primary, Secondary, Ghost }

/**
 * A pill button. Unfocused it's frosted glass (Primary is solid white); focused it turns white
 * with black text, the way premium TV apps do it.
 */
@Composable
fun KButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	icon: ImageVector? = null,
	style: KButtonStyle = KButtonStyle.Secondary,
	enabled: Boolean = true,
) {
	val (source, focused) = rememberFocusState()
	val background by animateColorAsState(
		when {
			focused -> KhyloColors.Focus
			style == KButtonStyle.Primary -> Color(0xE6FFFFFF)
			style == KButtonStyle.Ghost -> Color.Transparent
			else -> KhyloColors.Glass
		},
		label = "buttonBackground",
	)
	val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "buttonScale")
	val content = when {
		!enabled -> KhyloColors.TextMuted
		focused || style == KButtonStyle.Primary -> KhyloColors.OnFocus
		else -> KhyloColors.Text
	}

	Row(
		modifier = modifier
			.scale(scale)
			.clip(RoundedCornerShape(10.dp))
			.background(background)
			.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
			// Icons carry a little built-in space, so their side gets less padding to look even
			.padding(start = if (icon != null) 16.dp else 22.dp, end = 22.dp)
			.height(44.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
	) {
		if (icon != null) KIcon(icon, content, Modifier.size(22.dp))
		KText(text, style = KhyloType.Label.copy(fontWeight = FontWeight.SemiBold).merge(KhyloType.Centered), color = content, maxLines = 1)
	}
}

/** A round icon-only button, for player controls. */
@Composable
fun KIconButton(
	icon: ImageVector,
	contentDescription: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	size: Dp = 52.dp,
) {
	val (source, focused) = rememberFocusState()
	val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "iconScale")
	Box(
		modifier = modifier
			.scale(scale)
			.size(size)
			.clip(CircleShape)
			.background(if (focused) KhyloColors.Focus else KhyloColors.Glass)
			.clickable(interactionSource = source, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		KIcon(icon, if (focused) KhyloColors.OnFocus else KhyloColors.Text, Modifier.size(size * 0.5f), contentDescription)
	}
}

enum class KCardShape(val ratio: Float) { Poster(2f / 3f), Wide(16f / 9f), Square(1f) }

/**
 * Artwork tile. Clean art with no clutter; focus lifts it with a white outline. Titles show only
 * where the art alone wouldn't say what it is (episodes, channels).
 */
@Composable
fun KCard(
	imageUrl: String?,
	title: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	shape: KCardShape = KCardShape.Poster,
	width: Dp = if (shape == KCardShape.Poster) 150.dp else 280.dp,
	progress: Float? = null,
	badge: String? = null,
	showTitle: Boolean = false,
	onFocus: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	/** The show's or film's logo, drawn on the artwork in place of the title text. */
	logoUrl: String? = null,
	/** Wide artwork for the page backdrop while this card is focused; the card's own art otherwise. */
	backdropUrl: String? = null,
	/** Identifies the card so the screen can focus it again when you come back to it. */
	focusKey: String? = null,
	/** Already watched: a small check mark in the corner. */
	watched: Boolean = false,
) {
	val (source, focused) = rememberFocusState()
	var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
	var longPressed by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")
	val ambient = LocalAmbient.current
	if (focused) LaunchedEffect(Unit) {
		if (backdropUrl != null) ambient?.show(backdropUrl, isBackdrop = true) else ambient?.show(imageUrl, isBackdrop = shape == KCardShape.Wide)
		onFocus?.invoke()
	}

	Column(modifier = modifier.width(width)) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(shape.ratio)
				.scale(scale)
				// Depth: every card sits on a soft shadow; the focused one lifts higher
				.shadow(if (focused) 28.dp else 8.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color.Black)
				.clip(RoundedCornerShape(8.dp))
				.background(KhyloColors.SurfaceRaised)
				// A faint edge keeps dark artwork from melting into the background
				.border(if (focused) 3.dp else 1.dp, if (focused) KhyloColors.Focus else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
				// Holding OK opens the item's options
				.onPreviewKeyEvent { event ->
					if (onLongClick == null) return@onPreviewKeyEvent false
					val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
					when {
						!isSelect -> false
						event.type == KeyEventType.KeyDown && event.nativeKeyEvent.isLongPress -> {
							longPressed = true
							onLongClick()
							true
						}
						// A fresh press clears a long press whose release went to the options menu
						event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> {
							longPressed = false
							false
						}
						event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> true
						event.type == KeyEventType.KeyUp && longPressed -> {
							longPressed = false
							true
						}
						else -> false
					}
				}
				.rememberedFocus(focusKey)
				.clickable(interactionSource = source, indication = null, onClick = onClick),
		) {
			if (imageUrl != null) {
				KImage(imageUrl, Modifier.fillMaxSize())
				if (logoUrl != null) {
					// A soft shade in the lower corner so any logo reads over the picture
					Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xB3000000))))
					KImage(
						logoUrl,
						Modifier
							.align(Alignment.BottomStart)
							.padding(start = 12.dp, end = 12.dp, bottom = if (progress != null && progress > 0f) 22.dp else 12.dp)
							.fillMaxWidth(0.55f)
							.height(40.dp),
						ContentScale.Fit,
						Alignment.BottomStart,
						onLoaded = { logoLoaded = true },
					)
				}
			} else {
				KText(title, style = KhyloType.Label, modifier = Modifier.align(Alignment.Center).padding(12.dp), maxLines = 3)
			}
			// On focus, a soft light catches the top of the card, like glass
			if (focused) {
				Box(Modifier.matchParentSize().background(Brush.linearGradient(0f to Color(0x30FFFFFF), 0.4f to Color(0x08FFFFFF), 0.6f to Color.Transparent)))
			}
			if (badge != null) {
				KText(
					badge,
					style = KhyloType.Caption.copy(fontWeight = FontWeight.Bold),
					color = Color.White,
					modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(KhyloColors.Live).padding(horizontal = 6.dp, vertical = 2.dp),
				)
			}
			if (watched) {
				Box(
					Modifier
						.align(Alignment.TopEnd)
						.padding(8.dp)
						.size(24.dp)
						.shadow(4.dp, CircleShape)
						.clip(CircleShape)
						.background(Color(0xE60B0C0E))
						.border(1.dp, Color(0x40FFFFFF), CircleShape),
					contentAlignment = Alignment.Center,
				) {
					KIcon(KIcons.Check, Color.White, Modifier.size(15.dp), "Watched")
				}
			}
			if (progress != null && progress > 0f) {
				Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x59FFFFFF))) {
					Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(KhyloColors.Accent))
				}
			}
		}
		if (showTitle) {
			Spacer(Modifier.height(12.dp))
			// With the logo on the picture, the line below only needs the episode
			if (!logoLoaded) KText(title, style = KhyloType.Label, color = if (focused) KhyloColors.Text else KhyloColors.TextSecondary, maxLines = 1)
			if (subtitle != null) KText(subtitle, style = if (logoLoaded) KhyloType.Label else KhyloType.Caption, color = if (logoLoaded && focused) KhyloColors.Text else if (logoLoaded) KhyloColors.TextSecondary else Color.Unspecified, maxLines = 1)
		}
	}
}

/** Server artwork, loaded with the app's shared image loader. */
@Composable
fun KImage(
	url: String?,
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Crop,
	alignment: Alignment = Alignment.Center,
	onLoaded: (() -> Unit)? = null,
) {
	val context = coil3.compose.LocalPlatformContext.current
	AsyncImage(
		model = remember(url) { coil3.request.ImageRequest.Builder(context).data(url).crossfade(260).build() },
		contentDescription = null,
		imageLoader = koinInject<ImageLoader>(),
		modifier = modifier,
		contentScale = contentScale,
		alignment = alignment,
		onSuccess = onLoaded?.let { { _ -> it() } },
	)
}

/** Left-to-right fade so text over artwork stays readable. */
val HeroScrim = Brush.horizontalGradient(
	0f to KhyloColors.Background,
	0.35f to KhyloColors.Background.copy(alpha = 0.88f),
	0.65f to KhyloColors.Background.copy(alpha = 0.25f),
	1f to Color.Transparent,
)

val BottomScrim = Brush.verticalGradient(
	0f to Color.Transparent,
	0.55f to KhyloColors.Background.copy(alpha = 0.55f),
	1f to KhyloColors.Background,
)

@Composable
fun KSectionTitle(text: String, modifier: Modifier = Modifier) {
	KText(text, style = KhyloType.Heading, modifier = modifier.padding(bottom = 12.dp))
}

/** Loading ring: a thin white arc. */
@Composable
fun KSpinner(modifier: Modifier = Modifier, size: Dp = 40.dp) {
	val transition = rememberInfiniteTransition(label = "spinner")
	val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinnerAngle")
	Canvas(modifier.size(size)) {
		val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
		drawArc(Color(0x1FFFFFFF), 0f, 360f, false, style = stroke)
		drawArc(Color.White, angle, 90f, false, style = stroke)
	}
}

/** A text box for the on-screen keyboard. Up and Down leave it, like any other control. */
@Composable
fun KTextField(
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	placeholder: String = "",
	isPassword: Boolean = false,
	keyboardType: KeyboardType = KeyboardType.Text,
	onDone: (() -> Unit)? = null,
) {
	val (source, focused) = rememberFocusState()
	val focusManager = LocalFocusManager.current
	Column(modifier) {
		KText(label, style = KhyloType.Caption, modifier = Modifier.padding(bottom = 8.dp))
		BasicTextField(
			value = value,
			onValueChange = onValueChange,
			modifier = Modifier.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				when (event.key) {
					Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
					Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
					else -> false
				}
			},
			interactionSource = source,
			singleLine = true,
			textStyle = KhyloType.Heading.copy(fontWeight = FontWeight.Medium, color = if (focused) KhyloColors.OnFocus else KhyloColors.Text),
			cursorBrush = SolidColor(KhyloColors.Accent),
			visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
			keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType, imeAction = ImeAction.Done),
			keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
			decorationBox = { inner ->
				Box(
					Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(10.dp))
						.background(if (focused) KhyloColors.Focus else KhyloColors.Glass)
						.padding(horizontal = 18.dp, vertical = 14.dp),
				) {
					if (value.isEmpty()) KText(placeholder, style = KhyloType.Heading.copy(fontWeight = FontWeight.Normal), color = if (focused) Color(0xFF6B6B6B) else KhyloColors.TextMuted)
					inner()
				}
			},
		)
	}
}

/**
 * Puts the TV's focus inside this area when it appears (a TV always needs something highlighted).
 * Content that loads from the server shows up a moment later, so it keeps trying briefly.
 */
@Composable
fun Modifier.autoFocusContent(key: Any?, requester: FocusRequester = remember { FocusRequester() }): Modifier {
	var hasFocus by remember { mutableStateOf(false) }
	LaunchedEffect(key) {
		repeat(40) {
			if (hasFocus) return@LaunchedEffect
			delay(150)
			runCatching { requester.requestFocus() }
		}
	}
	return this
		.focusRequester(requester)
		.onFocusChanged { hasFocus = it.hasFocus }
		// Coming back into the screen (e.g. Down from the top bar) returns to where you were
		.focusRestorer()
		.focusGroup()
}

/**
 * A quiet text tab for choosing between options (categories, seasons, sort order). Fully
 * transparent; the selected one is bright with a thin accent underline, and focus turns it into
 * a white pill like every other focused control.
 */
@Composable
fun KTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
	val (source, focused) = rememberFocusState()
	Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
		// Same space above as the underline takes below, so the tab is balanced in a row
		Spacer(Modifier.height(5.dp))
		// The pill wraps only the label, so the words sit dead centre in it when focused
		Box(
			Modifier
				.clip(RoundedCornerShape(50))
				.background(if (focused) KhyloColors.Focus else Color.Transparent)
				.clickable(interactionSource = source, indication = null, onClick = onClick)
				.padding(horizontal = 14.dp)
				.height(34.dp),
			contentAlignment = Alignment.Center,
		) {
			KText(
				label,
				style = KhyloType.Label.copy(fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal).merge(KhyloType.Centered),
				color = when {
					focused -> KhyloColors.OnFocus
					selected -> KhyloColors.Text
					else -> Color(0x99FFFFFF)
				},
				maxLines = 1,
			)
		}
		Spacer(Modifier.height(3.dp))
		Box(Modifier.width(18.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (selected && !focused) KhyloColors.Accent else Color.Transparent))
	}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private object StayPut : androidx.compose.foundation.gestures.BringIntoViewSpec {
	override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val LocalRowBringIntoViewSpec = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.foundation.gestures.BringIntoViewSpec?> { null }

/**
 * For a list of rows that snaps the focused row into place itself. The list no longer also slides
 * up or down to chase the focused card, which fought the snap and made rows jump when moving left
 * and right; rows inside still slide sideways (see [SnapRow]).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SnapRows(content: @Composable () -> Unit) {
	val rowSpec = androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current
	CompositionLocalProvider(
		androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides StayPut,
		LocalRowBringIntoViewSpec provides rowSpec,
		content = content,
	)
}

/** One row inside [SnapRows]: it scrolls left and right to keep the focused card in view as usual. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SnapRow(content: @Composable () -> Unit) {
	val rowSpec = LocalRowBringIntoViewSpec.current ?: androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current
	CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides rowSpec, content = content)
}

/**
 * Which card a screen had focused, so coming back to it (Back from a title) lands on the same
 * card. One per screen in the back stack; the shell provides it.
 */
class FocusMemory(restore: String? = null) {
	/** The focus key of the card focused last on this screen. */
	var last: String? = restore

	/** The card to put focus back on when the screen returns; cleared once it has. */
	var pending: String? = restore
}

val LocalFocusMemory = androidx.compose.runtime.staticCompositionLocalOf<FocusMemory?> { null }

/** Saves a screen's [FocusMemory] with the rest of its state while another screen is on top. */
val FocusMemorySaver = androidx.compose.runtime.saveable.Saver<FocusMemory, String>(
	save = { it.last.orEmpty() },
	restore = { FocusMemory(it.ifEmpty { null }) },
)

/**
 * Remembers this element as the screen's focused one under [key], and gives it focus again when
 * the screen comes back. Goes before the element's clickable/focusable modifier.
 */
@Composable
fun Modifier.rememberedFocus(key: String?): Modifier {
	val memory = LocalFocusMemory.current
	if (key == null || memory == null) return this
	val requester = remember { FocusRequester() }
	if (memory.pending == key) {
		LaunchedEffect(Unit) {
			// After the screen's own first focus, so this one wins
			delay(250)
			if (runCatching { requester.requestFocus() }.isSuccess) memory.pending = null
		}
	}
	return this
		.focusRequester(requester)
		.onFocusChanged { if (it.isFocused) memory.last = key }
}

/**
 * Steady scrolling for grids: moving to another row puts it at the same height every time (about a
 * third of the way down), rather than nudging the page by however much each card needs.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private object PivotSpec : androidx.compose.foundation.gestures.BringIntoViewSpec {
	override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
		val target = containerSize * 0.3f
		// Rows taller than the space below the pivot are shown whole instead
		val leading = if (size <= containerSize - target) target else (containerSize - size).coerceAtLeast(0f)
		return offset - leading
	}
}

/** Wraps a grid so it scrolls with [PivotSpec]. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PivotScroll(content: @Composable () -> Unit) {
	CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides PivotSpec, content = content)
}
