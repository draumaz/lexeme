package io.github.draumaz.lexeme.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitlePartsTest {
    @Test
    fun plainEntriesHaveNoLabel() {
        val parts = titleParts("water")
        assertEquals(TitleParts(null, null, "water"), parts)
        assertNull(parts.label)
    }

    @Test
    fun categoriesDropTheirPrefix() {
        val parts = titleParts("category:English_nouns")
        assertEquals(TitleParts(Namespace.Category, null, "English nouns"), parts)
        assertEquals("Category", parts.label)
    }

    @Test
    fun reconstructionsSplitOutTheLanguageAndStarTheTerm() {
        val parts = titleParts("Reconstruction:Proto-Germanic/watōr")
        assertEquals(TitleParts(Namespace.Reconstruction, "Proto-Germanic", "*watōr"), parts)
        assertEquals("Reconstruction · Proto-Germanic", parts.label)
        assertEquals("*wódr̥", titleParts("Reconstruction:Proto-Indo-European/*wódr̥").name)
    }

    @Test
    fun otherNamespacesKeepTheRestWhole() {
        assertEquals(TitleParts(Namespace.Thesaurus, null, "water"), titleParts("Thesaurus:water"))
        assertEquals(TitleParts(Namespace.Appendix, null, "Proto-Germanic/watōr"), titleParts("Appendix:Proto-Germanic/watōr"))
    }
}
