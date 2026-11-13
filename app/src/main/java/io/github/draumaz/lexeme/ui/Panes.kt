package io.github.draumaz.lexeme.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * A two-pane layout: a [start] pane (the word and its contents, or the home screen's mark), a
 * [gap] (a hinge, on dual-screen devices) and an [end] pane with the reading. Side by side, or
 * [stacked] top and bottom across a horizontal hinge; sizes run along that axis.
 */
@Immutable
data class PaneSplit(val start: Dp, val gap: Dp, val end: Dp, val stacked: Boolean = false) {
    /** Where the end pane begins, from the window's start (or top) edge. */
    val endOffset: Dp get() = start + gap

    /** Places something over the end pane alone. */
    val endPane: Modifier
        get() = if (stacked) Modifier.padding(top = endOffset) else Modifier.padding(start = endOffset)

    /** The status bar's height where it overlaps the end pane: none when that pane is the bottom one. */
    fun endTopInset(statusBar: Dp): Dp = if (stacked) 0.dp else statusBar

    /** The navigation bar's height where it overlaps the start pane: none when that pane is the top one. */
    fun startBottomInset(navigationBar: Dp): Dp = if (stacked) 0.dp else navigationBar
}

/** The current split, or null for the single-column phone layout. */
val LocalPaneSplit = staticCompositionLocalOf<PaneSplit?> { null }

/** Expanded width (Material's window size class): enough for two panes on a plain tablet. */
private val ExpandedWidth = 840.dp

/** Each side of a vertical fold must be at least this wide to hold a pane. */
private val MinPaneWidth = 300.dp

/** Each side of a horizontal fold must be at least this tall to hold a pane. */
private val MinPaneHeight = 280.dp

/** The least room left at a fold, so nothing sits on a crease or behind a hinge. */
private val FoldGutter = 32.dp

/** Shorter than this, two panes are a phone on its side: too little height for either. */
private val MinSplitHeight = 480.dp

/**
 * Decides between one pane and two. A fold or hinge splits the window right at it, so nothing
 * straddles the crease: side by side across a vertical one (a book-style foldable, the Surface
 * Duo's two screens), stacked across a horizontal one (the Duo turned on its side, a foldable
 * half-open on a table). Otherwise a wide enough window gets a fixed start pane and gives the
 * rest to the reading.
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
            val folds = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>().orEmpty()
            val vertical = folds.firstOrNull {
                it.orientation == FoldingFeature.Orientation.VERTICAL &&
                    it.bounds.left.toDp() >= MinPaneWidth && width - it.bounds.right.toDp() >= MinPaneWidth
            }
            // A flat, seamless horizontal fold across a wide screen is better read side by side;
            // a hinge, a half-open fold, or a narrow window (the rotated Duo) stacks at it.
            val horizontal = folds.firstOrNull {
                it.orientation == FoldingFeature.Orientation.HORIZONTAL &&
                    it.bounds.top.toDp() >= MinPaneHeight && height - it.bounds.bottom.toDp() >= MinPaneHeight &&
                    (it.isSeparating || it.state == FoldingFeature.State.HALF_OPENED || width < ExpandedWidth)
            }
            when {
                vertical != null && height >= MinSplitHeight -> {
                    val (start, gap) = gutter(vertical.bounds.left.toDp(), vertical.bounds.right.toDp())
                    PaneSplit(start = start, gap = gap, end = width - start - gap)
                }
                horizontal != null -> {
                    val (start, gap) = gutter(horizontal.bounds.top.toDp(), horizontal.bounds.bottom.toDp())
                    PaneSplit(start = start, gap = gap, end = height - start - gap, stacked = true)
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

/**
 * The start pane's size and the gap for a fold spanning [from] to [to]: a gutter at least
 * FoldGutter across, centred on the fold, since some devices report a hinge as a zero-width
 * line (the Surface Duo's hides about 34dp).
 */
private fun gutter(from: Dp, to: Dp): Pair<Dp, Dp> {
    val gap = maxOf(to - from, FoldGutter)
    return ((from + to) / 2 - gap / 2) to gap
}

/** The two panes of [split], side by side or stacked, each filling the other axis. */
@Composable
fun Panes(
    split: PaneSplit,
    modifier: Modifier = Modifier,
    start: @Composable BoxScope.() -> Unit,
    end: @Composable BoxScope.() -> Unit,
) {
    if (split.stacked) {
        Column(modifier.fillMaxSize()) {
            Box(Modifier.height(split.start).fillMaxWidth(), content = start)
            Spacer(Modifier.height(split.gap))
            Box(Modifier.weight(1f).fillMaxWidth(), content = end)
        }
    } else {
        Row(modifier.fillMaxSize()) {
            Box(Modifier.width(split.start).fillMaxHeight(), content = start)
            Spacer(Modifier.width(split.gap))
            Box(Modifier.weight(1f).fillMaxHeight(), content = end)
        }
    }
}
