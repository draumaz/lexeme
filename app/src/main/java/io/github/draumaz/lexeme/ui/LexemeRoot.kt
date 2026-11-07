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
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LexemeRoot(
    vm: LexemeViewModel,
    openExternal: (String) -> Unit,
    share: (String) -> Unit,
) {
    val pages by vm.pages.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val randomBusy by vm.randomBusy.collectAsStateWithLifecycle()

    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
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

    // The toolbar tucks into its FAB while reading down, and comes back on any upward scroll.
    var toolbarExpanded by remember { mutableStateOf(true) }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) toolbarExpanded = false else if (available.y > 8f) toolbarExpanded = true
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
        LocalAudio provides audio,
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
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
                            contentPadding = contentPadding,
                            onOpen = ::submit,
                            onRandom = vm::random,
                            onClearHistory = vm::clearHistory,
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

            SearchBar(
                query = query,
                onQueryChange = { vm.query.value = it },
                onSubmit = ::submit,
                focused = focused,
                onFocusChange = { focused = it },
                focusRequester = focusRequester,
                canGoBack = pages.isNotEmpty(),
                onBack = {
                    if (focused) focusManager.clearFocus() else vm.back()
                },
                suggestions = suggestions,
                recent = recent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .fillMaxWidth(),
            )

            AnimatedVisibility(
                visible = current != null && !focused,
                enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) { it * 2 } + fadeIn(),
                exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it * 2 } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                val title = (current?.state as? PageState.Loaded)?.entry?.title ?: current?.title.orEmpty()
                HorizontalFloatingToolbar(
                    expanded = toolbarExpanded,
                    floatingActionButton = {
                        FloatingToolbarDefaults.VibrantFloatingActionButton(
                            onClick = { focusRequester.requestFocus() },
                            modifier = Modifier.testTag("toolbar_search"),
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    },
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    modifier = Modifier.testTag("toolbar"),
                ) {
                    IconButton(onClick = { vm.home() }, modifier = Modifier.testTag("toolbar_home")) {
                        Icon(Icons.Rounded.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = vm::random, modifier = Modifier.testTag("toolbar_random")) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Random word")
                    }
                    IconButton(
                        onClick = {
                            val id = current?.id ?: return@IconButton
                            scope.launch { listStates[id]?.animateScrollToItem(0) }
                        },
                        modifier = Modifier.testTag("toolbar_top"),
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Back to top")
                    }
                    IconButton(onClick = { share(wiktionaryUrl(title)) }, modifier = Modifier.testTag("toolbar_share")) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { openExternal(wiktionaryUrl(title)) }, modifier = Modifier.testTag("toolbar_browser")) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = "Open on Wiktionary")
                    }
                }
            }

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp),
            )
        }
    }
}
