package io.github.draumaz.lexeme.data

/** A [Cell] pinned to its grid position once rowspans and colspans are resolved. */
data class PlacedCell(val row: Int, val col: Int, val rowspan: Int, val colspan: Int, val cell: Cell)

data class TableGrid(val rows: Int, val columns: Int, val cells: List<PlacedCell>) {
    companion object {
        /**
         * The HTML table algorithm, minus the edge cases nobody uses: each cell takes the next
         * free column in its row, skipping slots still covered by a rowspan from above.
         */
        fun of(rows: List<List<Cell>>): TableGrid {
            val occupied = HashSet<Long>()
            fun key(r: Int, c: Int) = (r.toLong() shl 32) or c.toLong()
            val placed = ArrayList<PlacedCell>()
            var columns = 0
            rows.forEachIndexed { r, cells ->
                var c = 0
                for (cell in cells) {
                    while (key(r, c) in occupied) c++
                    // Clamp rowspans that run past the table end.
                    val rowspan = minOf(cell.rowspan, rows.size - r)
                    for (dr in 0 until rowspan) for (dc in 0 until cell.colspan) occupied += key(r + dr, c + dc)
                    placed += PlacedCell(r, c, rowspan, cell.colspan, cell)
                    c += cell.colspan
                    columns = maxOf(columns, c)
                }
            }
            return TableGrid(rows.size, columns, placed)
        }
    }
}
