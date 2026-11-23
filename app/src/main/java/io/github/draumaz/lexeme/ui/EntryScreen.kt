package io.github.draumaz.lexeme.ui

import io.github.draumaz.lexeme.data.Namespace
import io.github.draumaz.lexeme.ui.components.NamespaceLabel
import io.github.draumaz.lexeme.data.titleParts
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.draumaz.lexeme.ui.components.GlassSupported
import io.github.draumaz.lexeme.ui.components.glass
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.graphics.shapes.RoundedPolygon
import io.github.draumaz.lexeme.data.Entry
import io.github.draumaz.lexeme.data.LanguageEntry
import io.github.draumaz.lexeme.data.Link
import io.github.draumaz.lexeme.data.MemberKind
import io.github.draumaz.lexeme.data.Section
import io.github.draumaz.lexeme.data.SectionKind
import io.github.draumaz.lexeme.ui.blocks.BlockList
import io.github.draumaz.lexeme.ui.blocks.FindHighlight
import io.github.draumaz.lexeme.ui.blocks.LocalFindHighlight
import io.github.draumaz.lexeme.ui.blocks.matchStarts
import io.github.draumaz.lexeme.ui.blocks.richTexts
import io.github.draumaz.lexeme.data.RichText
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.mutableIntStateOf
import io.github.draumaz.lexeme.ui.components.SelectableText
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.flow.first
import io.github.draumaz.lexeme.ui.blocks.LocalLinkHandler
import io.github.draumaz.lexeme.ui.blocks.RichTextView
import io.github.draumaz.lexeme.ui.blocks.StrokeOrderCard
import io.github.draumaz.lexeme.ui.components.PoppingTitle
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import io.github.draumaz.lexeme.ui.components.rememberSpin
import io.github.draumaz.lexeme.ui.components.badgeShape
import io.github.draumaz.lexeme.ui.components.bouncyEnter
import io.github.draumaz.lexeme.ui.components.hueFor
import io.github.draumaz.lexeme.ui.components.pressScale
import io.github.draumaz.lexeme.ui.components.rememberStagger
import io.github.draumaz.lexeme.ui.components.segmentShape
import kotlinx.coroutines.launch

@Composable
fun PageScreen(
    page: Page,
    listState: LazyListState,
    seen: MutableSet<Any>,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: (MemberKind) -> Unit,
    /** Reloads a category's pages from a letter (null: from the start). */
    onJumpToLetter: (String?) -> Unit,
    /** Find on page, while it's open on this page. */
    find: FindState? = null,
) {
    val split = LocalPaneSplit.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    AnimatedContent(
        targetState = page.state,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.94f))
                .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 1.04f))
        },
        label = "pageState",
    ) { state ->
        @Composable
        fun Status(padding: PaddingValues) = when (state) {
            PageState.Loading -> LoadingView(page.title, padding)
            is PageState.Missing -> MissingView(page.title, state.suggestions, padding, onOpen)
            is PageState.Failed -> FailedView(state.message, padding, onRetry)
            is PageState.Loaded, is PageState.Category -> Unit
        }
        when {
            state is PageState.Loaded -> EntryView(state.entry, page.fragment, listState, seen, contentPadding, find)
            state is PageState.Category -> CategoryView(state, listState, seen, contentPadding, onLoadMore, onJumpToLetter)
            split != null -> Panes(
                split,
                start = { PendingOverview(page.title, contentPadding) },
                end = { Status(PaddingValues(top = split.endTopInset(statusTop) + 16.dp, bottom = contentPadding.calculateBottomPadding())) },
            )
            else -> Status(contentPadding)
        }
    }
}

/** The start pane while a page loads or fails: just the word, where its title will land. */
@Composable
private fun PendingOverview(title: String, contentPadding: PaddingValues) {
    val parts = remember(title) { titleParts(title) }
    Column(Modifier.padding(top = contentPadding.calculateTopPadding()).padding(horizontal = 24.dp).padding(top = 8.dp)) {
        NamespaceLabel(parts, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            parts.name,
            style = MaterialTheme.typography.displayMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CenteredStatus(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(contentPadding).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

@Composable
private fun LoadingView(title: String, contentPadding: PaddingValues) {
    CenteredStatus(contentPadding) {
        ContainedLoadingIndicator(Modifier.size(96.dp).testTag("loading"))
        Text(
            "Looking up “${titleParts(title).name}”",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WobblingBadge(shape: RoundedPolygon, color: Color, icon: ImageVector, tint: Color) {
    val wobble = remember { Animatable(-40f) }
    LaunchedEffect(Unit) { wobble.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow)) }
    ShapeBadge(shape, color, size = 112.dp, rotation = { wobble.value }) {
        Icon(icon, null, Modifier.size(48.dp), tint = tint)
    }
}

@Composable
private fun MissingView(title: String, suggestions: List<String>, contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    CenteredStatus(contentPadding) {
        WobblingBadge(MaterialShapes.Puffy, colors.tertiaryContainer, Icons.Rounded.SearchOff, colors.onTertiaryContainer)
        Text(
            "No entry for “$title”",
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("missing"),
        )
        if (suggestions.isNotEmpty()) {
            Text("Did you mean", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            val seen = remember { HashSet<Any>() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEachIndexed { i, s ->
                    val source = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onOpen(s)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = colors.secondaryContainer,
                        interactionSource = source,
                        modifier = Modifier.pressScale(source, 0.9f).bouncyEnter(s, seen, i),
                    ) {
                        Text(s, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedView(message: String, contentPadding: PaddingValues, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    CenteredStatus(contentPadding) {
        WobblingBadge(MaterialShapes.Cookie6Sided, colors.errorContainer, Icons.Rounded.CloudOff, colors.onErrorContainer)
        Text("Couldn't load this page", style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onRetry, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("retry")) {
            Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
    }
}

/** Flattened LazyColumn rows for an entry, so each section is its own lazily composed item. */
private sealed interface EntryRow {
    val key: String

    data object Title : EntryRow { override val key = "title" }
    data object Chips : EntryRow { override val key = "chips" }
    data class LanguageHeader(val lang: LanguageEntry, val index: Int) : EntryRow { override val key = "lang-$index" }
    data class Strokes(val lang: LanguageEntry, val index: Int) : EntryRow { override val key = "strokes-$index" }
    data class Intro(val lang: LanguageEntry, val index: Int, val first: Boolean, val last: Boolean) : EntryRow {
        override val key = "intro-$index"
    }
    data class SectionRow(
        val section: Section, val langIndex: Int, val index: Int, val first: Boolean, val last: Boolean,
    ) : EntryRow { override val key = "sec-$langIndex-$index" }
    data class Categories(val groups: List<CategoryGroup>) : EntryRow { override val key = "categories" }
}

/**
 * The reading list's rows. With [overviewApart] (two panes) the title and language picker live in
 * the start pane's overview instead, so the list is just the languages.
 */
private fun rowsFor(entry: Entry, overviewApart: Boolean): List<EntryRow> = buildList {
    if (!overviewApart) {
        add(EntryRow.Title)
        if (entry.languages.size > 1) add(EntryRow.Chips)
    }
    entry.languages.forEachIndexed { li, lang ->
        add(EntryRow.LanguageHeader(lang, li))
        if (lang.strokeOrder.isNotEmpty()) add(EntryRow.Strokes(lang, li))
        val hasIntro = lang.intro.isNotEmpty()
        val count = lang.sections.size + if (hasIntro) 1 else 0
        if (hasIntro) add(EntryRow.Intro(lang, li, first = true, last = count == 1))
        lang.sections.forEachIndexed { si, s ->
            val pos = si + if (hasIntro) 1 else 0
            add(EntryRow.SectionRow(s, li, si, first = pos == 0, last = pos == count - 1))
        }
    }
    if (entry.categories.isNotEmpty()) {
        add(EntryRow.Categories(groupCategories(entry.categories, entry.languages.map { it.name })))
    }
}

/** Find on page over an entry's rows: where every match is, numbered from the top of the page. */
private class FindIndex(
    /** Rows holding matches, top to bottom. */
    val rows: IntArray,
    /** The page-wide number of each of those rows' first match. */
    val firsts: IntArray,
    /** Per row, each text's matches as row-local numbers. */
    val ranges: Map<Int, Map<RichText, IntRange>>,
    val total: Int,
) {
    /** Which of [rows] holds match number [match]. */
    fun rowOf(match: Int): Int {
        val at = firsts.binarySearch(match)
        return if (at >= 0) at else -at - 2
    }

    /** The first match in row [row] or below it, or the very first if there are none below. */
    fun firstFrom(row: Int): Int {
        val at = rows.indexOfFirst { it >= row }
        return if (at >= 0) firsts[at] else 0
    }

    companion object {
        val Empty = FindIndex(IntArray(0), IntArray(0), emptyMap(), 0)
    }
}

/** The texts find on page looks through in a row, in the order the row shows them. */
private fun EntryRow.richTexts(entry: Entry): Sequence<RichText> = when (this) {
    EntryRow.Title -> listOfNotNull(entry.seeAlso).asSequence()
    is EntryRow.Intro -> lang.intro.richTexts()
    is EntryRow.SectionRow -> section.blocks.richTexts()
    else -> emptySequence()
}

private fun findIndexOf(entry: Entry, rows: List<EntryRow>, query: String): FindIndex {
    val hitRows = ArrayList<Int>()
    val firsts = ArrayList<Int>()
    val ranges = HashMap<Int, Map<RichText, IntRange>>()
    var total = 0
    rows.forEachIndexed { i, row ->
        val inRow = java.util.IdentityHashMap<RichText, IntRange>()
        var n = 0
        for (text in row.richTexts(entry)) {
            val count = matchStarts(text.text, query).size
            if (count > 0) {
                inRow[text] = n until n + count
                n += count
            }
        }
        if (n > 0) {
            hitRows += i
            firsts += total
            ranges[i] = inRow
            total += n
        }
    }
    return FindIndex(hitRows.toIntArray(), firsts.toIntArray(), ranges, total)
}

/**
 * How far below the list's top padding a language header lands: just below the pinned picker,
 * which sits 16dp higher than the padding (under the status bar) and is 76dp tall with its gap.
 */
internal val LanguagePickerOffset = 60.dp

/** How much surfaceContainerHigh the language picker's glass keeps: the search scallop's strength. */
private const val LanguagePickerGlassTint = 0.5f

/** How far below the list's top a language header lands: under the picker, or flush on two panes. */
internal fun languageScrollOffset(split: PaneSplit?): Dp = if (split != null) 0.dp else LanguagePickerOffset

/** List indices of each language's header, or none when there's only one language (and no picker). */
internal fun languageRowIndices(entry: Entry, overviewApart: Boolean): List<Int> =
    if (entry.languages.size < 2) emptyList()
    else rowsFor(entry, overviewApart).withIndex().filter { it.value is EntryRow.LanguageHeader }.map { it.index }

/**
 * Scrolls up one language: to the start of the one being read, or to the one before if already
 * at its start. Before the first language, or on single-language pages, goes to the top.
 */
internal suspend fun LazyListState.scrollUpOneLanguage(languageRows: List<Int>, pickerOffset: Int) {
    val line = pickerOffset + 8
    val visible = layoutInfo.visibleItemsInfo
    fun offsetOf(index: Int) = visible.firstOrNull { it.index == index }?.offset
    val passed = languageRows.filter { h -> h < firstVisibleItemIndex || (offsetOf(h)?.let { it <= line } ?: false) }
    val current = passed.lastOrNull()
    val atItsStart = current != null && (offsetOf(current)?.let { it >= line - 24 } ?: false)
    val target = if (atItsStart) passed.getOrNull(passed.size - 2) else current
    if (target == null) animateScrollToItem(0) else animateScrollToItem(target, -pickerOffset)
}

/** `German`, `German:_dog`, `Etymology_2`... the language part of a Wiktionary anchor. */
private fun languageIndexFor(entry: Entry, fragment: String): Int {
    val lang = fragment.substringBefore(":_").substringBefore(':')
    return entry.languages.indexOfFirst { it.id == lang || it.id == fragment || it.name.replace(' ', '_') == lang }
}

@Composable
private fun EntryView(
    entry: Entry,
    fragment: String?,
    listState: LazyListState,
    seen: MutableSet<Any>,
    contentPadding: PaddingValues,
    find: FindState?,
) {
    val split = LocalPaneSplit.current
    val overviewApart = split != null
    val rows = remember(entry, overviewApart) { rowsFor(entry, overviewApart) }
    val languageRows = remember(rows) { rows.withIndex().filter { it.value is EntryRow.LanguageHeader }.map { it.index } }
    val languageRowSet = remember(languageRows) { languageRows.toHashSet() }
    // When this entry appeared: only rows composed right away stagger in.
    val shownAt = remember(entry) { SystemClock.uptimeMillis() }
    val stickyOffset = with(LocalDensity.current) { languageScrollOffset(split).roundToPx() }
    val scope = rememberCoroutineScope()
    // The reading line: rows whose tops have passed it are being read. Under the pinned picker
    // on one pane; a quarter of the way down on two, where nothing is pinned over the list.
    fun readingLine(): Int {
        val info = listState.layoutInfo
        return if (overviewApart) (info.viewportEndOffset - info.viewportStartOffset) / 4 else stickyOffset + 8
    }
    val currentLanguage by remember(languageRows, overviewApart) {
        derivedStateOf {
            // The language whose header has most recently passed the reading line.
            val line = readingLine()
            val passed = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.index in languageRowSet && it.offset <= line }
            if (passed != null) {
                languageRows.indexOf(passed.index).coerceAtLeast(0)
            } else {
                languageRows.indexOfLast { it <= listState.firstVisibleItemIndex }.coerceAtLeast(0)
            }
        }
    }

    fun scrollToLanguage(index: Int) {
        if (index !in languageRows.indices) return
        scope.launch { listState.animateScrollToItem(languageRows[index], -stickyOffset) }
    }

    // Same-page anchors scroll; everything else goes up to the app.
    val outer = LocalLinkHandler.current
    val handler = remember(entry, outer, languageRows, stickyOffset) {
        { link: Link ->
            val fragment = (link as? Link.Wiki)?.takeIf { it.title == entry.title }?.fragment
            val target = if (fragment != null) languageIndexFor(entry, fragment) else -1
            if (target >= 0) scrollToLanguage(target) else outer(link)
        }
    }

    // Find on page: count the matches, step through them, light them up.
    val needle = find?.needle.orEmpty()
    val findIndex = remember(rows, needle) { if (needle.isEmpty()) FindIndex.Empty else findIndexOf(entry, rows, needle) }
    val currentFindIndex by rememberUpdatedState(findIndex)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val readingTop = if (split == null) contentPadding.calculateTopPadding() else split.endTopInset(statusTop) + 12.dp
    val density = LocalDensity.current
    val listTopPad = with(density) { readingTop.toPx() }
    var listTop by remember { mutableFloatStateOf(0f) }
    var findToken by remember { mutableIntStateOf(0) }
    val revealMatch: suspend (Float, Float) -> Boolean = remember(listState, density, find, stickyOffset, listTopPad) {
        { top, bottom ->
            // Let a jump to the row finish first, then nudge the match itself into the clear:
            // below the pinned picker, above the find bar (and the keyboard under it).
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            val margin = with(density) { 24.dp.toPx() }
            val listBottom = listTop + listState.layoutInfo.viewportSize.height
            val barTop = find?.barTop?.takeUnless { it.isNaN() } ?: listBottom
            val bandTop = listTop + listTopPad + stickyOffset + margin
            val bandBottom = minOf(barTop, listBottom) - margin
            if (top < bandTop || bottom > bandBottom) {
                // Land it a third of the way down the clear space, with room to read around it.
                val target = bandTop + (bandBottom - bandTop - (bottom - top)).coerceAtLeast(0f) / 3f
                listState.animateScrollBy(top - target)
                true
            } else {
                false
            }
        }
    }
    if (find != null) {
        // A new query starts from the first match at or below where you're reading.
        LaunchedEffect(findIndex) {
            find.total = findIndex.total
            find.current = findIndex.firstFrom(listState.firstVisibleItemIndex)
            find.steps++
        }
        LaunchedEffect(find.steps) {
            val index = currentFindIndex
            if (index.total == 0) return@LaunchedEffect
            val row = index.rows[index.rowOf(find.current.coerceIn(0, index.total - 1))]
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == row }) {
                listState.animateScrollToItem(row, -stickyOffset)
            }
            findToken++
        }
    }
    // Clamped: for a frame after the query narrows, current can still point past the new total.
    val activeMatch = if (find != null && findIndex.total > 0) find.current.coerceIn(0, findIndex.total - 1) else -1
    val activeHit = if (activeMatch >= 0) findIndex.rowOf(activeMatch) else -1
    val activeRow = if (activeHit >= 0) findIndex.rows[activeHit] else -1
    val activeOrdinal = if (activeHit >= 0) activeMatch - findIndex.firsts[activeHit] else -1

    /**
     * A row of reading text: selectable for copying (a section at a time; selections can't
     * span lazily composed rows reliably), and lit up by find on page. Find's highlight is
     * always provided, even as null, so rows keep their state as find opens and closes.
     */
    @Composable
    fun Findable(rowIndex: Int, content: @Composable () -> Unit) {
        val ranges = findIndex.ranges[rowIndex]
        val active = if (rowIndex == activeRow) activeOrdinal else -1
        val highlight = remember(ranges, needle, active, findToken) {
            ranges?.let { FindHighlight(needle, it, active, findToken, revealMatch) }
        }
        CompositionLocalProvider(LocalFindHighlight provides highlight) {
            SelectableText(content)
        }
    }

    LaunchedEffect(entry, fragment) {
        if (fragment != null && listState.firstVisibleItemIndex == 0) {
            val target = languageIndexFor(entry, fragment)
            if (target > 0) listState.scrollToItem(languageRows[target], -stickyOffset)
        }
    }

    @Composable
    fun Reading(top: Dp) {
        // The list runs right up to the top of the screen, so the page scrolls up under the
        // status bar instead of being cut off below it; the picker pins itself under the bar.
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = top, bottom = contentPadding.calculateBottomPadding()),
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { listTop = it.positionInRoot().y }
                .testTag("entry_list"),
        ) {
            rows.forEachIndexed { rowIndex, row ->
                when (row) {
                    EntryRow.Title -> item(key = row.key) {
                        val fresh = remember { row.key !in seen }
                        Findable(rowIndex) {
                            EntryTitle(entry, animate = fresh, modifier = Modifier.bouncyEnter(row.key, seen, 0))
                        }
                    }
                    EntryRow.Chips -> stickyHeader(key = row.key) {
                        LanguagePicker(entry.languages, currentLanguage, ::scrollToLanguage)
                    }
                    is EntryRow.LanguageHeader -> item(key = row.key) {
                        val order = rememberStagger(rowIndex.coerceAtMost(8), shownAt)
                        val fresh = remember { row.key !in seen }
                        LanguageHeader(row.lang, row.index, wobbleIn = fresh, modifier = Modifier.animateItem().bouncyEnter(row.key, seen, order))
                    }
                    is EntryRow.Strokes -> item(key = row.key) {
                        val order = rememberStagger(rowIndex.coerceAtMost(8), shownAt)
                        StrokeOrderCard(
                            row.lang.strokeOrder,
                            Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).bouncyEnter(row.key, seen, order),
                        )
                    }
                    is EntryRow.Intro -> item(key = row.key) {
                        val order = rememberStagger(rowIndex.coerceAtMost(8), shownAt)
                        Surface(
                            shape = segmentShape(row.first, row.last),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = if (row.last) 8.dp else 3.dp)
                                .bouncyEnter(row.key, seen, order),
                        ) {
                            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                                Findable(rowIndex) {
                                    BlockList(row.lang.intro, Modifier.fillMaxWidth().padding(18.dp))
                                }
                            }
                        }
                    }
                    is EntryRow.SectionRow -> item(key = row.key) {
                        val order = rememberStagger(rowIndex.coerceAtMost(8), shownAt)
                        Findable(rowIndex) {
                            SectionCard(row, reveal = rowIndex == activeRow, modifier = Modifier.bouncyEnter(row.key, seen, order))
                        }
                    }
                    is EntryRow.Categories -> item(key = row.key) {
                        CategoriesBox(row.groups, title = "Categories", modifier = Modifier.padding(top = 20.dp))
                    }
                }
            }
            item(key = "footer") {
                Text(
                    "Content from Wiktionary, available under CC BY-SA 4.0.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }

    CompositionLocalProvider(LocalLinkHandler provides handler) {
        if (split == null) {
            Reading(top = readingTop)
        } else {
            val outline = remember(rows) { outlineOf(entry, rows) }
            // The row at the reading line, for the overview's "you are here".
            val readingRow by remember {
                derivedStateOf {
                    val line = readingLine()
                    listState.layoutInfo.visibleItemsInfo.lastOrNull { it.offset <= line }?.index ?: listState.firstVisibleItemIndex
                }
            }
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Panes(
                split,
                start = {
                    EntryOverview(
                        entry = entry,
                        outline = outline,
                        currentLanguage = currentLanguage,
                        readingRow = { readingRow },
                        onJump = { row -> scope.launch { listState.animateScrollToItem(row) } },
                        contentPadding = PaddingValues(
                            top = contentPadding.calculateTopPadding(),
                            bottom = split.startBottomInset(navBottom) + 16.dp,
                        ),
                        seen = seen,
                    )
                },
                end = { Reading(top = readingTop) },
            )
        }
    }
}

/** Each language's header row in the reading list, and each of its sections' rows. */
private fun outlineOf(entry: Entry, rows: List<EntryRow>): List<OutlineLanguage> {
    val headers = HashMap<Int, Int>()
    val sections = HashMap<Int, MutableList<OutlineSection>>()
    rows.forEachIndexed { i, row ->
        when (row) {
            is EntryRow.LanguageHeader -> headers[row.index] = i
            is EntryRow.SectionRow -> sections.getOrPut(row.langIndex) { mutableListOf() }.add(OutlineSection(row.section, i))
            else -> Unit
        }
    }
    return entry.languages.mapIndexed { li, lang ->
        OutlineLanguage(lang, li, headers[li] ?: 0, sections[li].orEmpty())
    }
}

@Composable
internal fun EntryTitle(entry: Entry, animate: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Appendix, Thesaurus and Reconstruction pages lead with what kind of page they are, then
    // the bare name: "Reconstruction · Proto-Germanic" over "*watōr".
    val parts = remember(entry.title) { titleParts(entry.title) }
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp)) {
        NamespaceLabel(parts, modifier = Modifier.padding(bottom = 2.dp))
        PoppingTitle(
            parts.name,
            style = MaterialTheme.typography.displayMediumEmphasized,
            color = colors.onSurface,
            animate = animate,
            modifier = Modifier.testTag("entry_title"),
        )
        Spacer(Modifier.height(6.dp))
        val n = entry.languages.size
        // An appendix's top-level headings are its own topics, not languages.
        val unit = if (parts.namespace == Namespace.Appendix) "section" else "language"
        Text(
            if (n == 1) "1 $unit" else "$n ${unit}s",
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
        entry.seeAlso?.let {
            Spacer(Modifier.height(8.dp))
            RichTextView(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * The pinned language switcher: a pill naming the language you're reading, which springs open
 * into a vertical list of every language on the page. Pinning the whole list would bury the
 * entry on pages with dozens of languages.
 */
@Composable
private fun LanguagePicker(languages: List<LanguageEntry>, selected: Int, onSelect: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (open) 24.dp else 30.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "pickerCorner")
    val rotation by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "pickerChevron")
    if (selected !in languages.indices) return
    val spin by rememberSpin(16_000)
    val source = remember { MutableInteractionSource() }
    // Pinned at the very top of the list: clear of the status bar, with nothing painted around
    // the pill, so the page shows through either side of it rather than being cut off.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = statusTop + 8.dp, bottom = 8.dp)) {
        val shape = RoundedCornerShape(corner)
        // Frosted glass, as on the search scallop: the page blurs through its surfaceContainerHigh
        // tone at the scallop's strength. No shadow on glass, since it would darken through the
        // tint; before Android 17 QPR2 it keeps its solid fill and its lift when open.
        Surface(
            shape = shape,
            color = if (GlassSupported) Color.Transparent else colors.surfaceContainerHigh,
            shadowElevation = if (open && !GlassSupported) 6.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (GlassSupported) {
                        Modifier.glass(shape = shape, tint = colors.surfaceContainerHigh.copy(alpha = LanguagePickerGlassTint))
                    } else {
                        Modifier
                    }
                )
                .testTag("language_picker"),
        ) {
            Column {
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(if (open) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        open = !open
                    },
                    color = Color.Transparent,
                    interactionSource = source,
                    modifier = Modifier.fillMaxWidth().pressScale(source, 0.97f).testTag("language_picker_toggle"),
                ) {
                    Row(Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = {
                                val up = targetState > initialState
                                (slideInVertically(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) { if (up) it else -it } + fadeIn())
                                    .togetherWith(slideOutVertically { if (up) -it else it } + fadeOut())
                            },
                            label = "currentLanguage",
                            modifier = Modifier.weight(1f),
                        ) { index ->
                            val lang = languages[index]
                            val h = hueFor(lang.name)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ShapeBadge(badgeShape(index), h.container, size = 36.dp, rotation = { spin }) {
                                    Text(initials(lang.name), style = MaterialTheme.typography.labelLarge, color = h.content)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(lang.name, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f, fill = false))
                            }
                        }
                        Text(
                            "${selected + 1} of ${languages.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = if (open) "Close language list" else "Show all languages",
                            modifier = Modifier.graphicsLayer { rotationZ = rotation },
                        )
                    }
                }
                AnimatedVisibility(
                    open,
                    enter = expandVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
                ) {
                    val firstShown = remember { (selected - 2).coerceAtLeast(0) }
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstShown)
                    val seen = remember { HashSet<Any>() }
                    val openedAt = remember { SystemClock.uptimeMillis() }
                    val listSpin by rememberSpin(22_000)
                    // Keep drags here to the list: no toolbar tuck, no chaining into the page.
                    val claim = io.github.draumaz.lexeme.ui.components.LocalScrollClaim.current
                    val contain = remember(claim) {
                        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                            override fun onPostScroll(
                                consumed: androidx.compose.ui.geometry.Offset,
                                available: androidx.compose.ui.geometry.Offset,
                                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                            ): androidx.compose.ui.geometry.Offset {
                                claim.claimed = true
                                return available
                            }

                            override suspend fun onPostFling(
                                consumed: androidx.compose.ui.unit.Velocity,
                                available: androidx.compose.ui.unit.Velocity,
                            ) = available
                        }
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.heightIn(max = 380.dp).nestedScroll(contain).testTag("language_list"),
                    ) {
                        itemsIndexed(languages, key = { i, l -> "${l.id}-$i" }) { index, lang ->
                            val isSelected = index == selected
                            val h = hueFor(lang.name)
                            val bg by animateColorAsState(if (isSelected) colors.secondaryContainer else Color.Transparent, label = "optionBg")
                            val order = rememberStagger((index - firstShown).coerceIn(0, 8), openedAt)
                            Surface(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    open = false
                                    onSelect(index)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = bg,
                                modifier = Modifier.fillMaxWidth().bouncyEnter(lang.id + index, seen, order).testTag("language_option_$index"),
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ShapeBadge(badgeShape(index), h.container, size = 32.dp, rotation = { listSpin + index * 37f }) {
                                        Text(initials(lang.name), style = MaterialTheme.typography.labelMedium, color = h.content)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(lang.name, style = MaterialTheme.typography.bodyLarge)
                                        val pos = partsOfSpeech(lang)
                                        if (pos.isNotEmpty()) {
                                            Text(pos, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun partsOfSpeech(lang: LanguageEntry): String =
    lang.sections.filter { it.kind == SectionKind.PART_OF_SPEECH || it.kind == SectionKind.CHARACTER }
        .map { it.title }.distinct().joinToString(" · ")

internal fun initials(name: String): String {
    val words = name.split(' ', '-').filter { it.isNotBlank() }
    return if (words.size >= 2) "${words[0].first()}${words[1].first()}" else name.take(2)
}

@Composable
private fun LanguageHeader(lang: LanguageEntry, index: Int, wobbleIn: Boolean, modifier: Modifier = Modifier) {
    val hue = hueFor(lang.name)
    // Wobbles in on first sight only, not every time it scrolls back into view.
    val wobble = remember { Animatable(if (wobbleIn) -60f else 0f) }
    LaunchedEffect(Unit) { wobble.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = if (index == 0) 4.dp else 28.dp, bottom = 12.dp)
            .testTag("language_header_$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val spin by rememberSpin(20_000)
        ShapeBadge(badgeShape(index), hue.container, size = 54.dp, rotation = { wobble.value + spin }) {
            Text(initials(lang.name), style = MaterialTheme.typography.titleMediumEmphasized, color = hue.content)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(lang.name, style = MaterialTheme.typography.headlineMediumEmphasized)
            val n = lang.sections.count { it.kind == SectionKind.PART_OF_SPEECH || it.kind == SectionKind.CHARACTER }
            if (n > 0) {
                Text(
                    partsOfSpeech(lang),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal data class KindLook(val icon: ImageVector, val shape: RoundedPolygon)

internal fun lookOf(kind: SectionKind) = when (kind) {
    SectionKind.ETYMOLOGY -> KindLook(Icons.Rounded.History, MaterialShapes.Cookie9Sided)
    SectionKind.PRONUNCIATION -> KindLook(Icons.Rounded.RecordVoiceOver, MaterialShapes.Sunny)
    SectionKind.PART_OF_SPEECH -> KindLook(Icons.Rounded.AutoStories, MaterialShapes.Clover4Leaf)
    SectionKind.INFLECTION -> KindLook(Icons.Rounded.TableChart, MaterialShapes.Cookie4Sided)
    SectionKind.TRANSLATIONS -> KindLook(Icons.Rounded.Translate, MaterialShapes.Flower)
    SectionKind.RELATIONS -> KindLook(Icons.Rounded.Hub, MaterialShapes.SoftBurst)
    SectionKind.USAGE -> KindLook(Icons.Rounded.Lightbulb, MaterialShapes.Gem)
    SectionKind.CHARACTER -> KindLook(Icons.Rounded.Draw, MaterialShapes.Cookie6Sided)
    SectionKind.REFERENCES -> KindLook(Icons.AutoMirrored.Rounded.MenuBook, MaterialShapes.Pentagon)
    SectionKind.OTHER -> KindLook(Icons.AutoMirrored.Rounded.Notes, MaterialShapes.Puffy)
}

@Composable
private fun SectionCard(row: EntryRow.SectionRow, reveal: Boolean, modifier: Modifier = Modifier) {
    val section = row.section
    val kind = section.kind
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val hasBody = section.blocks.isNotEmpty()
    // References and anagrams are rarely what you came for; they start folded.
    var expanded by rememberSaveable {
        mutableStateOf(kind != SectionKind.REFERENCES && !section.title.startsWith("Anagrams"))
    }
    // Find on page stepping to a match in here opens it.
    LaunchedEffect(reveal, LocalFindHighlight.current?.token) { if (reveal) expanded = true }
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chevron")
    val badgeTurn by animateFloatAsState(if (expanded) 0f else -45f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "badgeTurn")
    val look = lookOf(kind)
    val (badgeColor, badgeContent) = when (kind) {
        SectionKind.PART_OF_SPEECH, SectionKind.CHARACTER -> colors.primaryContainer to colors.onPrimaryContainer
        SectionKind.ETYMOLOGY -> colors.tertiaryContainer to colors.onTertiaryContainer
        SectionKind.INFLECTION -> colors.primary to colors.onPrimary
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    val nested = (section.level - 3).coerceIn(0, 2)
    val source = remember { MutableInteractionSource() }

    Surface(
        shape = segmentShape(row.first, row.last),
        color = if (kind == SectionKind.PART_OF_SPEECH || kind == SectionKind.CHARACTER) colors.surfaceContainer else colors.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp + 10.dp * nested, end = 16.dp)
            .padding(bottom = if (row.last) 8.dp else 3.dp)
            .testTag("section_${section.title}"),
    ) {
        Column {
            Surface(
                onClick = {
                    if (!hasBody) return@Surface
                    haptics.performHapticFeedback(if (expanded) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    expanded = !expanded
                },
                color = Color.Transparent,
                interactionSource = source,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = if (expanded && hasBody) 6.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapeBadge(look.shape, badgeColor, size = 38.dp, rotation = { badgeTurn }) {
                        Icon(look.icon, null, Modifier.size(19.dp), tint = badgeContent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        section.title,
                        style = when {
                            kind == SectionKind.PART_OF_SPEECH || kind == SectionKind.CHARACTER -> MaterialTheme.typography.headlineSmallEmphasized
                            section.level <= 3 -> MaterialTheme.typography.titleLargeEmphasized
                            else -> MaterialTheme.typography.titleMediumEmphasized
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (hasBody) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Collapse ${section.title}" else "Expand ${section.title}",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { rotationZ = rotation },
                        )
                    }
                }
            }
            AnimatedVisibility(
                expanded && hasBody,
                enter = expandVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
            ) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyLarge,
                    LocalContentColor provides colors.onSurface,
                ) {
                    BlockList(section.blocks, Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp))
                }
            }
        }
    }
}
