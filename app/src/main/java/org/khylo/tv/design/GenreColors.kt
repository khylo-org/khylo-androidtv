package org.khylo.tv.design

import androidx.compose.ui.graphics.Color

/**
 * A hand-picked pair of colors for each genre (deep, rich), so category tiles read as one designed
 * set rather than a mix of artwork. Genres not listed get a color from the same palette, always the
 * same one for the same name.
 */
fun genreColors(name: String): Pair<Color, Color> {
	val key = name.lowercase()
	val match = known.entries.firstOrNull { (words, _) -> words.any { key.contains(it) } }?.value
	return match ?: palette[Math.floorMod(key.hashCode(), palette.size)]
}

private fun pair(deep: Long, rich: Long) = Color(deep) to Color(rich)

private val known: Map<List<String>, Pair<Color, Color>> = linkedMapOf(
	listOf("sci", "science") to pair(0xFF07142E, 0xFF1F5FBF),
	listOf("action") to pair(0xFF2A0608, 0xFFB0242B),
	listOf("adventure") to pair(0xFF082019, 0xFF1F8A63),
	listOf("animation", "anime") to pair(0xFF2C0B26, 0xFFD2457A),
	listOf("comedy") to pair(0xFF2B1604, 0xFFE08A1E),
	listOf("crime") to pair(0xFF14110A, 0xFF7A6230),
	listOf("documentary") to pair(0xFF0E1418, 0xFF4E6B7A),
	listOf("drama") to pair(0xFF1C0A24, 0xFF7B3A9E),
	listOf("family", "kids", "children") to pair(0xFF0A2216, 0xFF3FA34D),
	listOf("fantasy") to pair(0xFF0B1B2C, 0xFF2F8C9C),
	listOf("history") to pair(0xFF1E1206, 0xFF9C6A2E),
	listOf("horror") to pair(0xFF120203, 0xFF6E0A12),
	listOf("music") to pair(0xFF240A2A, 0xFFB43BB8),
	listOf("mystery") to pair(0xFF0C0C24, 0xFF3D3FA8),
	listOf("romance") to pair(0xFF2A0816, 0xFFC4466E),
	listOf("thriller", "suspense") to pair(0xFF0C1216, 0xFF3C5866),
	listOf("war") to pair(0xFF141808, 0xFF5E6B2A),
	listOf("western") to pair(0xFF22100A, 0xFFA4502A),
	listOf("reality") to pair(0xFF261608, 0xFFC9733A),
	listOf("news", "talk") to pair(0xFF0A1424, 0xFF325A92),
	listOf("sport") to pair(0xFF061E20, 0xFF168C88),
	listOf("tv movie", "short") to pair(0xFF181818, 0xFF5A5A62),
)

private val palette = listOf(
	pair(0xFF07142E, 0xFF1F5FBF),
	pair(0xFF1C0A24, 0xFF7B3A9E),
	pair(0xFF082019, 0xFF1F8A63),
	pair(0xFF2B1604, 0xFFE08A1E),
	pair(0xFF0B1B2C, 0xFF2F8C9C),
	pair(0xFF2A0816, 0xFFC4466E),
	pair(0xFF0C0C24, 0xFF3D3FA8),
	pair(0xFF22100A, 0xFFA4502A),
)
