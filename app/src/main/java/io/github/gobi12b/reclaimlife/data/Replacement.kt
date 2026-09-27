package io.github.gobi12b.reclaimlife.data

/**
 * Habit replacement, not just suppression: reaching a limit leads into a short, good-for-you
 * alternative the user picked during onboarding, instead of a bare stop sign.
 */
const val SWAP_SECONDS = 120

enum class ReplacementActivity(val label: String, val emoji: String, val blurb: String) {
    BREATHING("Breathe", "🌬️", "A slow, guided breathing exercise."),
    FLASHCARDS("Flashcards", "🃏", "Flip through a quick deck and learn something."),
    JOURNALING("Journal", "✍️", "Answer one small prompt, just for you.");

    companion object {
        fun fromStored(value: String?): ReplacementActivity =
            entries.firstOrNull { it.name == value } ?: BREATHING
    }
}

data class Flashcard(val front: String, val back: String)

enum class FlashcardDeck(val label: String, val cards: List<Flashcard>) {
    CAPITALS(
        "World capitals",
        listOf(
            "France" to "Paris", "Japan" to "Tokyo", "Canada" to "Ottawa", "Australia" to "Canberra",
            "Brazil" to "Brasília", "Kenya" to "Nairobi", "Egypt" to "Cairo", "Argentina" to "Buenos Aires",
            "South Korea" to "Seoul", "Norway" to "Oslo", "Turkey" to "Ankara", "New Zealand" to "Wellington",
            "Peru" to "Lima", "Vietnam" to "Hanoi", "Morocco" to "Rabat", "Switzerland" to "Bern",
            "Nigeria" to "Abuja", "Portugal" to "Lisbon", "Thailand" to "Bangkok", "Chile" to "Santiago"
        ).map { (country, capital) -> Flashcard("What's the capital of $country?", capital) }
    ),
    WORDS(
        "Words worth knowing",
        listOf(
            "Serendipity" to "Finding something good without looking for it.",
            "Ephemeral" to "Lasting a very short time.",
            "Ubiquitous" to "Found everywhere.",
            "Resilient" to "Able to recover quickly from difficulty.",
            "Eloquent" to "Fluent and persuasive in speaking or writing.",
            "Candid" to "Truthful and straightforward.",
            "Meticulous" to "Very careful and precise.",
            "Pragmatic" to "Dealing with things sensibly and realistically.",
            "Tenacious" to "Holding on firmly; persistent.",
            "Benevolent" to "Well-meaning and kind.",
            "Nostalgia" to "A fond longing for the past.",
            "Equanimity" to "Calmness, especially when things are hard.",
            "Gregarious" to "Fond of company; sociable.",
            "Lucid" to "Clear and easy to understand.",
            "Frugal" to "Careful with money or resources.",
            "Innate" to "Inborn; natural.",
            "Mundane" to "Ordinary; everyday.",
            "Zeal" to "Great energy or enthusiasm.",
            "Ambivalent" to "Having mixed feelings about something.",
            "Savor" to "To enjoy something slowly and fully."
        ).map { (word, meaning) -> Flashcard(word, meaning) }
    );

    companion object {
        fun fromStored(value: String?): FlashcardDeck = entries.firstOrNull { it.name == value } ?: CAPITALS
    }
}

val JOURNAL_PROMPTS = listOf(
    "What's one small thing that went well today?",
    "Who's someone you'd like to catch up with this week?",
    "What are you looking forward to tomorrow?",
    "What would make the next hour feel good?",
    "Name three things you can see right now that you like.",
    "What's something you learned recently?",
    "What's one thing you'd like to do this weekend?",
    "What made you smile today?",
    "What's a small thing you could do for yourself tonight?",
    "If today had a title, what would it be?",
    "What's something you're grateful for right now?",
    "What's one thing you've been meaning to start?"
)
