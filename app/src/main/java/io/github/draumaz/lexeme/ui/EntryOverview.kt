package io.github.draumaz.lexeme.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.draumaz.lexeme.data.Entry
import io.github.draumaz.lexeme.data.LanguageEntry
import io.github.draumaz.lexeme.data.Section
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.draumaz.lexeme.ui.components.LocalScrollClaim
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import io.github.draumaz.lexeme.ui.components.badgeShape
import io.github.draumaz.lexeme.ui.components.bouncyEnter
import io.github.draumaz.lexeme.ui.components.hueFor
import io.github.draumaz.lexeme.ui.components.rememberSpin

/** A language on the page, with where its header and sections sit in the reading list. */
internal class OutlineLanguage(val lang: LanguageEntry, val index: Int, val headerRow: Int, val sections: List<OutlineSection>)

internal class OutlineSection(val section: Section, val row: Int)

/** Rows above the languages in the overview list: the title and the "Contents" label. */
private const val OverviewLeadingRows = 2

/**
 * The start pane on two-pane layouts: the word, then the page's contents. Each language is a
 * card that starts folded and opens into its sections, the one under the reading line
 * highlighted; any of them can be tapped to jump the reading pane there. The language being
 * read is lit up, and the list follows along as the reading pane scrolls.
 */
@Composable
internal fun EntryOverview(
    entry: Entry,
    outline: List<OutlineLanguage>,
    currentLanguage: Int,
    readingRow: () -> Int,
    onJump: (row: Int) -> Unit,
    contentPadding: PaddingValues,
    seen: MutableSet<Any>,
) {
    val listState = rememberLazyListState()
    // Keep the language being read in view as the reading pane moves through a long page.
    LaunchedEffect(currentLanguage) {
        val target = OverviewLeadingRows + currentLanguage
        val visible = listState.layoutInfo.visibleItemsInfo
        val shown = visible.firstOrNull { it.index == target }
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        if (shown == null || shown.offset < 0 || shown.offset + 120 > viewportEnd) {
            listState.animateScrollToItem(target)
        }
    }
    // Its scrolls are its own: they shouldn't tuck the reading pane's toolbar away.
    val claim = LocalScrollClaim.current
    val ownScrolls = remember(claim) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                claim.claimed = true
                return Offset.Zero
            }
        }
    }
    // Like the reading list, the viewport starts below the search bar so nothing scrolls behind it.
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .nestedScroll(ownScrolls)
            .testTag("entry_overview"),
    ) {
        item(key = "title") {
            val fresh = remember { "overview-title" !in seen }
            EntryTitle(entry, animate = fresh, modifier = Modifier.bouncyEnter("overview-title", seen, 0))
        }
        item(key = "contents") {
            Text(
                "Contents",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 6.dp),
            )
        }
        itemsIndexed(outline, key = { i, l -> "${l.lang.id}-$i" }) { index, language ->
            OverviewLanguage(
                language = language,
                selected = index == currentLanguage,
                readingRow = readingRow,
                onJump = onJump,
                modifier = Modifier.bouncyEnter("overview-${language.lang.id}-$index", seen, 1 + index.coerceAtMost(8)),
            )
        }
    }
}

@Composable
private fun OverviewLanguage(
    language: OutlineLanguage,
    selected: Boolean,
    readingRow: () -> Int,
    onJump: (row: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val lang = language.lang
    val hue = hueFor(lang.name)
    val spin by rememberSpin(20_000)
    // Folded to start, as on the phone; tapping the language opens it and jumps there.
    var expanded by rememberSaveable(lang.id, language.index) { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 0f else -90f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "overviewChevron")
    val hasSections = language.sections.isNotEmpty()
    val card by animateColorAsState(
        if (selected) colors.surfaceContainer else Color.Transparent,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "overviewCard",
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = card,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .testTag("overview_language_${language.index}"),
    ) {
        Column {
            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    expanded = true
                    onJump(language.headerRow)
                },
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(badgeShape(language.index), hue.container, size = 40.dp, rotation = { spin + language.index * 37f }) {
                        Text(initials(lang.name), style = MaterialTheme.typography.labelLarge, color = hue.content)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(lang.name, style = MaterialTheme.typography.titleMediumEmphasized)
                        val pos = partsOfSpeech(lang)
                        if (pos.isNotEmpty()) {
                            Text(
                                pos,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (hasSections) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(if (expanded) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                                expanded = !expanded
                            },
                            modifier = Modifier.testTag("overview_toggle_${language.index}"),
                        ) {
                            Icon(
                                Icons.Rounded.ExpandMore,
                                contentDescription = if (expanded) "Collapse ${lang.name}" else "Expand ${lang.name}",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { rotationZ = chevron },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                expanded && hasSections,
                enter = expandVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
            ) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    val row = readingRow()
                    val active = language.sections.lastOrNull { it.row <= row }?.row
                    language.sections.forEach { entry ->
                        OverviewSection(entry, active = entry.row == active) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onJump(entry.row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(entry: OutlineSection, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val section = entry.section
    val bg by animateColorAsState(
        if (active) colors.secondaryContainer else Color.Transparent,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "overviewSection",
    )
    val content = if (active) colors.onSecondaryContainer else colors.onSurface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        contentColor = content,
        modifier = Modifier.fillMaxWidth().testTag("overview_section_${section.title}"),
    ) {
        Row(
            Modifier.padding(start = 14.dp + 16.dp * (section.level - 3).coerceIn(0, 2), end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                lookOf(section.kind).icon,
                contentDescription = null,
                tint = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                section.title,
                style = if (section.level <= 3) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
