package io.github.draumaz.lexeme.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionsTest {
    private val s = Suggestions("Hun", listOf("Hun", "hun", "hundred", "Hund"))

    @Test
    fun exactMatchWinsOverTopSuggestion() {
        assertEquals("Hund", Suggestions("Hund", listOf("hund", "Hund", "Hunde")).pickFor("Hund"))
    }

    @Test
    fun fallsBackToTopSuggestion() {
        assertEquals("serendipity", Suggestions("serendip", listOf("serendipity", "serendipitous")).pickFor("serendip "))
    }

    @Test
    fun staleOrEmptyPicksNothing() {
        assertNull(s.pickFor("Hund")) // results are for "Hun", not what's typed now
        assertNull(Suggestions("qzx", emptyList()).pickFor("qzx"))
        assertNull(s.pickFor("  "))
    }
}
