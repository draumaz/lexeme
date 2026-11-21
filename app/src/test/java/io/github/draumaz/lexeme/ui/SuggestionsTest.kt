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

    @Test
    fun bareNamePicksTheNamespacedPage() {
        val items = listOf("English round hand", "Appendix:English nouns", "Category:English nouns")
        assertEquals("Appendix:English nouns", Suggestions("English nouns", items).pickFor("English nouns"))
        val reconstruction = listOf("protogermanica", "Reconstruction:Proto-Germanic/watōr")
        assertEquals(
            "Reconstruction:Proto-Germanic/watōr",
            Suggestions("Proto-Germanic/watōr", reconstruction).pickFor("Proto-Germanic/watōr"),
        )
    }

    @Test
    fun realPrefixMatchesLeadLooseOnes() {
        assertEquals(
            listOf("Reconstruction:Proto-Germanic/watōr", "protogermanica", "proto-germânica"),
            prefixMatchesFirst("Proto-Germanic/wat", listOf("protogermanica", "proto-germânica", "Reconstruction:Proto-Germanic/watōr")),
        )
        assertEquals(listOf("cat", "catch", "Catalan"), prefixMatchesFirst("cat", listOf("cat", "catch", "Catalan")))
    }
}
