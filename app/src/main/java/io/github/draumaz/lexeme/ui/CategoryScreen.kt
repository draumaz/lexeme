package io.github.draumaz.lexeme.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.draumaz.lexeme.data.Breadcrumb
import io.github.draumaz.lexeme.data.CategoryPage
import io.github.draumaz.lexeme.data.CategoryPrefix
import io.github.draumaz.lexeme.data.Link
import io.github.draumaz.lexeme.data.MemberKind
import io.github.draumaz.lexeme.ui.blocks.BlockList
import io.github.draumaz.lexeme.ui.blocks.LocalLinkHandler
import io.github.draumaz.lexeme.ui.components.PoppingTitle
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import io.github.draumaz.lexeme.ui.components.bouncyEnter
import io.github.draumaz.lexeme.ui.components.pressScale
import io.github.draumaz.lexeme.ui.components.segmentShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.text.NumberFormat

/** Category chips shown before "Show all": entries like "water" sit in hundreds. */
private const val CategoryPreview = 24

/** Start fetching the next batch of pages when this few rows are left below the screen. */
private const val PrefetchRows = 30

/** One heading's worth of categories in the box; [label] is null when there's only one group. */
internal data class CategoryGroup(val label: String?, val categories: List<String>)

/**
 * Groups an entry's categories under the page's languages, the way they read on the site:
 * `Russian adverbs` and `Rhymes:Russian/ot͡ɕ` go under Russian. The longest matching name wins,
 * so `Old East Slavic lemmas` isn't filed under a page's `Old` anything. Whatever matches no
 * language comes last, under "Other". Only one group means no headings at all.
 */
internal fun groupCategories(categories: List<String>, languages: List<String>): List<CategoryGroup> {
    if (categories.isEmpty()) return emptyList()
    val byLength = languages.distinct().sortedByDescending { it.length }
    fun languageOf(category: String): String? {
        val bare = category.substringAfter(':')
        return byLength.firstOrNull { lang ->
            listOf(category, bare).any { it.startsWith("$lang ") || it.startsWith("$lang/") || it == lang }
        }
    }
    val grouped = categories.groupBy(::languageOf)
    val groups = languages.distinct().mapNotNull { lang -> grouped[lang]?.let { CategoryGroup(lang, it) } } +
        listOfNotNull(grouped[null]?.let { CategoryGroup("Other", it) })
    return if (groups.size == 1) listOf(groups[0].copy(label = null)) else groups
}

/**
 * The categories box at the foot of an entry (or a category's own parents): tappable chips,
 * grouped by language when the page has several.
 */
@Composable
internal fun CategoriesBox(
    groups: List<CategoryGroup>,
    title: String,
    modifier: Modifier = Modifier,
) {
    val total = groups.sumOf { it.categories.size }
    if (total == 0) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("categories"),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(MaterialShapes.Cookie7Sided, colors.secondaryContainer, size = 38.dp) {
                    Icon(Icons.Rounded.Category, null, Modifier.size(19.dp), tint = colors.onSecondaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                Text("$total", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            // Show the first chips across groups, keeping their headings, until the preview runs out.
            var budget = if (showAll) Int.MAX_VALUE else CategoryPreview
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (group in groups) {
                    if (budget <= 0) break
                    val shown = group.categories.take(budget)
                    budget -= shown.size
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.label?.let {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            shown.forEach { CategoryChip(it) }
                        }
                    }
                }
                if (total > CategoryPreview && !showAll) {
                    val haptics = LocalHapticFeedback.current
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            showAll = true
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.testTag("categories_show_all"),
                    ) {
                        Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Show all $total")
                    }
                }
            }
        }
    }
}

/** A category to tap through to; [name] is without its `Category:` prefix. */
@Composable
private fun CategoryChip(name: String, icon: Boolean = false) {
    val onLink = LocalLinkHandler.current
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = { onLink(Link.Wiki(CategoryPrefix + name, null)) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        interactionSource = source,
        modifier = Modifier.pressScale(source, 0.92f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon) {
                Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val Count = NumberFormat.getIntegerInstance()

private fun plural(n: Int, one: String, many: String) = "${Count.format(n)} ${if (n == 1) one else many}"

/** "62,292 pages · 16 subcategories", skipping whichever is none. */
private fun countsLine(category: CategoryPage): String = listOfNotNull(
    category.pageCount.takeIf { it > 0 }?.let { plural(it, "page", "pages") },
    category.subcategoryCount.takeIf { it > 0 }?.let { plural(it, "subcategory", "subcategories") },
).joinToString(" · ").ifEmpty { "Empty category" }

/** The category's name, counts and breadcrumb trail: atop the list, or the start pane on two. */
@Composable
private fun CategoryHeader(category: CategoryPage, animate: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp)) {
        Text("Category", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        PoppingTitle(
            category.name,
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = colors.onSurface,
            animate = animate,
            modifier = Modifier.testTag("category_title"),
        )
        Spacer(Modifier.height(6.dp))
        Text(countsLine(category), style = MaterialTheme.typography.labelLarge, color = colors.primary)
        if (category.breadcrumbs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Breadcrumbs(category.breadcrumbs)
        }
    }
}

/** Fundamental › All languages › Russian › …, each step a link up the tree. */
@Composable
private fun Breadcrumbs(trail: List<Breadcrumb>) {
    val onLink = LocalLinkHandler.current
    val colors = MaterialTheme.colorScheme
    FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        trail.forEachIndexed { i, crumb ->
            if (i > 0) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    Modifier.size(16.dp), tint = colors.onSurfaceVariant,
                )
            }
            Surface(
                onClick = { onLink(Link.Wiki(CategoryPrefix + crumb.name, null)) },
                shape = RoundedCornerShape(8.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = colors.primary,
            ) {
                Text(crumb.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(Count.format(count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The tail of a member list: a spinner while the next batch loads, a retry if it didn't. */
@Composable
private fun MoreRow(loading: Boolean, failed: Boolean, label: String, onMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            loading -> LoadingIndicator(Modifier.size(48.dp))
            else -> FilledTonalButton(onClick = onMore, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("category_more")) {
                Icon(if (failed) Icons.Rounded.Refresh else Icons.Rounded.UnfoldMore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (failed) "Couldn't load more. Try again" else label)
            }
        }
    }
}

@Composable
internal fun CategoryView(
    state: PageState.Category,
    listState: LazyListState,
    seen: MutableSet<Any>,
    contentPadding: PaddingValues,
    onLoadMore: (MemberKind) -> Unit,
) {
    val category = state.category
    val split = LocalPaneSplit.current
    val pages = category.pages
    val subcategories = remember(category.subcategories) {
        category.subcategories.titles.map { it.removePrefix(CategoryPrefix) }
    }
    val parents = remember(category.parents) { groupCategories(category.parents, emptyList()) }
    val onLink = LocalLinkHandler.current
    val haptics = LocalHapticFeedback.current

    // Pages stream in as you near the end of what's loaded; nothing to tap.
    val latestState by rememberUpdatedState(state)
    val latestLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - PrefetchRows
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                val s = latestState
                if (s.category.pages.next != null && s.loading == null && s.failed != MemberKind.Pages) {
                    latestLoadMore(MemberKind.Pages)
                }
            }
    }

    @Composable
    fun Reading(top: androidx.compose.ui.unit.Dp, withHeader: Boolean) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = top, bottom = contentPadding.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize().testTag("category_list"),
        ) {
            if (withHeader) item(key = "header") {
                val fresh = remember { "header" !in seen }
                CategoryHeader(category, animate = fresh, modifier = Modifier.bouncyEnter("header", seen, 0))
            }
            if (category.description.isNotEmpty()) item(key = "description") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).bouncyEnter("description", seen, 1),
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyLarge,
                    ) {
                        io.github.draumaz.lexeme.ui.components.SelectableText {
                            BlockList(category.description, Modifier.fillMaxWidth().padding(18.dp))
                        }
                    }
                }
            }
            if (subcategories.isNotEmpty()) {
                item(key = "subcats-label") { SectionLabel("Subcategories", category.subcategoryCount) }
                item(key = "subcats") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp).bouncyEnter("subcats", seen, 2).testTag("subcategories"),
                    ) {
                        subcategories.forEach { CategoryChip(it, icon = true) }
                    }
                }
                if (category.subcategories.next != null) item(key = "subcats-more") {
                    MoreRow(
                        loading = state.loading == MemberKind.Subcategories,
                        failed = state.failed == MemberKind.Subcategories,
                        label = "More subcategories",
                    ) { onLoadMore(MemberKind.Subcategories) }
                }
            }
            if (pages.titles.isNotEmpty()) {
                item(key = "pages-label") { SectionLabel("Pages", category.pageCount) }
                // Keyed by position: a category can list the same title under two sort keys.
                itemsIndexed(pages.titles, key = { i, _ -> "page-$i" }) { i, title ->
                    val source = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onLink(Link.Wiki(title, null))
                        },
                        shape = segmentShape(first = i == 0, last = i == pages.titles.lastIndex && pages.next == null, outer = 24.dp, inner = 6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        interactionSource = source,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 3.dp)
                            .pressScale(source, 0.97f)
                            .bouncyEnter("page-$i", seen, i.coerceAtMost(8)),
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
                if (pages.next != null) item(key = "pages-more") {
                    MoreRow(
                        loading = state.loading == MemberKind.Pages,
                        failed = state.failed == MemberKind.Pages,
                        label = "More pages",
                    ) { onLoadMore(MemberKind.Pages) }
                }
            }
            if (subcategories.isEmpty() && pages.titles.isEmpty()) item(key = "empty") {
                Text(
                    "Nothing in this category yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            if (parents.isNotEmpty()) item(key = "parents") {
                CategoriesBox(parents, title = "Parent categories", modifier = Modifier.padding(top = 16.dp))
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

    if (split == null) {
        Reading(top = contentPadding.calculateTopPadding(), withHeader = true)
    } else {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Panes(
            split,
            start = {
                val fresh = remember { "overview" !in seen }
                // A deep trail can outgrow a short pane (stacked across a hinge), so it scrolls.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    CategoryHeader(
                        category,
                        animate = fresh,
                        modifier = Modifier
                            .padding(top = contentPadding.calculateTopPadding(), bottom = split.startBottomInset(navBottom))
                            .bouncyEnter("overview", seen, 0),
                    )
                }
            },
            end = { Reading(top = split.endTopInset(statusTop) + 12.dp, withHeader = false) },
        )
    }
}
