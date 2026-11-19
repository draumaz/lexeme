package io.github.draumaz.lexeme.ui

import io.github.draumaz.lexeme.data.Block
import io.github.draumaz.lexeme.data.Cell
import io.github.draumaz.lexeme.data.IndentStyle
import io.github.draumaz.lexeme.data.ListStyle
import io.github.draumaz.lexeme.data.RichText
import io.github.draumaz.lexeme.ui.blocks.matchStarts
import io.github.draumaz.lexeme.ui.blocks.richTexts
import org.junit.Assert.assertEquals
import org.junit.Test

class FindOnPageTest {
    @Test
    fun matchesIgnoreCaseAndDontOverlap() {
        assertEquals(listOf(0, 10), matchStarts("Dog-eared dog", "dog"))
        assertEquals(listOf(0, 2), matchStarts("aaaaa", "aa"))
        assertEquals(emptyList<Int>(), matchStarts("cat", ""))
        assertEquals(emptyList<Int>(), matchStarts("a", "abc"))
    }

    @Test
    fun textsComeInReadingOrderThroughNesting() {
        fun p(s: String) = Block.Paragraph(RichText.plain(s))
        val blocks = listOf(
            p("one"),
            Block.ListBlock(false, listOf(listOf(p("two")), listOf(Block.Indented(IndentStyle.Example, listOf(p("three"))))), ListStyle.Senses),
            Block.Table(RichText.plain("four"), listOf(listOf(Cell(listOf(p("five")), header = false))), listOf(p("six")), inflection = false),
            Block.Collapsible(RichText.plain("seven"), listOf(p("eight")), startExpanded = false),
        )
        assertEquals(
            listOf("one", "two", "three", "four", "five", "six", "seven", "eight"),
            blocks.richTexts().map { it.text }.toList(),
        )
    }

    @Test
    fun stepsWrapAroundBothWays() {
        val find = FindState().apply { total = 3 }
        find.previous()
        assertEquals(2, find.current)
        find.next()
        assertEquals(0, find.current)
        find.total = 0
        find.next()
        assertEquals(0, find.current)
    }
}
