package io.github.draumaz.lexeme.ui.blocks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import io.github.draumaz.lexeme.data.Block
import io.github.draumaz.lexeme.data.EtymologyBranch
import io.github.draumaz.lexeme.data.RichText

/**
 * Find on page, as one reading-list row sees it: which of its texts hold matches for [query],
 * and which match (if any) is the one being looked at.
 */
@Immutable
class FindHighlight(
    val query: String,
    /** Each text's matches as a run of row-local ordinals. Keyed by identity: equal texts can sit apart. */
    val ranges: Map<RichText, IntRange>,
    /** The row-local ordinal of the current match, or -1 when it's in another row. */
    val active: Int,
    /** Bumps each time the reader steps to a match, so the text holding it scrolls it into view. */
    val token: Int,
    /**
     * Scrolls the list so the span between [top] and [bottom] (root pixels) is comfortably on
     * screen; false if it already was.
     */
    val reveal: suspend (top: Float, bottom: Float) -> Boolean,
) {
    /** Whether the current match lies somewhere in [blocks]: folds holding it open themselves. */
    fun holdsActive(blocks: List<Block>): Boolean =
        active >= 0 && blocks.richTexts().any { ranges[it]?.contains(active) == true }
}

/** The find highlight for the row being composed; null outside the reading list, or with find closed. */
val LocalFindHighlight = compositionLocalOf<FindHighlight?> { null }

/**
 * For folds and "show all" previews: calls [open] when the reader steps to a match among the
 * [hidden] blocks, so it's never tucked away out of sight.
 */
@Composable
fun OpenForFind(hidden: () -> List<Block>, open: () -> Unit) {
    val find = LocalFindHighlight.current ?: return
    if (find.active < 0) return
    LaunchedEffect(find.token, find.active) { if (find.holdsActive(hidden())) open() }
}

/** Where [query] occurs in [text], ignoring case; matches don't overlap. */
fun matchStarts(text: String, query: String): List<Int> {
    if (query.isEmpty() || query.length > text.length) return emptyList()
    val starts = ArrayList<Int>()
    var i = 0
    while (i <= text.length - query.length) {
        if (text.regionMatches(i, query, 0, query.length, ignoreCase = true)) {
            starts += i
            i += query.length
        } else {
            i++
        }
    }
    return starts
}

/** Every text in [this] a reader can see (once folds and previews open), in reading order. */
fun List<Block>.richTexts(): Sequence<RichText> = asSequence().flatMap { it.richTexts() }

fun Block.richTexts(): Sequence<RichText> = when (this) {
    is Block.Paragraph -> sequenceOf(text)
    is Block.ListBlock -> items.asSequence().flatMap { it.richTexts() }
    is Block.Indented -> blocks.richTexts()
    is Block.Table -> sequence {
        caption?.let { yield(it) }
        for (row in rows) for (cell in row) yieldAll(cell.blocks.richTexts())
        yieldAll(notes.richTexts())
    }
    is Block.Images -> images.asSequence().mapNotNull { it.caption }
    is Block.Audio -> emptySequence()
    is Block.Collapsible -> sequenceOf(title) + blocks.richTexts()
    is Block.EtymologyTree -> root.richTexts()
    is Block.StrokeOrder -> emptySequence()
}

private fun EtymologyBranch.richTexts(): Sequence<RichText> =
    sources.asSequence().flatMap { it.richTexts() } + chain.asSequence().map { it.term }
