package io.github.draumaz.lexeme.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.draumaz.lexeme.data.Block
import io.github.draumaz.lexeme.data.PlacedCell
import io.github.draumaz.lexeme.data.TableGrid
import io.github.draumaz.lexeme.ui.components.ShapeBadge

/** Tables beyond this many rows render a prefix first; dialect tables run to 500 rows. */
private const val ROW_PREVIEW = 40

@Composable
fun TableView(table: Block.Table) {
    var showAll by remember(table) { mutableStateOf(false) }
    val rows = if (showAll) table.rows else table.rows.take(ROW_PREVIEW)
    val grid = remember(rows) { TableGrid.of(rows) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("table")) {
        table.caption?.let { caption ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (table.inflection) {
                    ShapeBadge(MaterialShapes.Cookie4Sided, MaterialTheme.colorScheme.primary, size = 28.dp) {
                        Icon(Icons.Rounded.TableChart, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                RichTextView(caption, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            }
        }
        // Tables shrink to fit the screen: columns first, then the whole grid down to MIN_SCALE.
        // Past that they scroll sideways, with faded edges and a track that show there's more.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val available = constraints.maxWidth
            val scroll = rememberScrollState()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .scrollFades(scroll)
                        .clip(RoundedCornerShape(18.dp))
                        .horizontalScroll(scroll)
                        .testTag("table_grid")
                ) {
                    GridLayout(grid, table.inflection, available)
                }
                if (scroll.maxValue > 0) ScrollTrack(scroll, available)
            }
        }
        if (table.rows.size > rows.size) {
            OutlinedButton(onClick = { showAll = true }, shapes = ButtonDefaults.shapes()) {
                Text("Show all ${table.rows.size} rows")
            }
        }
        if (table.notes.isNotEmpty()) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
            ) { BlockList(table.notes, spacing = 4) }
        }
    }
}

/** Smallest the grid is scaled to before it scrolls instead; below this text gets hard to read. */
private const val MIN_SCALE = 0.72f

@Composable
private fun GridLayout(grid: TableGrid, inflection: Boolean, available: Int) {
    Layout(content = {
        grid.cells.forEach { CellBox(it, grid, inflection) }
    }) { measurables, _ ->
        val gap = 3.dp.roundToPx()
        val maxCell = 260.dp.roundToPx()
        val minCell = 36.dp.roundToPx()
        val n = grid.columns
        val gaps = gap * (n - 1).coerceAtLeast(0)
        val preferred = IntArray(measurables.size) {
            measurables[it].maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(maxCell * grid.cells[it].colspan)
        }
        // A column can't go narrower than its longest unbreakable word.
        val narrowest = IntArray(measurables.size) {
            measurables[it].minIntrinsicWidth(Constraints.Infinity).coerceAtMost(preferred[it])
        }
        val pref = IntArray(n)
        val min = IntArray(n)
        grid.cells.forEachIndexed { i, c ->
            if (c.colspan == 1) {
                pref[c.col] = maxOf(pref[c.col], preferred[i].coerceIn(minCell, maxCell))
                min[c.col] = maxOf(min[c.col], narrowest[i].coerceIn(minCell, maxCell))
            }
        }
        for (k in 0 until n) {
            if (pref[k] == 0) pref[k] = minCell
            if (min[k] == 0) min[k] = minCell
            min[k] = min[k].coerceAtMost(pref[k])
        }
        val prefTotal = pref.sum() + gaps
        val minTotal = min.sum() + gaps
        val limit = if (available in 1 until Constraints.Infinity) available else Int.MAX_VALUE
        // Scale only when even the narrowest columns overflow, and never below MIN_SCALE.
        val scale = if (minTotal <= limit) 1f else maxOf(limit.toFloat() / minTotal, MIN_SCALE)
        val target = if (limit == Int.MAX_VALUE) Int.MAX_VALUE else (limit / scale).toInt()
        val cols = when {
            prefTotal <= target -> pref
            minTotal >= target -> min
            else -> {
                // Give each column its minimum, then share what's left by how much more it wanted.
                val spare = (target - minTotal).toFloat()
                val want = (prefTotal - minTotal).toFloat()
                IntArray(n) { min[it] + ((pref[it] - min[it]) * spare / want).toInt() }
            }
        }
        // Spanning cells widen their columns only if they don't fit what the others need.
        grid.cells.forEachIndexed { i, c ->
            if (c.colspan > 1) {
                val range = c.col until minOf(c.col + c.colspan, n)
                val have = range.sumOf { cols[it] } + gap * (range.count() - 1)
                val need = if (minTotal >= target) narrowest[i] else minOf(preferred[i], maxOf(narrowest[i], have))
                val extra = need - have
                if (extra > 0) range.forEach { cols[it] += extra / range.count() + 1 }
            }
        }
        val x = IntArray(n + 1)
        for (k in 0 until n) x[k + 1] = x[k] + cols[k] + gap
        fun width(c: PlacedCell) = x[minOf(c.col + c.colspan, n)] - x[c.col] - gap

        val rows = IntArray(grid.rows)
        val heights = IntArray(measurables.size) { measurables[it].minIntrinsicHeight(width(grid.cells[it])) }
        grid.cells.forEachIndexed { i, c -> if (c.rowspan == 1) rows[c.row] = maxOf(rows[c.row], heights[i]) }
        grid.cells.forEachIndexed { i, c ->
            if (c.rowspan > 1) {
                val range = c.row until minOf(c.row + c.rowspan, grid.rows)
                val have = range.sumOf { rows[it] } + gap * (range.count() - 1)
                if (heights[i] > have) rows[range.last] += heights[i] - have
            }
        }
        val y = IntArray(grid.rows + 1)
        for (r in 0 until grid.rows) y[r + 1] = y[r] + rows[r] + gap
        fun height(c: PlacedCell) = y[minOf(c.row + c.rowspan, grid.rows)] - y[c.row] - gap

        val placeables = measurables.mapIndexed { i, m ->
            val c = grid.cells[i]
            m.measure(Constraints.fixed(width(c).coerceAtLeast(0), height(c).coerceAtLeast(0)))
        }
        val w = (x[n] - gap).coerceAtLeast(0)
        val h = (y[grid.rows] - gap).coerceAtLeast(0)
        layout(ceil(w * scale).toInt(), ceil(h * scale).toInt()) {
            placeables.forEachIndexed { i, p ->
                val c = grid.cells[i]
                if (scale == 1f) {
                    p.place(x[c.col], y[c.row])
                } else {
                    p.placeWithLayer((x[c.col] * scale).roundToInt(), (y[c.row] * scale).roundToInt()) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
        }
    }
}

/** Fades whichever edge still has table beyond it. */
private fun Modifier.scrollFades(scroll: ScrollState): Modifier = composed {
    val start by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f, label = "fadeStart")
    val end by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, label = "fadeEnd")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fade = with(LocalDensity.current) { 40.dp.toPx() }
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val left = if (rtl) end else start
            val right = if (rtl) start else end
            if (left > 0f) drawRect(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = left), Color.Transparent), startX = 0f, endX = fade),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstOut,
            )
            if (right > 0f) drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = right)), startX = size.width - fade, endX = size.width),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstOut,
            )
        }
}

/** A track under scrolling tables: chevrons at the ends that have more, and a thumb for where you are. */
@Composable
private fun ScrollTrack(scroll: ScrollState, viewport: Int) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val startAlpha by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0.3f, label = "chevStart")
    val endAlpha by animateFloatAsState(if (scroll.canScrollForward) 1f else 0.3f, label = "chevEnd")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("table_scroll_track")) {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = "Scroll table back",
            tint = colors.primary,
            modifier = Modifier
                .size(28.dp)
                .alpha(startAlpha)
                .clip(CircleShape)
                .clickable(enabled = scroll.canScrollBackward) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch { scroll.animateScrollTo((scroll.value - viewport * 3 / 4).coerceAtLeast(0)) }
                }
                .padding(2.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
                .drawWithContent {
                    val content = viewport + scroll.maxValue
                    val thumb = (size.width * viewport / content).coerceAtLeast(12.dp.toPx())
                    val travel = size.width - thumb
                    val fraction = if (scroll.maxValue == 0) 0f else scroll.value.toFloat() / scroll.maxValue
                    val offset = if (layoutDirection == LayoutDirection.Rtl) travel * (1 - fraction) else travel * fraction
                    drawRoundRect(
                        colors.primary,
                        topLeft = Offset(offset, 0f),
                        size = Size(thumb, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                },
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Scroll table forward",
            tint = colors.primary,
            modifier = Modifier
                .size(28.dp)
                .alpha(endAlpha)
                .clip(CircleShape)
                .clickable(enabled = scroll.canScrollForward) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch { scroll.animateScrollTo((scroll.value + viewport * 3 / 4).coerceAtMost(scroll.maxValue)) }
                }
                .padding(2.dp),
        )
    }
}

@Composable
private fun CellBox(placed: PlacedCell, grid: TableGrid, inflection: Boolean) {
    val colors = MaterialTheme.colorScheme
    val cell = placed.cell
    val empty = cell.blocks.isEmpty()
    val background = when {
        cell.header && empty -> Color.Transparent
        cell.header && inflection -> colors.secondaryContainer
        cell.header -> colors.surfaceContainerHighest
        cell.secondary -> colors.surfaceContainer
        else -> colors.surfaceBright
    }
    val content = when {
        cell.header && inflection -> colors.onSecondaryContainer
        cell.secondary -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    // The grid's own outer corners are generous; everything inside stays tight.
    val outer = 14.dp
    val inner = 5.dp
    val top = placed.row == 0
    val bottom = placed.row + placed.rowspan >= grid.rows
    val start = placed.col == 0
    val end = placed.col + placed.colspan >= grid.columns
    val shape = RoundedCornerShape(
        topStart = if (top && start) outer else inner,
        topEnd = if (top && end) outer else inner,
        bottomStart = if (bottom && start) outer else inner,
        bottomEnd = if (bottom && end) outer else inner,
    )
    Box(
        Modifier
            .background(background, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = if (cell.header) Alignment.Center else Alignment.CenterStart,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides if (cell.header) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            LocalContentColor provides content,
        ) {
            BlockList(cell.blocks, spacing = 4)
        }
    }
}
