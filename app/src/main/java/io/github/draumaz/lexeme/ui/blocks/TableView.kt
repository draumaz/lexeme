package io.github.draumaz.lexeme.ui.blocks

import androidx.compose.foundation.background
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
        // Wide tables scroll sideways inside the card rather than squashing their columns.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .horizontalScroll(rememberScrollState())
        ) {
            GridLayout(grid, table.inflection)
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

@Composable
private fun GridLayout(grid: TableGrid, inflection: Boolean) {
    Layout(content = {
        grid.cells.forEach { CellBox(it, grid, inflection) }
    }) { measurables, _ ->
        val gap = 3.dp.roundToPx()
        val maxCell = 260.dp.roundToPx()
        val minCell = 36.dp.roundToPx()
        val n = grid.columns
        val cols = IntArray(n)
        val preferred = IntArray(measurables.size) {
            measurables[it].maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(maxCell * grid.cells[it].colspan)
        }
        grid.cells.forEachIndexed { i, c ->
            if (c.colspan == 1) cols[c.col] = maxOf(cols[c.col], preferred[i].coerceIn(minCell, maxCell))
        }
        for (k in 0 until n) if (cols[k] == 0) cols[k] = minCell
        // Spanning cells widen their columns only if they don't fit what the others need.
        grid.cells.forEachIndexed { i, c ->
            if (c.colspan > 1) {
                val range = c.col until minOf(c.col + c.colspan, n)
                val have = range.sumOf { cols[it] } + gap * (range.count() - 1)
                val extra = preferred[i] - have
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
        layout((x[n] - gap).coerceAtLeast(0), (y[grid.rows] - gap).coerceAtLeast(0)) {
            placeables.forEachIndexed { i, p -> p.place(x[grid.cells[i].col], y[grid.cells[i].row]) }
        }
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
