package com.isycat.compose.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzySearchTest {

    @Test
    fun `non-subsequence returns null`() {
        assertNull(FuzzySearch.score("xyz", "charizard"))
    }

    @Test
    fun `subsequence matches`() {
        assertTrue(FuzzySearch.score("czrd", "charizard") != null)
    }

    @Test
    fun `blank query matches everything`() {
        assertEquals(0, FuzzySearch.score("", "anything"))
    }

    @Test
    fun `prefix scores higher than mid-string match`() {
        val prefix = FuzzySearch.score("char", "charizard")!!
        val mid = FuzzySearch.score("char", "supercharger")!!
        assertTrue(prefix > mid, "prefix=$prefix should beat mid=$mid")
    }

    @Test
    fun `contiguous scores higher than scattered`() {
        val contiguous = FuzzySearch.score("pika", "pikachu")!!
        val scattered = FuzzySearch.score("pika", "p i k a chu")!!
        assertTrue(contiguous > scattered, "contiguous=$contiguous should beat scattered=$scattered")
    }

    @Test
    fun `search ranks best match first`() {
        val items = listOf("Pidgey", "Pikachu", "Spinarak", "Charizard")
        val results = FuzzySearch.search("pika", items) { it }
        assertEquals("Pikachu", results.first().item)
    }

    @Test
    fun `search drops non-matches`() {
        val items = listOf("Bulbasaur", "Charmander", "Squirtle")
        val results = FuzzySearch.search("char", items) { it }
        assertEquals(listOf("Charmander"), results.map { it.item })
    }

    @Test
    fun `blank query sorts alphabetically`() {
        val items = listOf("Zubat", "Abra", "Mew")
        val results = FuzzySearch.search("", items) { it }
        assertEquals(listOf("Abra", "Mew", "Zubat"), results.map { it.item })
    }

    @Test
    fun `searchMulti uses best key`() {
        data class Mon(val name: String, val alias: String)
        val items = listOf(Mon("Nidoran", "poison-pin"), Mon("Jigglypuff", "balloon"))
        val results = FuzzySearch.searchMulti("balloon", items) { listOf(it.name, it.alias) }
        assertEquals("Jigglypuff", results.first().item.name)
    }
}
