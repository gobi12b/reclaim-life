package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacementTest {

    @Test
    fun missingOrUnknownStoredValuesFallBackToDefaults() {
        assertEquals(ReplacementActivity.BREATHING, ReplacementActivity.fromStored(null))
        assertEquals(ReplacementActivity.BREATHING, ReplacementActivity.fromStored("garbage"))
        assertEquals(ReplacementActivity.JOURNALING, ReplacementActivity.fromStored("JOURNALING"))
        assertEquals(FlashcardDeck.CAPITALS, FlashcardDeck.fromStored(null))
        assertEquals(FlashcardDeck.WORDS, FlashcardDeck.fromStored("WORDS"))
    }

    @Test
    fun everyDeckHasFilledInCards() {
        FlashcardDeck.entries.forEach { deck ->
            assertTrue("${deck.name} is empty", deck.cards.isNotEmpty())
            deck.cards.forEach { assertTrue(it.front.isNotBlank() && it.back.isNotBlank()) }
            assertEquals("${deck.name} has duplicate cards", deck.cards.size, deck.cards.map { it.front }.toSet().size)
        }
    }

    @Test
    fun journalPromptsArePresent() {
        assertTrue(JOURNAL_PROMPTS.isNotEmpty())
        assertTrue(JOURNAL_PROMPTS.all { it.isNotBlank() })
    }
}
