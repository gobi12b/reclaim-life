package io.github.gobi12b.reclaimlife.data

/**
 * Milestones a streak adds to the tree. Earned once by reaching [streak] days in a row
 * under the limit, then kept for good: a broken streak never takes one away.
 */
enum class Keepsake(val streak: Int, val label: String, val arrival: String) {
    LADYBUG(3, "Ladybug", "A ladybug"),
    BUTTERFLY(7, "Butterfly", "A butterfly"),
    NEST(14, "Nest", "A bird's nest"),
    SONGBIRD(30, "Songbird", "A songbird"),
    FIREFLIES(60, "Fireflies", "Fireflies"),
    LANTERN(90, "Lantern", "A lantern")
}

/** Every keepsake earned by a best streak of [bestStreak]. */
fun keepsakesFor(bestStreak: Int): List<Keepsake> = Keepsake.entries.filter { bestStreak >= it.streak }

/** The next one to earn, or null once all are in. */
fun nextKeepsake(bestStreak: Int): Keepsake? = Keepsake.entries.firstOrNull { bestStreak < it.streak }

/** The keepsake a streak of exactly [streak] brings, if any. */
fun keepsakeAt(streak: Int): Keepsake? = Keepsake.entries.firstOrNull { it.streak == streak }
