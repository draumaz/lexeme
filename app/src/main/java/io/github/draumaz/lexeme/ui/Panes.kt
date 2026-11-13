package io.github.draumaz.lexeme.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * A side-by-side layout: a [start] pane (the word and its contents, or the home screen's mark),
 * a [gap] (a hinge, on dual-screen devices) and an [end] pane with the reading.
 */
@Immutable
data class PaneSplit(val start: Dp, val gap: Dp, val end: Dp) {
    /** Where the end pane begins, from the window's start edge. */
    val endOffset: Dp get() = start + gap
}

/** The current split, or null for the single-column phone layout. */
val LocalPaneSplit = staticCompositionLocalOf<PaneSplit?> { null }

/** Expanded width (Material's window size class): enough for two panes on a plain tablet. */
private val ExpandedWidth = 840.dp

/** Each side of a fold must be at least this wide to hold a pane. */
private val MinPaneWidth = 300.dp

/** The least room left at a fold, so nothing sits on a crease or behind a hinge. */
private val FoldGutter = 32.dp

/** Shorter than this, two panes are a phone on its side: too little height for either. */
private val MinSplitHeight = 480.dp

/**
 * Decides between one pane and two. A vertical fold or hinge (a book-style foldable, the
 * Surface Duo's two screens) splits the window right at it, so nothing straddles the crease.
 * Otherwise a wide enough window gets a fixed start pane and gives the rest to the reading.
 */
@Composable
fun rememberPaneSplit(): PaneSplit? {
    val activity = LocalActivity.current
    val layoutFlow = remember(activity) {
        activity?.let { WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it) }
    }
    val layoutInfo = layoutFlow?.collectAsStateWithLifecycle(initialValue = null)?.value
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return remember(layoutInfo, size, density) {
        with(density) {
            val width = size.width.toDp()
            val height = size.height.toDp()
            val fold = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull {
                it.orientation == FoldingFeature.Orientation.VERTICAL &&
                    it.bounds.left.toDp() >= MinPaneWidth && width - it.bounds.right.toDp() >= MinPaneWidth
            }
            when {
                fold != null && height >= MinSplitHeight -> {
                    // A gutter at least FoldGutter wide, centred on the fold: some devices report
                    // a hinge as a zero-width line (the Surface Duo's hides about 34dp).
                    val centre = (fold.bounds.left + fold.bounds.right).toDp() / 2
                    val gap = maxOf((fold.bounds.right - fold.bounds.left).toDp(), FoldGutter)
                    val start = centre - gap / 2
                    PaneSplit(start = start, gap = gap, end = width - start - gap)
                }
                width >= ExpandedWidth && height >= MinSplitHeight -> {
                    val start = (width * 0.38f).coerceIn(320.dp, 440.dp)
                    val gap = 8.dp
                    PaneSplit(start = start, gap = gap, end = width - start - gap)
                }
                else -> null
            }
        }
    }
}

/** The two panes of [split] side by side, each filling the height. */
@Composable
fun PaneRow(
    split: PaneSplit,
    modifier: Modifier = Modifier,
    start: @Composable BoxScope.() -> Unit,
    end: @Composable BoxScope.() -> Unit,
) {
    Row(modifier.fillMaxSize()) {
        Box(Modifier.width(split.start).fillMaxHeight(), content = start)
        Spacer(Modifier.width(split.gap))
        Box(Modifier.weight(1f).fillMaxHeight(), content = end)
    }
}
