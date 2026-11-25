package com.isycat.compose.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The offer to open an app's files on a double-click: asked where the system says they don't open in it, taken on yes,
 * "Don't ask again" kept on either answer, and a failure said in the bar. A stand-in system answers, so no test changes
 * the registry.
 */
class FileTypesOfferTest {
    private class StandIn(var opens: Boolean = false, val fails: Boolean = false) : FileOpeners {
        val taken = mutableListOf<List<String>>()

        override fun opensThem(extensions: List<String>) = opens

        override fun takeThem(extensions: List<String>) {
            if (fails) error("access denied")
            taken += extensions
            opens = true
        }
    }

    private var kept: Boolean = true

    private fun offer(system: FileOpeners?, extensions: List<String> = listOf("cshproj", ".CSCARD", "cscard", "ptcgcard")) =
        FileTypesOffer("cardSTUDIO", { extensions }, system, kept, { kept = it }, Dispatchers.Unconfined)

    @Test
    fun asksWhereTheFilesDontOpenInItAndTakesThemOnYes() = runBlocking {
        val system = StandIn()
        val offer = offer(system)
        offer.look()
        assertTrue(offer.asking)
        assertEquals(listOf("cshproj", "cscard", "ptcgcard"), offer.asked, "without dots, lower case, each once")
        assertEquals("Make cardSTUDIO open .cshproj, .cscard and .ptcgcard files when you double-click them?", offer.question)
        assertEquals("Open them with cardSTUDIO", offer.answer)
        offer.accept()
        assertEquals(listOf(listOf("cshproj", "cscard", "ptcgcard")), system.taken)
        assertFalse(offer.asking)
        assertTrue(kept, "yes without don't ask again leaves the offer on, for an exe that moves")
        offer.look()
        assertFalse(offer.asking, "answered this start")
        offer(system).let {
            it.look()
            assertFalse(it.asking, "next start: it opens them now")
        }
    }

    @Test
    fun looksAgainWhenPluginsAddTypesButNotOnceAnswered() = runBlocking {
        var extensions = listOf("cshproj")
        val offer = FileTypesOffer("cardSTUDIO", { extensions }, StandIn(), away = Dispatchers.Unconfined)
        offer.look()
        assertEquals(listOf("cshproj"), offer.asked)
        extensions = listOf("cshproj", "ptcgcard")
        offer.look()
        assertEquals(listOf("cshproj", "ptcgcard"), offer.asked)
        assertTrue(offer.asking)
        offer.decline()
        extensions = listOf("cshproj", "ptcgcard", "mtgcard")
        offer.look()
        assertFalse(offer.asking, "Not now holds until the next start")
    }

    @Test
    fun doesNotAskWhereItOpensThemAlreadyOrCannotAskOrHasNoFiles() = runBlocking {
        offer(StandIn(opens = true)).let {
            it.look()
            assertFalse(it.asking)
        }
        offer(null).let {
            it.look()
            assertFalse(it.asking)
            assertFalse(it.possible, "Settings shows no choice where there is none")
        }
        offer(StandIn(), emptyList()).let {
            it.look()
            assertFalse(it.asking)
        }
    }

    @Test
    fun dontAskAgainIsKeptOnEitherAnswerAndSettingsTurnsItBackOn() = runBlocking {
        for (yes in listOf(false, true)) {
            kept = true
            val offer = offer(StandIn())
            offer.look()
            offer.dontAskAgain = true
            if (yes) offer.accept() else offer.decline()
            assertFalse(offer.asking)
            assertFalse(kept, "kept for the next start (yes: $yes)")
            val next = offer(StandIn())
            assertFalse(next.offering)
            next.look()
            assertFalse(next.asking)
            next.offering(true)
            assertFalse(next.dontAskAgain, "turned back on, the box is not still ticked")
            assertTrue(kept)
            next.look()
            assertTrue(next.asking)
        }
    }

    @Test
    fun notNowAsksAgainNextStart() = runBlocking {
        offer(StandIn()).let {
            it.look()
            it.decline()
            assertFalse(it.asking)
        }
        offer(StandIn()).let {
            it.look()
            assertTrue(it.asking)
        }
    }

    @Test
    fun aFailureSaysWhyAndChangesNoPreference() = runBlocking {
        val offer = offer(StandIn(fails = true))
        offer.look()
        offer.dontAskAgain = true
        offer.accept()
        assertTrue(offer.asking, "the bar stays to say so")
        assertEquals("Could not make cardSTUDIO open its files: access denied", offer.failure)
        assertTrue(kept)
        offer.dontAskAgain = false
        offer.decline()
        assertEquals(null, offer.failure)
    }

    @Test
    fun olderExtensionsAreTakenButNotNamed() = runBlocking {
        val system = StandIn()
        val offer = FileTypesOffer("cardSTUDIO", { listOf("cshproj", "chproj") }, system, away = Dispatchers.Unconfined, unnamed = setOf("chproj"))
        offer.look()
        assertEquals("Make cardSTUDIO open .cshproj files when you double-click them?", offer.question)
        offer.accept()
        assertEquals(listOf(listOf("cshproj", "chproj")), system.taken)
    }

    @Test
    fun aSentenceNamesTheExtensions() {
        assertEquals(".a", FileTypesOffer.sentence(listOf("a")))
        assertEquals(".a and .b", FileTypesOffer.sentence(listOf("a", "b")))
        assertEquals(".a, .b and .c", FileTypesOffer.sentence(listOf("a", "b", "c")))
    }
}
