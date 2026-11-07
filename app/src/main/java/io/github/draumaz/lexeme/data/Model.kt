package io.github.draumaz.lexeme.data

/**
 * A Wiktionary page reduced to a small, renderer-agnostic document model. Everything here is
 * plain immutable data, so the parser is testable on the JVM and the UI can `remember` on it.
 */
data class Entry(
    val title: String,
    /** Page-level "See also" line (other capitalisations, diacritics). */
    val seeAlso: RichText?,
    val languages: List<LanguageEntry>,
)

data class LanguageEntry(
    val name: String,
    /** Anchor id from the page, e.g. `Alemannic_German`; what `#fragment` links point at. */
    val id: String,
    /** Content before the first subsection: floating images, glyph boxes, notes. */
    val intro: List<Block>,
    /** Stroke-order animations and diagrams, lifted out of wherever they sat in the page. */
    val strokeOrder: List<Image>,
    val sections: List<Section>,
)

data class Section(
    val title: String,
    /** HTML heading level: 3 for top-level subsections, 4 and 5 for nested ones. */
    val level: Int,
    val blocks: List<Block>,
) {
    val kind: SectionKind get() = SectionKind.of(title)
}

enum class SectionKind {
    ETYMOLOGY, PRONUNCIATION, PART_OF_SPEECH, INFLECTION, TRANSLATIONS, RELATIONS,
    USAGE, CHARACTER, REFERENCES, OTHER;

    companion object {
        private val partsOfSpeech = setOf(
            "noun", "proper noun", "verb", "adjective", "adverb", "pronoun", "preposition",
            "postposition", "conjunction", "interjection", "determiner", "article", "numeral",
            "particle", "prefix", "suffix", "infix", "interfix", "circumfix", "affix", "phrase",
            "proverb", "idiom", "abbreviation", "initialism", "acronym", "contraction", "symbol",
            "letter", "number", "classifier", "counter", "participle", "romanization",
            "prepositional phrase", "adjectival noun", "syllable", "root", "ideophone",
            "diacritical mark", "punctuation mark", "cardinal number", "ordinal number",
            "definitions", "kanji", "hanja", "han character", "logogram", "combining form",
        )

        fun of(title: String): SectionKind {
            val t = title.lowercase().trim().replace(Regex("\\s+\\d+$"), "")
            return when {
                t.startsWith("etymology") || t == "glyph origin" -> ETYMOLOGY
                t.startsWith("pronunciation") -> PRONUNCIATION
                t in partsOfSpeech -> if (t == "han character" || t == "kanji" || t == "hanja") CHARACTER else PART_OF_SPEECH
                t.startsWith("declension") || t.startsWith("conjugation") || t.startsWith("inflection") ||
                    t.startsWith("mutation") || t == "forms" || t.startsWith("conjugated forms") -> INFLECTION
                t.startsWith("translation") -> TRANSLATIONS
                t in setOf(
                    "synonyms", "antonyms", "hypernyms", "hyponyms", "meronyms", "holonyms", "troponyms",
                    "derived terms", "related terms", "coordinate terms", "descendants", "compounds",
                    "see also", "alternative forms", "derived characters", "collocations", "anagrams",
                    "idioms", "proverbs", "paronyms", "abbreviations", "related characters", "phrasal verbs",
                ) -> RELATIONS
                t.startsWith("usage notes") || t == "trivia" || t == "notes" -> USAGE
                t == "references" || t == "further reading" || t == "sources" || t == "external links" -> REFERENCES
                else -> OTHER
            }
        }
    }
}

sealed interface Block {
    data class Paragraph(val text: RichText, val role: Role = Role.Body) : Block {
        enum class Role { Body, Headword }
    }

    /** `<ul>`/`<ol>`; [style] decides whether it is drawn as bullets, numbered senses or chips. */
    data class ListBlock(val ordered: Boolean, val items: List<List<Block>>, val style: ListStyle) : Block

    /** `<dl>`/`<dd>` content: usage examples, quotation texts, or plain indentation. */
    data class Indented(val style: IndentStyle, val blocks: List<Block>) : Block

    data class Table(
        val caption: RichText?,
        val rows: List<List<Cell>>,
        val notes: List<Block>,
        /** Inflection tables (declension, conjugation) get the prominent treatment. */
        val inflection: Boolean,
    ) : Block

    data class Images(val images: List<Image>) : Block

    data class Audio(val url: String, val label: String) : Block

    /** A NavFrame or collapsible box: a title bar that hides [blocks] until expanded. */
    data class Collapsible(val title: RichText, val blocks: List<Block>, val startExpanded: Boolean) : Block

    /** The "Etymology tree" box: a chain of ancestor terms, oldest first. */
    data class EtymologyTree(val nodes: List<EtymologyNode>) : Block

    /** Stroke-order images; lifted into [LanguageEntry.strokeOrder] after parsing. */
    data class StrokeOrder(val images: List<Image>) : Block
}

enum class ListStyle { Bullets, Senses, Chips, Quotations }

enum class IndentStyle { Plain, Example, Quotation }

data class Cell(
    val blocks: List<Block>,
    val header: Boolean,
    val colspan: Int = 1,
    val rowspan: Int = 1,
    /** Muted cells like articles in German declension tables. */
    val secondary: Boolean = false,
)

data class Image(
    val url: String,
    val width: Int,
    val height: Int,
    val caption: RichText?,
    /** Black-on-transparent artwork (stroke diagrams, ancient scripts) that needs inverting in dark mode. */
    val invertInDark: Boolean,
) {
    val animated: Boolean get() = url.substringBefore('?').endsWith(".gif", ignoreCase = true)

    /** A readable name for screen readers: `水-order.gif` → "水 order". */
    val label: String
        get() {
            val file = runCatching { java.net.URLDecoder.decode(url.substringAfterLast('/'), "UTF-8") }
                .getOrDefault(url.substringAfterLast('/'))
            return file.substringBeforeLast('.').replace(Regex("^\\d+px-"), "").replace(Regex("[_-]+"), " ").trim()
        }
}

data class EtymologyNode(val language: String, val term: RichText, val relation: String?)

/** Text plus styled ranges, mapped onto an AnnotatedString by the UI. */
data class RichText(val text: String, val spans: List<Span>) {
    val isBlank: Boolean get() = text.isBlank()

    companion object {
        fun plain(text: String) = RichText(text, emptyList())
    }
}

data class Span(val start: Int, val end: Int, val style: SpanStyleKind, val link: Link? = null)

enum class SpanStyleKind {
    Bold, Italic, Link, Superscript, Subscript, Small, Ipa,
    /** The page's own word wherever it appears: headwords, bolded quotation hits. */
    Self,
    /** Qualifiers and labels like "(derogatory)". */
    Label,
    /** Translations of examples and transliterations, drawn muted. */
    Muted,
}

sealed interface Link {
    /** A page on English Wiktionary; [fragment] usually names a language section. */
    data class Wiki(val title: String, val fragment: String?) : Link
    data class External(val url: String) : Link
}
