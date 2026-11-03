package org.khylo.tv.reader

import android.util.Xml
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/** One chapter of a book: its name and where its text lives in the file. */
data class EpubChapter(val title: String, val path: String)

/**
 * A minimal EPUB reader: finds the book's reading order and table of contents, and turns each
 * chapter's XHTML into plain paragraphs (headings kept, pictures and styling dropped), which is
 * what a TV-distance reading view needs.
 */
class EpubBook(private val file: File) {
	val title: String
	val chapters: List<EpubChapter>

	init {
		ZipFile(file).use { zip ->
			val opfPath = zip.readText("META-INF/container.xml")
				?.let { Regex("""full-path="([^"]+)"""").find(it)?.groupValues?.get(1) }
				?: error("Not an EPUB")
			val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
			val opf = zip.readText(opfPath) ?: error("Missing package file")

			val manifest = mutableMapOf<String, Pair<String, String>>() // id -> (href, media-type)
			val spine = mutableListOf<String>()
			var bookTitle: String? = null
			var tocId: String? = null
			var navHref: String? = null

			val parser = Xml.newPullParser()
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
			parser.setInput(opf.reader())
			while (parser.next() != XmlPullParser.END_DOCUMENT) {
				if (parser.eventType != XmlPullParser.START_TAG) continue
				when (parser.name.substringAfter(':')) {
					"title" -> if (bookTitle == null) bookTitle = parser.nextText().trim()
					"item" -> {
						val id = parser.getAttributeValue(null, "id") ?: continue
						val href = parser.getAttributeValue(null, "href") ?: continue
						manifest[id] = href to parser.getAttributeValue(null, "media-type").orEmpty()
						if (parser.getAttributeValue(null, "properties")?.contains("nav") == true) navHref = href
					}
					"spine" -> tocId = parser.getAttributeValue(null, "toc")
					"itemref" -> parser.getAttributeValue(null, "idref")?.let(spine::add)
				}
			}

			// Chapter names from the table of contents (EPUB 2 NCX or EPUB 3 nav), when there is one
			val names = mutableMapOf<String, String>()
			tocId?.let(manifest::get)?.first?.let { zip.readText(base + it) }?.let { ncx ->
				Regex("""<navLabel>\s*<text>(.*?)</text>\s*</navLabel>\s*<content[^>]*src="([^"#]+)""", RegexOption.DOT_MATCHES_ALL)
					.findAll(ncx).forEach { m -> names.putIfAbsent(m.groupValues[2], clean(m.groupValues[1])) }
			}
			navHref?.let { zip.readText(base + it) }?.let { nav ->
				Regex("""<a[^>]*href="([^"#]+)[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
					.findAll(nav).forEach { m -> names.putIfAbsent(m.groupValues[1], clean(m.groupValues[2])) }
			}

			title = bookTitle ?: file.nameWithoutExtension
			// Cover and picture-only pages have no words to read on a TV; leave them out
			chapters = spine.mapNotNull { manifest[it] }
				.filter { (_, type) -> type.contains("html") }
				.map { (href, _) -> href }
				.filter { href -> textOf(zip, base + href).isNotEmpty() }
				.mapIndexed { index, href -> EpubChapter(names[href] ?: "Chapter ${index + 1}", base + href) }
		}
	}

	/** A chapter's text as paragraphs. */
	fun paragraphs(chapter: EpubChapter): List<String> = ZipFile(file).use { zip -> textOf(zip, chapter.path) }

	private fun textOf(zip: ZipFile, path: String): List<String> {
		val html = zip.readText(path).orEmpty()
			.replace(Regex("""<(head|style|script)[^>]*>.*?</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
		val text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
			.replace('\uFFFC', ' ') // images
			.split('\n')
			.map { it.trim() }
			.filter { it.isNotEmpty() }
		return text
	}

	private fun ZipFile.readText(path: String): String? {
		val decoded = java.net.URLDecoder.decode(path, "UTF-8")
		val entry = getEntry(path) ?: getEntry(decoded) ?: return null
		return getInputStream(entry).bufferedReader().use { it.readText() }
	}

	private fun clean(text: String) = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}
