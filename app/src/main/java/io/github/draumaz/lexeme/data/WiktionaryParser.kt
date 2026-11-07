package io.github.draumaz.lexeme.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URLDecoder
import java.util.IdentityHashMap

/**
 * Turns the HTML from `action=parse` into an [Entry]. Wiktionary's markup is a few thousand
 * templates deep, so rather than special-casing templates this walks the DOM generically
 * (paragraphs, lists, tables, images) and only recognises the handful of structures that need
 * their own presentation: headword lines, inflection tables, NavFrames, audio, etymology trees
 * and stroke order. Anything unrecognised still comes through as text, so no data is dropped.
 */
object WiktionaryParser {

    /** Chrome, edit links and invisible scaffolding. `.noprint` is sister-project boxes and similar. */
    private const val SKIP = "style, link, script, meta, noscript, .mw-editsection, .noprint, " +
        ".sister-project, .sister-wikipedia, ul.etymonid, .mw-empty-elt, .NavToggle, " +
        ".mw-collapsible-toggle, span.indicator, .mf-icon, .catlinks, .mw-cite-backlink, " +
        "[style~=display:\\s*none]"

    private val BLOCK_TAGS = setOf(
        "p", "div", "ul", "ol", "dl", "dd", "dt", "li", "table", "figure", "img", "audio", "video",
        "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "center", "pre", "hr", "section", "caption",
        "tr", "td", "th", "tbody", "thead", "tfoot", "figcaption",
    )

    fun parse(title: String, html: String): Entry {
        val doc = Jsoup.parseBodyFragment(html)
        val root = doc.selectFirst("div.mw-parser-output") ?: doc.body()
        root.select(SKIP).remove()

        val ctx = Ctx(title)
        val sequence = ArrayList<Node>()
        flatten(root, sequence)

        val preamble = ArrayList<Block>()
        val languages = ArrayList<LanguageBuilder>()
        val pending = ArrayList<Node>()

        fun flush() {
            if (pending.isEmpty()) return
            val blocks = ctx.blocksOf(pending)
            pending.clear()
            val lang = languages.lastOrNull()
            when {
                lang == null -> preamble += blocks
                lang.sections.isEmpty() -> lang.intro += blocks
                else -> lang.sections.last().blocks += blocks
            }
        }

        for (node in sequence) {
            val heading = (node as? Element)?.let(::headingOf)
            if (heading == null) {
                pending += node
                continue
            }
            flush()
            val (level, text, id) = heading
            if (level <= 2) {
                languages += LanguageBuilder(text, id)
            } else {
                languages.lastOrNull()?.sections?.add(SectionBuilder(text, level))
            }
        }
        flush()

        val seeAlso = preamble.filterIsInstance<Block.Paragraph>().firstOrNull()?.text
        return Entry(
            title = title,
            seeAlso = seeAlso,
            languages = languages.map { it.build() },
        )
    }

    /** MobileFrontend wraps each language in a `<section>`; unwrap so headings and content are siblings. */
    private fun flatten(parent: Element, out: MutableList<Node>) {
        for (child in parent.childNodes()) {
            if (child is Element && child.normalName() == "section") flatten(child, out) else out += child
        }
    }

    private fun headingOf(el: Element): Triple<Int, String, String>? {
        val h = when {
            el.hasClass("mw-heading") -> el.selectFirst("h1, h2, h3, h4, h5, h6")
            el.normalName().matches(Regex("h[1-6]")) -> el
            else -> null
        } ?: return null
        return Triple(h.normalName().drop(1).toInt(), h.text().trim(), h.id())
    }

    private class SectionBuilder(val title: String, val level: Int) {
        val blocks = ArrayList<Block>()
    }

    private class LanguageBuilder(val name: String, val id: String) {
        val intro = ArrayList<Block>()
        val sections = ArrayList<SectionBuilder>()

        fun build(): LanguageEntry {
            val strokes = ArrayList<Image>()
            val intro = liftStrokeOrder(intro, strokes)
            val sections = sections.map { Section(it.title, it.level, liftStrokeOrder(it.blocks, strokes)) }
            return LanguageEntry(
                name = name,
                id = id.ifBlank { name.replace(' ', '_') },
                intro = intro,
                strokeOrder = strokes.distinctBy { it.url },
                sections = sections,
            )
        }
    }

    /** Pulls [Block.StrokeOrder] out of a block tree (wherever a template put it) into [sink]. */
    private fun liftStrokeOrder(blocks: List<Block>, sink: MutableList<Image>): List<Block> =
        blocks.mapNotNull { block ->
            when (block) {
                is Block.StrokeOrder -> { sink += block.images; null }
                is Block.Collapsible -> block.copy(blocks = liftStrokeOrder(block.blocks, sink))
                    .takeIf { it.blocks.isNotEmpty() }
                is Block.Indented -> block.copy(blocks = liftStrokeOrder(block.blocks, sink))
                is Block.ListBlock -> block.copy(items = block.items.map { liftStrokeOrder(it, sink) })
                else -> block
            }
        }

    private class Ctx(val pageTitle: String) {
        private val blockMemo = IdentityHashMap<Element, Boolean>()

        /** Whether [el] is, or contains, something that can't live inside a line of text. */
        fun isBlock(el: Element): Boolean {
            blockMemo[el]?.let { return it }
            val result = el.normalName() in BLOCK_TAGS ||
                el.attr("typeof").startsWith("mw:File") ||
                el.children().any { isBlock(it) }
            blockMemo[el] = result
            return result
        }

        fun blocksOf(nodes: List<Node>): List<Block> {
            val out = ArrayList<Block>()
            val text = RichBuilder()
            for (node in nodes) {
                when (node) {
                    is TextNode -> text.text(node.text())
                    is Element -> if (isBlock(node)) {
                        text.flushInto(out)
                        out += blockOf(node)
                    } else {
                        inline(node, text)
                    }
                }
            }
            text.flushInto(out)
            return out
        }

        fun rich(el: Element?): RichText? {
            el ?: return null
            val b = RichBuilder()
            richInto(el, b)
            return b.build()
        }

        /** Like [inline] but tolerant of block children, flattening them into lines. */
        private fun richInto(el: Element, b: RichBuilder) {
            for (child in el.childNodes()) {
                when (child) {
                    is TextNode -> b.text(child.text())
                    is Element -> if (child.normalName() in setOf("p", "div", "li", "dd", "tr")) {
                        if (b.length > 0) b.newline()
                        richInto(child, b)
                    } else if (isBlock(child)) {
                        richInto(child, b)
                    } else {
                        inline(child, b)
                    }
                }
            }
        }

        private fun inline(el: Element, b: RichBuilder) {
            if (el.normalName() == "br") {
                b.newline()
                return
            }
            val start = b.length
            for (child in el.childNodes()) {
                when (child) {
                    is TextNode -> b.text(child.text())
                    is Element -> inline(child, b)
                }
            }
            val end = b.length
            if (end <= start) return
            for (style in stylesOf(el)) b.span(start, end, style)
            if (el.normalName() == "a") linkOf(el)?.let { b.span(start, end, SpanStyleKind.Link, it) }
        }

        private fun stylesOf(el: Element): List<SpanStyleKind> {
            val out = ArrayList<SpanStyleKind>(2)
            val cls = el.classNames()
            when (el.normalName()) {
                "b", "strong" -> out += if ("selflink" in cls || "headword" in cls) SpanStyleKind.Self else SpanStyleKind.Bold
                "i", "em", "cite", "var" -> out += SpanStyleKind.Italic
                "sup" -> out += SpanStyleKind.Superscript
                "sub" -> out += SpanStyleKind.Subscript
                "small" -> out += SpanStyleKind.Small
                "code", "kbd", "tt" -> out += SpanStyleKind.Ipa
            }
            if ("IPA" in cls) out += SpanStyleKind.Ipa
            if ("selflink" in cls || "mw-selflink" in cls) out += SpanStyleKind.Self
            if ("ib-content" in cls || "ib-brac" in cls) out += SpanStyleKind.Label
            if ("e-translation" in cls || "tr" in cls || "mention-tr" in cls || "e-transliteration" in cls) {
                out += SpanStyleKind.Muted
            }
            return out
        }

        fun linkOf(a: Element): Link? {
            val href = a.attr("href")
            return when {
                href.isBlank() || href.startsWith("#cite") -> null
                href.startsWith("#") -> Link.Wiki(pageTitle, decode(href.drop(1)))
                href.startsWith("/wiki/") -> wikiLink(href.removePrefix("/wiki/"), href)
                href.contains("redlink=1") -> null
                href.startsWith("/w/") -> Link.External("https://en.wiktionary.org$href")
                href.startsWith("//") -> Link.External("https:$href")
                href.startsWith("https://en.wiktionary.org/wiki/") ->
                    wikiLink(href.removePrefix("https://en.wiktionary.org/wiki/"), href)
                href.startsWith("http") -> Link.External(href)
                else -> null
            }
        }

        private fun wikiLink(path: String, href: String): Link {
            val title = decode(path.substringBefore('#')).replace('_', ' ')
            val fragment = path.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let(::decode)
            val namespace = title.substringBefore(':', "")
            return if (namespace in setOf("File", "Category", "Special", "Help", "Talk", "User")) {
                Link.External(if (href.startsWith("http")) href else "https://en.wiktionary.org$href")
            } else {
                Link.Wiki(title, fragment)
            }
        }

        private fun blockOf(el: Element): List<Block> {
            val tag = el.normalName()
            val cls = el.classNames()
            return when {
                tag == "p" || tag == "pre" -> paragraph(el)
                tag == "ul" || tag == "ol" -> listOfNotNull(list(el))
                tag == "dl" -> listOfNotNull(indented(el))
                tag == "table" -> table(el)
                tag == "audio" -> listOfNotNull(audio(el, "Audio"))
                tag == "figure" || tag == "img" || el.attr("typeof").startsWith("mw:File") ||
                    "thumb" in cls -> media(el)
                tag == "blockquote" -> listOf(Block.Indented(IndentStyle.Quotation, blocksOf(el.childNodes())))
                tag.matches(Regex("h[1-6]")) -> listOfNotNull(rich(el)?.let {
                    Block.Paragraph(it.copy(spans = it.spans + Span(0, it.text.length, SpanStyleKind.Bold)))
                })
                tag == "hr" -> emptyList()
                "etytree" in cls -> etymologyTree(el)
                "NavFrame" in cls -> navFrame(el)
                "inflection-table-wrapper" in cls -> inflectionWrapper(el)
                else -> blocksOf(el.childNodes())
            }
        }

        private fun paragraph(el: Element): List<Block> {
            val blocks = blocksOf(el.childNodes())
            if (el.selectFirst(".headword-line") == null) return blocks
            var marked = false
            return blocks.map {
                if (!marked && it is Block.Paragraph) {
                    marked = true
                    it.copy(role = Block.Paragraph.Role.Headword)
                } else it
            }
        }

        private fun list(el: Element): Block? {
            val lis = el.children().filter { it.normalName() == "li" }
            val items = lis.map { blocksOf(it.childNodes()) }.filter { it.isNotEmpty() }
            if (items.isEmpty()) return null
            val ordered = el.normalName() == "ol"
            // Only real quotations (with quoted text); bare citations are reference lists.
            val quoteLike = lis.count { it.selectFirst(".h-quotation") != null }
            val style = when {
                ordered -> ListStyle.Senses
                quoteLike * 2 >= lis.size && quoteLike > 0 -> ListStyle.Quotations
                items.size >= 3 && items.all(::isChipLike) -> ListStyle.Chips
                else -> ListStyle.Bullets
            }
            return Block.ListBlock(ordered, items, style)
        }

        /** Derived-term style items: a short line that is mostly one link. */
        private fun isChipLike(item: List<Block>): Boolean {
            val p = item.singleOrNull() as? Block.Paragraph ?: return false
            val text = p.text.text
            if (text.length > 42 || '\n' in text) return false
            val linked = p.text.spans.filter { it.link is Link.Wiki }.sumOf { it.end - it.start }
            return linked * 10 >= text.trim().length * 6
        }

        private fun indented(el: Element): Block? {
            val blocks = el.children().flatMap { child ->
                when (child.normalName()) {
                    "dt" -> rich(child)?.let {
                        listOf(Block.Paragraph(it.copy(spans = it.spans + Span(0, it.text.length, SpanStyleKind.Bold))))
                    } ?: emptyList()
                    else -> blocksOf(child.childNodes())
                }
            }
            if (blocks.isEmpty()) return null
            val style = when {
                el.selectFirst(".h-usage-example") != null -> IndentStyle.Example
                el.selectFirst(".h-quotation") != null -> IndentStyle.Quotation
                else -> IndentStyle.Plain
            }
            return Block.Indented(style, blocks)
        }

        private fun directRows(table: Element): List<Element> = table.children().flatMap { child ->
            when (child.normalName()) {
                "tr" -> listOf(child)
                "thead", "tbody", "tfoot" -> child.children().filter { it.normalName() == "tr" }
                else -> emptyList()
            }
        }

        private fun table(el: Element): List<Block> {
            val cls = el.classNames()
            if ("audiotable" in cls) {
                val label = el.selectFirst("td")?.text()?.trim()?.trimEnd(':', ' ')?.ifBlank { null } ?: "Audio"
                return listOfNotNull(el.selectFirst("audio")?.let { audio(it, label) })
            }
            val rows = directRows(el)
            val cells = rows.flatMap { r -> r.children().filter { it.normalName() == "td" || it.normalName() == "th" } }
            val firstRowText = rows.firstOrNull()?.text()?.trim().orEmpty()
            val imgs = el.select("img")
            if (imgs.isNotEmpty() && (firstRowText.startsWith("Stroke order", ignoreCase = true) ||
                    imgs.any { isStrokeImage(it) })
            ) {
                return listOf(Block.StrokeOrder(imgs.mapNotNull { imageOf(it, null) }))
            }
            // Layout tables (translations, multi-column term lists) hold blocks, not data.
            val layout = "translations" in cls || cells.any { c ->
                c.children().any { it.normalName() in setOf("ul", "ol", "dl", "table") || it.hasClass("NavFrame") }
            }
            if (layout) return cells.flatMap { blocksOf(it.childNodes()) }

            var caption = rich(el.children().firstOrNull { it.normalName() == "caption" })
            var grid = rows.map { r ->
                r.children().filter { it.normalName() == "td" || it.normalName() == "th" }.map { c ->
                    Cell(
                        blocks = blocksOf(c.childNodes()),
                        header = c.normalName() == "th",
                        colspan = c.attr("colspan").toIntOrNull()?.coerceIn(1, 40) ?: 1,
                        rowspan = c.attr("rowspan").toIntOrNull()?.coerceIn(1, 80) ?: 1,
                        secondary = "secondary" in c.classNames(),
                    )
                }
            }.filter { it.isNotEmpty() }
            // A lone full-width header row is the table's title.
            val first = grid.firstOrNull()?.singleOrNull()
            if (caption == null && first != null && first.header && grid.size > 1 &&
                grid.drop(1).any { it.sumOf(Cell::colspan) > 1 }
            ) {
                caption = first.blocks.filterIsInstance<Block.Paragraph>().firstOrNull()?.text
                if (caption != null) grid = grid.drop(1)
            }
            if (grid.isEmpty()) return emptyList()
            val title = caption?.text.orEmpty().lowercase()
            val inflection = cls.any { it.startsWith("inflection") || it.endsWith("-inflection-table") } ||
                el.closest(".inflection-table-wrapper") != null ||
                listOf("declension", "conjugation", "inflection", "mutation").any { it in title }
            val table = Block.Table(caption, grid, emptyList(), inflection)
            return if ("mw-collapsed" in cls && !inflection) {
                listOf(Block.Collapsible(caption ?: RichText.plain("Table"), listOf(table.copy(caption = null)), false))
            } else {
                listOf(table)
            }
        }

        private fun inflectionWrapper(el: Element): List<Block> {
            val notes = el.selectFirst(".inflection-table-notes")?.let { blocksOf(it.childNodes()) }.orEmpty()
            el.selectFirst(".inflection-table-notes")?.remove()
            val blocks = blocksOf(el.childNodes())
            var attached = false
            return blocks.map { b ->
                if (!attached && b is Block.Table) {
                    attached = true
                    b.copy(notes = notes, inflection = true)
                } else b
            } + if (attached) emptyList() else notes
        }

        private fun navFrame(el: Element): List<Block> {
            val head = el.children().firstOrNull { it.hasClass("NavHead") }
            if (el.hasClass("pseudo")) {
                return listOfNotNull(rich(head)?.let { Block.Paragraph(it) })
            }
            val content = el.children().firstOrNull { it.hasClass("NavContent") }
            val blocks = if (content != null) blocksOf(content.childNodes())
            else blocksOf(el.childNodes().filter { it !== head })
            if (blocks.isEmpty()) return emptyList()
            val title = rich(head) ?: RichText.plain("More")
            val t = title.text.lowercase()
            val expand = listOf("conjugation", "declension", "inflection", "mutation").any { it in t } ||
                blocks.any { it is Block.Table && it.inflection }
            return listOf(Block.Collapsible(title, blocks, expand))
        }

        private fun etymologyTree(el: Element): List<Block> {
            val nodes = el.select(".etytree-block").filterNot { it.hasClass("etytree-termless-block") }.mapNotNull { b ->
                val term = rich(b.selectFirst(".etytree-term")) ?: return@mapNotNull null
                EtymologyNode(
                    language = b.selectFirst(".etyl")?.text()?.trim().orEmpty(),
                    term = term,
                    relation = b.selectFirst(".etytree-label abbr")?.attr("title")?.ifBlank { null },
                )
            }
            return if (nodes.size >= 2) listOf(Block.EtymologyTree(nodes)) else emptyList()
        }

        private fun media(el: Element): List<Block> {
            el.selectFirst("audio")?.let { return listOfNotNull(audio(it, "Audio")) }
            val caption = rich(el.selectFirst("figcaption, .thumbcaption"))?.takeUnless { it.isBlank }
            val imgs = if (el.normalName() == "img") listOf(el) else el.select("img")
            val images = imgs.mapNotNull { imageOf(it, caption.takeIf { _ -> imgs.size == 1 }) }
            if (images.isEmpty()) return emptyList()
            return if (imgs.any { isStrokeImage(it) }) listOf(Block.StrokeOrder(images))
            else listOf(Block.Images(images))
        }

        private fun audio(el: Element, label: String): Block.Audio? {
            val sources = el.select("source").map { it.attr("src") } + listOf(el.attr("src"))
            val src = sources.filter { it.isNotBlank() }
                .sortedByDescending { it.endsWith(".mp3") }
                .firstOrNull() ?: return null
            return Block.Audio(absolute(src), label)
        }
    }

    private fun isStrokeImage(img: Element): Boolean {
        val name = fileNameOf(img.attr("src")).lowercase()
        return Regex("-[a-z]?order\\.gif$").containsMatchIn(name) || ("stroke" in name && "order" in name)
    }

    /** The file name, from either an original URL or a `.../thumb/x/xy/Name.ext/250px-Name.ext` one. */
    private fun fileNameOf(src: String): String {
        val path = decode(src.substringBefore('?'))
        val parts = path.split('/')
        val thumbAt = parts.indexOf("thumb")
        return if (thumbAt >= 0 && parts.size > thumbAt + 3) parts[thumbAt + 3] else parts.last()
    }

    internal fun imageOf(img: Element, caption: RichText?): Image? {
        val src = img.attr("src").ifBlank { return null }
        val w = img.attr("width").toIntOrNull() ?: 0
        val h = img.attr("height").toIntOrNull() ?: 0
        // Icons (Commons logo, Wikipedia W) are decoration.
        if (w in 1..27 && h in 1..27) return null
        val twoX = img.attr("srcset").split(',').map { it.trim() }
            .firstOrNull { it.endsWith(" 2x") }?.substringBefore(' ')
        var url = absolute(twoX ?: src).substringBefore('?')
        // Animated GIFs only keep their animation at their original size.
        if (fileNameOf(src).endsWith(".gif", ignoreCase = true)) url = originalOf(absolute(src))
        val invert = img.hasClass("mw-invert") || img.parents().any {
            it.hasClass("mw-invert") || it.hasClass("skin-invert") || it.hasClass("skin-invert-image")
        }
        return Image(url, w, h, caption, invert)
    }

    private val ThumbUrl = Regex("^https://(?:thumb|upload)\\.wikimedia\\.org/(.+?)/thumb/(.+)/[^/]+$")

    internal fun originalOf(url: String): String {
        val clean = url.substringBefore('?')
        val m = ThumbUrl.find(clean) ?: return clean
        return "https://upload.wikimedia.org/${m.groupValues[1]}/${m.groupValues[2]}"
    }

    private fun absolute(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("/") -> "https://en.wiktionary.org$src"
        else -> src
    }

    private fun decode(s: String): String =
        runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
}

/** Accumulates text with HTML whitespace collapsing, plus styled ranges over it. */
private class RichBuilder {
    private val sb = StringBuilder()
    private val spans = ArrayList<Span>()

    val length: Int get() = sb.length

    fun text(s: String) {
        if (s.isEmpty()) return
        var t = s
        if (t[0] == ' ' && (sb.isEmpty() || sb.last() == ' ' || sb.last() == '\n')) t = t.trimStart(' ')
        sb.append(t)
    }

    fun newline() {
        while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
        if (sb.isNotEmpty()) sb.append('\n')
    }

    fun span(start: Int, end: Int, style: SpanStyleKind, link: Link? = null) {
        spans += Span(start, end, style, link)
    }

    fun build(): RichText? {
        var end = sb.length
        while (end > 0 && sb[end - 1].isWhitespace()) end--
        if (end == 0) return null
        val text = sb.substring(0, end)
        val clamped = spans.mapNotNull {
            val e = minOf(it.end, end)
            if (it.start < e) it.copy(end = e) else null
        }
        return RichText(text, clamped)
    }

    fun flushInto(out: MutableList<Block>) {
        build()?.let { out += Block.Paragraph(it) }
        sb.setLength(0)
        spans.clear()
    }
}
