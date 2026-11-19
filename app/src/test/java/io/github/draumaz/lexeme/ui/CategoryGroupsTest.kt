package io.github.draumaz.lexeme.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGroupsTest {
    @Test
    fun singleLanguageHasNoHeadings() {
        val groups = groupCategories(listOf("Russian adverbs", "Rhymes:Russian/ot͡ɕ"), listOf("Russian"))
        assertEquals(listOf(CategoryGroup(null, listOf("Russian adverbs", "Rhymes:Russian/ot͡ɕ"))), groups)
    }

    @Test
    fun groupsByLongestLanguageNameInPageOrderWithOtherLast() {
        val groups = groupCategories(
            listOf("Old English nouns", "English nouns", "Pages with entries", "English lemmas", "Old English lemmas"),
            listOf("English", "Old English"),
        )
        assertEquals(
            listOf(
                CategoryGroup("English", listOf("English nouns", "English lemmas")),
                CategoryGroup("Old English", listOf("Old English nouns", "Old English lemmas")),
                CategoryGroup("Other", listOf("Pages with entries")),
            ),
            groups,
        )
    }

    @Test
    fun noCategoriesNoGroups() {
        assertEquals(emptyList<CategoryGroup>(), groupCategories(emptyList(), listOf("English")))
    }
}
