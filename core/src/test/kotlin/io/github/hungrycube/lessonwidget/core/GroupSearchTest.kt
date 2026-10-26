package io.github.hungrycube.lessonwidget.core

import io.github.hungrycube.lessonwidget.core.groups.GroupSearch
import io.github.hungrycube.lessonwidget.core.parse.NstuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSearchTest {

    private val groups = NstuParser.parseGroups(Samples.read("groups.html"))

    private fun first(query: String) = GroupSearch.search(query, groups).firstOrNull()?.name

    @Test
    fun differentSpellings() {
        assertEquals("ЭМ-63", first("ЭМ-63"))
        assertEquals("ЭМ-63", first("эм63"))
        assertEquals("ЭМ-63", first("эм 63"))
        assertEquals("ЭМ-63", first("Эм-63 "))
    }

    @Test
    fun wrongKeyboardLayout() {
        assertEquals("ЭМ-63", first("'v63"))
    }

    @Test
    fun latinLookalikes() {
        assertEquals("АА-66", first("aa66"))
        assertEquals("EM-41", first("em41"))
    }

    @Test
    fun prefixSearch() {
        val results = GroupSearch.search("авт-3", groups)
        assertTrue(results.isNotEmpty())
        assertTrue(results.takeWhile { it.name.startsWith("АВТ-3") }.size >= 5)
    }

    @Test
    fun exactFind() {
        assertEquals("ЭМ-63", GroupSearch.find("эм-63", groups)?.name)
        assertNull(GroupSearch.find("ЭМ-6", groups))
    }

    @Test
    fun manualInputAndUrl() {
        assertEquals("ЭМ-63", GroupSearch.normalizeInput("эм63"))
        assertEquals("ЭМ-63", GroupSearch.normalizeInput(" эм-63 "))
        assertEquals("%D0%AD%D0%9C-63", GroupSearch.urlEncode("ЭМ-63"))
        assertEquals("EM-41", GroupSearch.urlEncode("EM-41"))
    }
}

class ShortNamesTest {
    @Test
    fun shortens() {
        assertEquals("Матанализ", io.github.hungrycube.lessonwidget.core.display.ShortNames.shorten("Математический анализ"))
        assertEquals(
            "Теория случайных процессов",
            io.github.hungrycube.lessonwidget.core.display.ShortNames.shorten("Теория случайных процессов (Дисциплина по выбору)"),
        )
        assertEquals("Физика / Матанализ", io.github.hungrycube.lessonwidget.core.display.ShortNames.shorten("Физика / Математический анализ"))
    }
}
