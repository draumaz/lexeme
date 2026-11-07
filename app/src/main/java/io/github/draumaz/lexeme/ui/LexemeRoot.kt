package io.github.draumaz.lexeme.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.draumaz.lexeme.data.Link
import io.github.draumaz.lexeme.ui.blocks.AudioController
import io.github.draumaz.lexeme.ui.blocks.LocalAudio
import io.github.draumaz.lexeme.ui.blocks.LocalLinkHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URLEncoder

fun wiktionaryUrl(title: String): String =
    "https://en.wiktionary.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("%2F", "/")

@OptIn(ExperimentalComposeUiApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LexemeRoot(
    vm: LexemeViewModel,
    settings: io.github.draumaz.lexeme.data.SettingsStore,
    openExternal: (String) -> Unit,
    share: (String) -> Unit,
) {
    val pages by vm.pages.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val randomBusy by vm.randomBusy.collectAsStateWithLifecycle()

    // Two search fields: the one pinned atop entries, and the one the scallop turns into,
    // docked above the keyboard. Each tracks its own focus so only one opens its dropdown.
    var topFocused by remember { mutableStateOf(false) }
    var dockedFocused by remember { mutableStateOf(false) }
    val focused = topFocused || dockedFocused
    var settingsOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // Set by the search scallop; the docked bar composes (morphing out of it), then takes focus.
    var dockedSearch by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val dockedFocus = remember { FocusRequester() }
    LaunchedEffect(dockedSearch) {
        if (dockedSearch) {
            androidx.compose.runtime.withFrameNanos { }
            dockedFocus.requestFocus()
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val audio = remember { AudioController(context.applicationContext) }
    DisposableEffect(audio) { onDispose { audio.stop() } }

    // Per-page scroll positions and "already bounced in" sets, so going back lands where you were.
    val listStates = remember { mutableMapOf<Long, LazyListState>() }
    val seenSets = remember { mutableMapOf<Long, MutableSet<Any>>() }
    // A new page (from a link, or from another app's "Look up") always drops the search
    // field's focus, so the keyboard never lingers over the entry.
    val topId = pages.lastOrNull()?.id
    LaunchedEffect(topId) {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    LaunchedEffect(pages) {
        val ids = pages.map { it.id }.toSet()
        listStates.keys.retainAll(ids)
        seenSets.keys.retainAll(ids)
        audio.stop()
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    fun submit(text: String) {
        if (text.isBlank()) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        focusManager.clearFocus()
        keyboard?.hide()
        vm.open(text)
    }

    val linkHandler: (Link) -> Unit = remember(vm) {
        { link ->
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            when (link) {
                is Link.Wiki -> vm.open(link.title, link.fragment)
                is Link.External -> openExternal(link.url)
            }
        }
    }

    // Predictive back shrinks the current page under the finger, then pops it.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = pages.isNotEmpty() && !focused) { events ->
        try {
            events.collect { backProgress = it.progress }
            vm.back()
        } catch (e: CancellationException) {
            throw e
        } finally {
            backProgress = 0f
        }
    }
    BackHandler(enabled = focused) { focusManager.clearFocus() }
    // Settings get the same treatment: shrink under the finger, then slide away.
    var settingsBack by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = settingsOpen) { events ->
        try {
            events.collect { settingsBack = it.progress }
            settingsOpen = false
        } catch (e: CancellationException) {
            throw e
        } finally {
            settingsBack = 0f
        }
    }

    // The toolbar tucks into its FAB while reading down, and comes back on any upward scroll.
    // Scrolls claimed by an inner list (the language picker) don't count.
    var toolbarExpanded by remember { mutableStateOf(true) }
    val scrollClaim = remember { io.github.draumaz.lexeme.ui.components.ScrollClaim() }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (scrollClaim.claimed) {
                    scrollClaim.claimed = false
                    return Offset.Zero
                }
                val dy = consumed.y + available.y
                if (dy < -8f) toolbarExpanded = false else if (dy > 8f) toolbarExpanded = true
                return Offset.Zero
            }
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(top = top + 84.dp, bottom = bottom + 112.dp)
    val current = pages.lastOrNull()

    CompositionLocalProvider(
        LocalLinkHandler provides linkHandler,
        io.github.draumaz.lexeme.ui.components.LocalScrollClaim provides scrollClaim,
        LocalAudio provides audio,
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        SharedTransitionLayout {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .nestedScroll(nested)
                .semantics { testTagsAsResourceId = true }
        ) {
            AnimatedContent(
                targetState = current,
                contentKey = { it?.id ?: 0L },
                transitionSpec = {
                    val forward = (targetState?.id ?: 0L) > (initialState?.id ?: 0L)
                    val slide = spring<IntOffset>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                    val bounce = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
                    if (forward) {
                        (slideInHorizontally(slide) { it / 3 } + scaleIn(bounce, initialScale = 0.9f) + fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally(slide) { -it / 6 } + scaleOut(targetScale = 0.94f) + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(slide) { -it / 6 } + scaleIn(bounce, initialScale = 0.94f) + fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally(slide) { it / 3 } + scaleOut(targetScale = 0.88f) + fadeOut(tween(180)))
                            .apply { targetContentZIndex = -1f }
                    }
                },
                label = "pages",
            ) { page ->
                val isTop = page?.id == current?.id
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = if (isTop && page != null) backProgress else 0f
                            val s = 1f - 0.1f * p
                            scaleX = s
                            scaleY = s
                            translationX = p * 32.dp.toPx()
                            shape = RoundedCornerShape(40.dp * p)
                            clip = p > 0f
                        }
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (page == null) {
                        HomeScreen(
                            recent = recent,
                            randomBusy = randomBusy,
                            onOpen = ::submit,
                            onRandom = vm::random,
                            onClearHistory = vm::clearHistory,
                            onOpenSettings = { settingsOpen = true },
                        )
                    } else {
                        PageScreen(
                            page = page,
                            listState = listStates.getOrPut(page.id) { LazyListState() },
                            seen = seenSets.getOrPut(page.id) { HashSet() },
                            contentPadding = contentPadding,
                            onOpen = ::submit,
                            onRetry = { vm.retry(page) },
                        )
                    }
                }
            }

            AnimatedVisibility(focused, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable(interactionSource = null, indication = null) { focusManager.clearFocus() }
                )
            }

            @Composable
            fun Bar(
                requester: FocusRequester,
                fromBottom: Boolean,
                modifier: Modifier,
                fieldModifier: Modifier = Modifier,
            ) = SearchBar(
                query = query,
                onQueryChange = { vm.query.value = it },
                onSubmit = ::submit,
                focused = if (fromBottom) dockedFocused else topFocused,
                onFocusChange = {
                    if (fromBottom) {
                        // Losing focus folds the docked bar back into the scallop.
                        if (dockedFocused && !it) dockedSearch = false
                        dockedFocused = it
                    } else {
                        topFocused = it
                    }
                },
                focusRequester = requester,
                canGoBack = pages.isNotEmpty(),
                onBack = {
                    if (focused) focusManager.clearFocus() else vm.back()
                },
                suggestions = suggestions,
                recent = recent,
                fromBottom = fromBottom,
                modifier = modifier,
                fieldModifier = fieldModifier,
            )

            // Entry pages keep the bar pinned up top.
            AnimatedVisibility(
                visible = current != null,
                enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { -it } + fadeIn(),
                exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .fillMaxWidth(),
            ) {
                Bar(focusRequester, fromBottom = false, Modifier.fillMaxWidth())
            }

            // The search scallop's bar: docked above the keyboard, its field morphing out of the
            // scallop (shared bounds), and back into it when search closes.
            AnimatedVisibility(
                visible = dockedSearch || dockedFocused,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Bar(
                    dockedFocus,
                    fromBottom = true,
                    Modifier.fillMaxSize(),
                    fieldModifier = Modifier.sharedBounds(
                        rememberSharedContentState(SearchMorphKey),
                        animatedVisibilityScope = this,
                        boundsTransform = SearchMorphBounds,
                    ),
                )
            }

            // One rotating search scallop, bottom centre on every screen. On entries the actions
            // sit in two see-through pills either side of it, and tuck into it while reading down.
            AnimatedVisibility(
                visible = !focused && !dockedSearch,
                enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = scaleOut(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                val title = (current?.state as? PageState.Loaded)?.entry?.title ?: current?.title.orEmpty()
                val pillsShown = current != null && toolbarExpanded
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Back to top floats just above the scallop, so the pills either side stay two and two.
                AnimatedVisibility(
                    visible = pillsShown,
                    enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.3f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                        slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(),
                    exit = scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.3f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                        slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it / 2 } + fadeOut(),
                ) {
                    Surface(
                        onClick = {
                            val id = current?.id ?: return@Surface
                            scope.launch { listStates[id]?.animateScrollToItem(0) }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 10.dp).size(48.dp).testTag("toolbar_top"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Back to top")
                        }
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SearchFab(
                        onClick = { dockedSearch = true },
                        modifier = Modifier.sharedBounds(
                            rememberSharedContentState(SearchMorphKey),
                            animatedVisibilityScope = this@AnimatedVisibility,
                            boundsTransform = SearchMorphBounds,
                        ),
                    )
                    // Left pill ends at the scallop's left edge, right pill starts at its right edge.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            ToolbarPill(pillsShown, towardEnd = true, tag = "toolbar") {
                                IconButton(onClick = { vm.home() }, modifier = Modifier.testTag("toolbar_home")) {
                                    Icon(Icons.Rounded.Home, contentDescription = "Home")
                                }
                                IconButton(onClick = vm::random, modifier = Modifier.testTag("toolbar_random")) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = "Random word")
                                }
                            }
                        }
                        Spacer(Modifier.width(84.dp + 16.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            ToolbarPill(pillsShown, towardEnd = false, tag = "toolbar_end") {
                                IconButton(onClick = { share(wiktionaryUrl(title)) }, modifier = Modifier.testTag("toolbar_share")) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share")
                                }
                                IconButton(onClick = { openExternal(wiktionaryUrl(title)) }, modifier = Modifier.testTag("toolbar_browser")) {
                                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = "Open on Wiktionary")
                                }
                            }
                        }
                    }
                }
                }
            }

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 112.dp),
            )

            // Settings slide in over everything, springing a little on arrival.
            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                exit = slideOutHorizontally(spring(stiffness = Spring.StiffnessMedium)) { it } + fadeOut(),
            ) {
                SettingsScreen(
                    modifier = Modifier.graphicsLayer {
                        val p = settingsBack
                        val sc = 1f - 0.1f * p
                        scaleX = sc
                        scaleY = sc
                        translationX = p * 32.dp.toPx()
                        shape = RoundedCornerShape(40.dp * p)
                        clip = p > 0f
                    },
                    settings = settings,
                    recentCount = recent.size,
                    onClearHistory = vm::clearHistory,
                    openExternal = openExternal,
                    onBack = { settingsOpen = false },
                )
            }
        }
        }
    }
}

/**
 * Half of the entry toolbar beside the search scallop: semi-transparent, and springing in from
 * (or tucking back into) the scallop's side. [towardEnd] means the scallop is on its right.
 */
@Composable
private fun ToolbarPill(visible: Boolean, towardEnd: Boolean, tag: String, content: @Composable RowScope.() -> Unit) {
    val origin = TransformOrigin(if (towardEnd) 1f else 0f, 0.5f)
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.3f, transformOrigin = origin) +
            slideInHorizontally(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { if (towardEnd) it / 2 else -it / 2 } +
            fadeIn(),
        exit = scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.3f, transformOrigin = origin) +
            slideOutHorizontally(spring(stiffness = Spring.StiffnessMedium)) { if (towardEnd) it / 2 else -it / 2 } +
            fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            // See-through like the scallop, so the page shows behind; no shadow to darken it.
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(tag),
        ) {
            Row(Modifier.height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

private const val SearchMorphKey = "searchMorph"

/** A slightly bouncy spring for the scallop-to-bar morph, so the bar lands with a little give. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val SearchMorphBounds = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)
}
