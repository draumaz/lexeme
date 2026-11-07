package io.github.draumaz.lexeme.ui

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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notes
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import io.github.draumaz.lexeme.data.Entry
import io.github.draumaz.lexeme.data.LanguageEntry
import io.github.draumaz.lexeme.data.Link
import io.github.draumaz.lexeme.data.Section
import io.github.draumaz.lexeme.data.SectionKind
import io.github.draumaz.lexeme.ui.blocks.BlockList
import io.github.draumaz.lexeme.ui.blocks.LocalLinkHandler
import io.github.draumaz.lexeme.ui.blocks.RichTextView
import io.github.draumaz.lexeme.ui.blocks.StrokeOrderCard
import io.github.draumaz.lexeme.ui.components.PoppingTitle
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import io.github.draumaz.lexeme.ui.components.badgeShape
import io.github.draumaz.lexeme.ui.components.bouncyEnter
import io.github.draumaz.lexeme.ui.components.hueFor
import io.github.draumaz.lexeme.ui.components.pressScale
import io.github.draumaz.lexeme.ui.components.segmentShape
import io.github.draumaz.lexeme.ui.theme.Serif
import kotlinx.coroutines.launch

@Composable
fun PageScreen(
    page: Page,
    listState: LazyListState,
    seen: MutableSet<Any>,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
) {
    AnimatedContent(
        targetState = page.state,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.94f))
                .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 1.04f))
        },
        label = "pageState",
    ) { state ->
        when (state) {
            PageState.Loading -> LoadingView(page.title, contentPadding)
            is PageState.Missing -> MissingView(page.title, state.suggestions, contentPadding, onOpen)
            is PageState.Failed -> FailedView(state.message, contentPadding, onRetry)
            is PageState.Loaded -> EntryView(state.entry, page.fragment, listState, seen, contentPadding)
        }
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
            "Looking up “$title”",
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
                        onClick = { onOpen(s) },
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
}

private fun rowsFor(entry: Entry): List<EntryRow> = buildList {
    add(EntryRow.Title)
    if (entry.languages.size > 1) add(EntryRow.Chips)
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
) {
    val rows = remember(entry) { rowsFor(entry) }
    val languageRows = remember(rows) { rows.withIndex().filter { it.value is EntryRow.LanguageHeader }.map { it.index } }
    val stickyOffset = with(LocalDensity.current) { 76.dp.roundToPx() }
    val scope = rememberCoroutineScope()
    val currentLanguage by remember(languageRows) {
        derivedStateOf {
            // The language whose header has most recently passed under the pinned picker.
            val info = listState.layoutInfo
            val line = stickyOffset + 8
            val passed = info.visibleItemsInfo.lastOrNull { it.key.toString().startsWith("lang-") && it.offset <= line }
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
    val handler = remember(entry, outer) {
        { link: Link ->
            val sameEntry = link is Link.Wiki && link.title == entry.title && link.fragment != null
            val target = if (sameEntry) languageIndexFor(entry, (link as Link.Wiki).fragment!!) else -1
            if (target >= 0) scrollToLanguage(target) else outer(link)
        }
    }

    LaunchedEffect(entry, fragment) {
        if (fragment != null && listState.firstVisibleItemIndex == 0) {
            val target = languageIndexFor(entry, fragment)
            if (target > 0) listState.scrollToItem(languageRows[target], -stickyOffset)
        }
    }

    CompositionLocalProvider(LocalLinkHandler provides handler) {
        // The viewport starts below the search bar (rather than padding the content) so the
        // pinned language picker sticks under the bar instead of sliding behind it.
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .testTag("entry_list"),
        ) {
            rows.forEachIndexed { rowIndex, row ->
                val order = rowIndex.coerceAtMost(8)
                when (row) {
                    EntryRow.Title -> item(key = row.key) {
                        EntryTitle(entry, Modifier.bouncyEnter(row.key, seen, 0))
                    }
                    EntryRow.Chips -> stickyHeader(key = row.key) {
                        LanguagePicker(entry.languages, currentLanguage, ::scrollToLanguage)
                    }
                    is EntryRow.LanguageHeader -> item(key = row.key) {
                        LanguageHeader(row.lang, row.index, Modifier.animateItem().bouncyEnter(row.key, seen, order))
                    }
                    is EntryRow.Strokes -> item(key = row.key) {
                        StrokeOrderCard(
                            row.lang.strokeOrder,
                            Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).bouncyEnter(row.key, seen, order),
                        )
                    }
                    is EntryRow.Intro -> item(key = row.key) {
                        Surface(
                            shape = segmentShape(row.first, row.last),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = if (row.last) 8.dp else 3.dp)
                                .bouncyEnter(row.key, seen, order),
                        ) {
                            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                                BlockList(row.lang.intro, Modifier.fillMaxWidth().padding(18.dp))
                            }
                        }
                    }
                    is EntryRow.SectionRow -> item(key = row.key) {
                        SectionCard(row, Modifier.bouncyEnter(row.key, seen, order))
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
}

@Composable
private fun EntryTitle(entry: Entry, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp)) {
        PoppingTitle(
            entry.title,
            style = MaterialTheme.typography.displayMediumEmphasized.copy(fontFamily = Serif),
            color = colors.onSurface,
            modifier = Modifier.testTag("entry_title"),
        )
        Spacer(Modifier.height(6.dp))
        val n = entry.languages.size
        Text(
            if (n == 1) "1 language" else "$n languages",
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
    val current = languages.getOrNull(selected) ?: return
    val hue = hueFor(current.name)
    val source = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(corner),
            color = colors.surfaceContainerHigh,
            shadowElevation = if (open) 6.dp else 0.dp,
            modifier = Modifier.fillMaxWidth().testTag("language_picker"),
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
                                ShapeBadge(badgeShape(index), h.container, size = 36.dp) {
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
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0))
                    val seen = remember { HashSet<Any>() }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.heightIn(max = 380.dp).testTag("language_list"),
                    ) {
                        itemsIndexed(languages, key = { i, l -> "${l.id}-$i" }) { index, lang ->
                            val isSelected = index == selected
                            val h = hueFor(lang.name)
                            val bg by animateColorAsState(if (isSelected) colors.secondaryContainer else Color.Transparent, label = "optionBg")
                            Surface(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    open = false
                                    onSelect(index)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = bg,
                                modifier = Modifier.fillMaxWidth().bouncyEnter(lang.id + index, seen, index - listState.firstVisibleItemIndex).testTag("language_option_$index"),
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ShapeBadge(badgeShape(index), h.container, size = 32.dp) {
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

private fun partsOfSpeech(lang: LanguageEntry): String =
    lang.sections.filter { it.kind == SectionKind.PART_OF_SPEECH || it.kind == SectionKind.CHARACTER }
        .map { it.title }.distinct().joinToString(" · ")

private fun initials(name: String): String {
    val words = name.split(' ', '-').filter { it.isNotBlank() }
    return if (words.size >= 2) "${words[0].first()}${words[1].first()}" else name.take(2)
}

@Composable
private fun LanguageHeader(lang: LanguageEntry, index: Int, modifier: Modifier = Modifier) {
    val hue = hueFor(lang.name)
    val wobble = remember { Animatable(-60f) }
    LaunchedEffect(Unit) { wobble.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = if (index == 0) 4.dp else 28.dp, bottom = 12.dp)
            .testTag("language_header_$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeBadge(badgeShape(index), hue.container, size = 54.dp, rotation = { wobble.value }) {
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

private data class KindLook(val icon: ImageVector, val shape: RoundedPolygon)

private fun lookOf(kind: SectionKind) = when (kind) {
    SectionKind.ETYMOLOGY -> KindLook(Icons.Rounded.History, MaterialShapes.Cookie9Sided)
    SectionKind.PRONUNCIATION -> KindLook(Icons.Rounded.RecordVoiceOver, MaterialShapes.Sunny)
    SectionKind.PART_OF_SPEECH -> KindLook(Icons.Rounded.AutoStories, MaterialShapes.Clover4Leaf)
    SectionKind.INFLECTION -> KindLook(Icons.Rounded.TableChart, MaterialShapes.Cookie4Sided)
    SectionKind.TRANSLATIONS -> KindLook(Icons.Rounded.Translate, MaterialShapes.Flower)
    SectionKind.RELATIONS -> KindLook(Icons.Rounded.Hub, MaterialShapes.SoftBurst)
    SectionKind.USAGE -> KindLook(Icons.Rounded.Lightbulb, MaterialShapes.Gem)
    SectionKind.CHARACTER -> KindLook(Icons.Rounded.Draw, MaterialShapes.Cookie6Sided)
    SectionKind.REFERENCES -> KindLook(Icons.Rounded.MenuBook, MaterialShapes.Pentagon)
    SectionKind.OTHER -> KindLook(Icons.Rounded.Notes, MaterialShapes.Puffy)
}

@Composable
private fun SectionCard(row: EntryRow.SectionRow, modifier: Modifier = Modifier) {
    val section = row.section
    val kind = section.kind
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val hasBody = section.blocks.isNotEmpty()
    // References and anagrams are rarely what you came for; they start folded.
    var expanded by rememberSaveable {
        mutableStateOf(kind != SectionKind.REFERENCES && !section.title.startsWith("Anagrams"))
    }
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
