package io.github.draumaz.lexeme.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WiktionaryParserTest {

    private fun page(body: String) = """<div class="mw-content-ltr mw-parser-output">$body</div>"""

    private fun h(level: Int, text: String, id: String = text.replace(' ', '_')) =
        """<div class="mw-heading mw-heading$level"><h$level id="$id">$text</h$level></div>"""

    @Test
    fun splitsLanguagesAndSections() {
        val entry = WiktionaryParser.parse(
            "Hund",
            page(
                h(2, "German") + """<section>""" + h(3, "Noun") +
                    """<p><span class="headword-line"><strong class="Latn headword">Hund</strong> m</span></p>""" +
                    """<ol><li><a href="/wiki/dog" title="dog">dog</a></li><li>scoundrel</li></ol></section>""" +
                    h(2, "Danish") + h(3, "Etymology") + "<p>From Old Norse.</p>"
            ),
        )
        assertEquals(listOf("German", "Danish"), entry.languages.map { it.name })
        val noun = entry.languages[0].sections.single()
        assertEquals(SectionKind.PART_OF_SPEECH, noun.kind)
        val headword = noun.blocks[0] as Block.Paragraph
        assertEquals(Block.Paragraph.Role.Headword, headword.role)
        assertEquals("Hund m", headword.text.text)
        assertTrue(headword.text.spans.any { it.style == SpanStyleKind.Self })
        val senses = noun.blocks[1] as Block.ListBlock
        assertEquals(ListStyle.Senses, senses.style)
        val link = (senses.items[0][0] as Block.Paragraph).text.spans.single { it.link != null }.link
        assertEquals(Link.Wiki("dog", null), link)
        assertEquals(SectionKind.ETYMOLOGY, entry.languages[1].sections.single().kind)
    }

    @Test
    fun parsesInflectionTableWithSpans() {
        val entry = WiktionaryParser.parse(
            "Hund",
            page(
                h(2, "German") + h(4, "Declension") +
                    """<div class="inflection-table-wrapper"><table class="inflection-table">
                    <caption class="inflection-table-title">Declension of <i>Hund</i></caption>
                    <tbody><tr><th></th><th colspan="2">singular</th></tr>
                    <tr><th>nominative</th><td class="secondary">der</td><td>Hund</td></tr>
                    <tr><th rowspan="2">genitive</th><td>des</td><td>Hundes<br/>Hunds</td></tr>
                    <tr><td>eines</td><td>Hundes</td></tr></tbody></table>
                    <div class="inflection-table-notes"><p>Note one.</p></div></div>"""
            ),
        )
        val table = entry.languages[0].sections[0].blocks.single() as Block.Table
        assertTrue(table.inflection)
        assertEquals("Declension of Hund", table.caption?.text)
        assertEquals("Note one.", (table.notes.single() as Block.Paragraph).text.text)
        assertTrue(table.rows[1][1].secondary)
        val grid = TableGrid.of(table.rows)
        assertEquals(3, grid.columns)
        assertEquals(4, grid.rows)
        // The rowspan pushes the last row's first cell one column right.
        val last = grid.cells.filter { it.row == 3 }
        assertEquals(listOf(1, 2), last.map { it.col })
        val forms = (grid.cells.single { it.row == 2 && it.col == 2 }.cell.blocks.single() as Block.Paragraph).text.text
        assertEquals("Hundes\nHunds", forms)
    }

    @Test
    fun liftsStrokeOrderAndKeepsGifOriginal() {
        val entry = WiktionaryParser.parse(
            "水",
            page(
                h(2, "Translingual") +
                    """<table class="floatright wikitable"><tbody><tr><th colspan="4">Stroke order</th></tr>
                    <tr><td><span typeof="mw:File"><a href="/wiki/File:x"><img src="//thumb.wikimedia.org/wikipedia/commons/thumb/4/42/%E6%B0%B4-order.gif/120px-%E6%B0%B4-order.gif?utm_source=x" width="100" height="100"/></a></span></td></tr></tbody></table>""" +
                    h(3, "Han character") + "<p>Kangxi radical 85</p>"
            ),
        )
        val lang = entry.languages.single()
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/4/42/%E6%B0%B4-order.gif",
            lang.strokeOrder.single().url,
        )
        assertTrue(lang.strokeOrder.single().animated)
        assertEquals("水 order", lang.strokeOrder.single().label)
        assertTrue(lang.intro.isEmpty())
        assertEquals(SectionKind.CHARACTER, lang.sections.single().kind)
    }

    @Test
    fun audioAndExamples() {
        val entry = WiktionaryParser.parse(
            "Hund",
            page(
                h(2, "German") + h(3, "Pronunciation") +
                    """<ul><li><table class="audiotable"><tbody><tr><td>Audio (Berlin):</td><td><audio>
                    <source src="//upload.wikimedia.org/a/De-Hund.ogg.mp3" type="audio/mpeg"/></audio></td></tr></tbody></table></li></ul>""" +
                    h(3, "Noun") + """<ol><li>dog<dl><dd><span class="h-usage-example"><i>ein <b>Hund</b></i> ― <span class="e-translation">a dog</span></span></dd></dl></li></ol>"""
            ),
        )
        val pron = entry.languages[0].sections[0].blocks.single() as Block.ListBlock
        val audio = pron.items.single().single() as Block.Audio
        assertEquals("Audio (Berlin)", audio.label)
        assertEquals("https://upload.wikimedia.org/a/De-Hund.ogg.mp3", audio.url)
        val sense = (entry.languages[0].sections[1].blocks.single() as Block.ListBlock).items.single()
        val example = sense[1] as Block.Indented
        assertEquals(IndentStyle.Example, example.style)
        val text = (example.blocks.single() as Block.Paragraph).text
        assertEquals("ein Hund ― a dog", text.text)
        assertTrue(text.spans.any { it.style == SpanStyleKind.Muted && text.text.substring(it.start, it.end) == "a dog" })
    }

    @Test
    fun selfFragmentLinksPointAtCurrentPage() {
        val entry = WiktionaryParser.parse(
            "Hund",
            page(h(2, "Bavarian") + """<p>Cognate with <a class="mw-selflink-fragment" href="#German">Hund</a>.</p>"""),
        )
        val p = entry.languages[0].intro.single() as Block.Paragraph
        assertEquals(Link.Wiki("Hund", "German"), p.text.spans.single { it.link != null }.link)
    }

    /**
     * Parses real pages saved from the API, if [SAMPLES] points at them (`<word>.html` files
     * holding the `parse.text` of `action=parse`). Prints a structural outline for eyeballing.
     */
    @Test
    fun realPagesIfPresent() {
        val dir = System.getenv(SAMPLES)?.let(::File)?.takeIf { it.isDirectory } ?: return
        val files = dir.listFiles { f -> f.name.endsWith(".html") }.orEmpty()
        for (file in files) {
            val html = file.readText()
            val title = file.nameWithoutExtension
            val started = System.nanoTime()
            val entry = WiktionaryParser.parse(title, html)
            val ms = (System.nanoTime() - started) / 1_000_000
            println("=== $title: ${entry.languages.size} languages, parsed in ${ms}ms; see also: ${entry.seeAlso?.text}")
            assertTrue(entry.languages.isNotEmpty())
            for (lang in entry.languages) {
                println("  [${lang.name}] intro=${lang.intro.map(::describe)} strokes=${lang.strokeOrder.map { it.url.substringAfterLast('/') }}")
                for (s in lang.sections) {
                    println("    ${"  ".repeat(s.level - 3)}${s.title} (${s.kind}): ${s.blocks.map(::describe)}")
                }
            }
            assertNotNull(entry)
        }
    }

    private fun describe(b: Block): String = when (b) {
        is Block.Paragraph -> "P${if (b.role == Block.Paragraph.Role.Headword) "(head)" else ""}'${b.text.text.take(40).replace('\n', '|')}'"
        is Block.ListBlock -> "${b.style}[${b.items.size}]"
        is Block.Indented -> "${b.style}{${b.blocks.size}}"
        is Block.Table -> "Table(${b.caption?.text?.take(30)}, ${b.rows.size}x${TableGrid.of(b.rows).columns}${if (b.inflection) ", infl" else ""})"
        is Block.Images -> "Img${b.images.map { it.url.substringAfterLast('/') }}"
        is Block.Audio -> "Audio(${b.label})"
        is Block.Collapsible -> "Fold('${b.title.text.take(30)}', ${b.blocks.map(::describe)})"
        is Block.EtymologyTree -> "Tree${b.nodes.map { it.language + ":" + it.term.text }}"
        is Block.StrokeOrder -> "Strokes"
    }

    companion object {
        const val SAMPLES = "LEXEME_SAMPLES"
    }
}
