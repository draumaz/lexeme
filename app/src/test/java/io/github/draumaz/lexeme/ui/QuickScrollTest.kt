package io.github.draumaz.lexeme.ui

import io.github.draumaz.lexeme.ui.components.alphabetFor
import io.github.draumaz.lexeme.ui.components.letterIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickScrollTest {
    @Test
    fun picksTheAlphabetTheTitlesAreIn() {
        assertEquals("A", alphabetFor(listOf("'m", "aardvark", "Ärger", "cat"))?.first())
        assertEquals("А", alphabetFor(listOf("мама", "ёж", "дом"))?.first())
        assertEquals("Α", alphabetFor(listOf("μάγαδις", "λόγος"))?.first())
        assertNull(alphabetFor(listOf("水", "猫", "犬")))
    }

    @Test
    fun filesTitlesUnderTheirBareFirstLetter() {
        val latin = alphabetFor(listOf("a"))!!
        assertEquals(12, letterIndex("'m", latin))
        assertEquals(0, letterIndex("Ärger", latin))
        assertEquals(-1, letterIndex("123", latin))
        assertEquals(-1, letterIndex("1% milk", latin))
        assertEquals(-1, letterIndex("\$h!t", latin))
        assertEquals(latin.indexOf("W"), letterIndex("*watōr", latin))
        val cyrillic = alphabetFor(listOf("дом"))!!
        assertEquals(cyrillic.indexOf("Е"), letterIndex("ёж", cyrillic))
        val greek = alphabetFor(listOf("λόγος"))!!
        assertEquals(greek.indexOf("Ω"), letterIndex("ὤμος", greek))
    }
}
