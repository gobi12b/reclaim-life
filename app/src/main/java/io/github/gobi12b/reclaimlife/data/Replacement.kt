package io.github.gobi12b.reclaimlife.data

/**
 * Habit replacement, not just suppression: reaching a limit leads into a short, good-for-you
 * alternative the user picked during onboarding, instead of a bare stop sign.
 */
const val SWAP_SECONDS = 120

enum class ReplacementActivity(val label: String, val emoji: String, val blurb: String) {
    BREATHING("Breathe", "🌬️", "Slow, guided breaths."),
    FLASHCARDS("Flashcards", "🃏", "Learn something quick."),
    JOURNALING("Journal", "✍️", "Answer one small prompt."),
    READING("Read", "📖", "A short poem, quote or idea.");

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

enum class ReadingKind(val label: String) { POEM("Poem"), QUOTE("Quote"), IDEA("Idea") }

/** One short read. Poems and quotes are public domain; ideas are ours, with an optional link to go deeper. */
data class Reading(val kind: ReadingKind, val text: String, val source: String? = null, val url: String? = null)

val READINGS = listOf(
    Reading(
        ReadingKind.POEM,
        "Nature's first green is gold,\nHer hardest hue to hold.\nHer early leaf's a flower;\nBut only so an hour.\n" +
            "Then leaf subsides to leaf.\nSo Eden sank to grief,\nSo dawn goes down to day.\nNothing gold can stay.",
        "Robert Frost, Nothing Gold Can Stay"
    ),
    Reading(
        ReadingKind.POEM,
        "\"Hope\" is the thing with feathers -\nThat perches in the soul -\nAnd sings the tune without the words -\nAnd never stops - at all -",
        "Emily Dickinson"
    ),
    Reading(
        ReadingKind.POEM,
        "To see a World in a Grain of Sand\nAnd a Heaven in a Wild Flower,\nHold Infinity in the palm of your hand\nAnd Eternity in an hour.",
        "William Blake, Auguries of Innocence"
    ),
    Reading(ReadingKind.QUOTE, "It is not that we have a short time to live, but that we waste a lot of it.", "Seneca, On the Shortness of Life"),
    Reading(ReadingKind.QUOTE, "Our life is frittered away by detail. Simplify, simplify.", "Henry David Thoreau, Walden"),
    Reading(ReadingKind.QUOTE, "The happiness of your life depends upon the quality of your thoughts.", "Marcus Aurelius, Meditations"),
    Reading(ReadingKind.QUOTE, "A journey of a thousand miles begins with a single step.", "Lao Tzu, Tao Te Ching"),
    Reading(ReadingKind.QUOTE, "The best time to plant a tree was twenty years ago. The second best time is now.", "Proverb"),
    Reading(
        ReadingKind.IDEA,
        "Every habit has a cue, a routine and a reward. You don't have to fight the cue. Swap the routine and keep the reward.",
        "Atomic Habits, summarised",
        "https://jamesclear.com/atomic-habits-summary"
    ),
    Reading(
        ReadingKind.IDEA,
        "Boredom isn't a problem to fix. A wandering mind is often where your best ideas start.",
        "Manoush Zomorodi, TED",
        "https://www.ted.com/talks/manoush_zomorodi_how_boredom_can_lead_to_your_most_brilliant_ideas"
    ),
    Reading(
        ReadingKind.IDEA,
        "Feeds have no bottom on purpose. Stopping is a decision the app won't make for you, and it gets easier with practice.",
        "Adam Alter, TED",
        "https://www.ted.com/talks/adam_alter_why_our_screens_make_us_less_happy"
    ),
    Reading(
        ReadingKind.IDEA,
        "Small choices compound. One better decision a day doesn't feel like much, until you look back after a month.",
    ),
    Reading(
        ReadingKind.IDEA,
        "Where your attention goes, your life goes. Spending it on purpose is the whole game.",
    ),
    Reading(
        ReadingKind.IDEA,
        "Tech works best as a tool you pick up, not a place you live. Turning off one notification is a real start.",
        "Center for Humane Technology",
        "https://www.humanetech.com/take-control"
    )
)
