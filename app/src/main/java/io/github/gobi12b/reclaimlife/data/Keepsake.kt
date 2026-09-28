package io.github.gobi12b.reclaimlife.data

/**
 * Milestones added to the tree. The watering can comes with planting it (onboarding done, limit
 * set); the rest by reaching [streak] days in a row under the limit. Kept for good: a broken
 * streak never takes one away. They're a surprise, so nothing names the next one ahead of time.
 */
enum class Keepsake(val streak: Int, val label: String, val arrival: String) {
    WATERING_CAN(0, "Watering can", "A watering can"),
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

/** The keepsake a streak of exactly [streak] brings, if any. The watering can isn't a streak. */
fun keepsakeAt(streak: Int): Keepsake? = Keepsake.entries.firstOrNull { it.streak == streak && it.streak > 0 }

/** The reveal line for [k], the first time it shows. */
fun keepsakeRevealLine(k: Keepsake, treeName: String): String = when (k) {
    Keepsake.WATERING_CAN -> "A watering can for ${treeNameInline(treeName)}. Thanks for planting it."
    else -> "${k.streak} days in a row! ${k.arrival} joined ${treeNameInline(treeName)}."
}
