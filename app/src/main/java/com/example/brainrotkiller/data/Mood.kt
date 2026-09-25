package com.example.brainrotkiller.data

/**
 * The sprout mascot's mood, driven by how far through today's limit someone is.
 * Used by the live overlay counter, home screen, and gate/block screens so the character
 * is consistent everywhere it shows up.
 */
enum class Mood(val emoji: String, val label: String) {
    ENERGIZED("🌱", "Fresh start"),      // 🌱
    GOOD("🙂", "Doing fine"),             // 🙂
    UNEASY("😕", "Getting close"),        // 😕
    TIRED("😩", "Running on empty"),      // 😩
    DONE("😴", "Done for today");         // 😴

    companion object {
        fun forProgress(count: Int, limit: Int): Mood {
            if (limit <= 0) return GOOD
            val ratio = count.toFloat() / limit.toFloat()
            return when {
                ratio >= 1f -> DONE
                // Negative moods are held back until they mean something: at 55% with half the
                // budget left, "getting close" read as a scolding rather than a heads-up.
                ratio >= 0.9f -> TIRED
                ratio >= 0.7f -> UNEASY
                ratio >= 0.2f -> GOOD
                else -> ENERGIZED
            }
        }
    }
}
