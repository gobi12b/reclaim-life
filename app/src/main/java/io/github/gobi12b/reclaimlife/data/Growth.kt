package io.github.gobi12b.reclaimlife.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

// Your tree: a companion that grows overnight after each good day and never shrinks. Everything
// here is pure (no Android), so the rules — monotonic growth, rest days, stage thresholds — are
// unit-tested on the JVM. A closed day is judged once, written to an append-only ledger, and never
// re-judged; the total is carried + the sum of the rows, so it can only go up.

const val GROWTH_LEDGER_KEEP = 400
/** Pauses on one day adding up to this rest the tree for that day. Shorter ones only pause growth while they run. */
const val PAUSE_REST_THRESHOLD_MS = 120 * 60_000L
/** Half the waking window has to be tracked for a day to grow: switching the counter off doesn't. */
const val MIN_TRACKED_MINUTES_TO_GROW = 480
/** How far back a missed close is still evaluated (the limit-change and swap logs keep 14 days). */
const val GROWTH_CLOSE_WINDOW_DAYS = 14
/** The limit-change and swap logs keep this long. */
const val GROWTH_LOG_RETENTION_MS = 14 * DAY_MS
/** Swaps earn growth up to this many a day. */
const val SWAPS_THAT_GROW_PER_DAY = 3
/** Longest tree name; the fields cap input at this. */
const val TREE_NAME_MAX = 16

private const val DAY_POINTS = 10
private const val NO_BASELINE_POINTS = 5
private const val SAVED_MS_PER_POINT = 6 * 60_000L
private const val MAX_SAVED_POINTS = 20
private const val SWAP_POINTS = 3
/** Growing-day pace assumed before there are 3 grown days to average. */
private const val DEFAULT_PACE = 15.0

// ---- Stages -------------------------------------------------------------------------------------

/**
 * Ten stages over about five months. [maxDetails] is how many leaf tufts, blossoms or fruits show
 * up within the stage, one at a time, so something new appears about every week.
 */
enum class TreeStage(val thresholdPoints: Long, val label: String, val maxDetails: Int, val nowPhrase: String, val note: String) {
    SEED(0, "Seed", 0, "is a seed", "Every tree starts here."),
    SPROUT(15, "Sprout", 0, "has sprouted", "First leaves."),
    SEEDLING(50, "Seedling", 0, "is a seedling now", "Roots are forming."),
    YOUNG_PLANT(110, "Young plant", 0, "is a young plant now", "Steady beats perfect."),
    SAPLING(240, "Sapling", 0, "is a sapling now", "Growing fast."),
    YOUNG_TREE(420, "Young tree", 4, "is a young tree now", "It has a trunk now."),
    TREE(650, "Tree", 4, "is a real tree now", "Weeks of small choices."),
    BLOSSOMING(1100, "Blossoming tree", 8, "is blossoming", "In bloom."),
    FRUITING(1700, "Fruiting tree", 8, "is bearing fruit", "Bearing fruit."),
    FULL_CANOPY(2700, "Full canopy", 6, "has reached full canopy", "Fully grown.")
}

/** Late blossoms at full canopy come one per this many points. */
private const val CANOPY_POINTS_PER_DETAIL = 250L

/** The last stage whose threshold [totalPoints] reaches; anything negative is still a seed. */
fun treeStage(totalPoints: Long): TreeStage = TreeStage.entries.last { totalPoints >= it.thresholdPoints || it == TreeStage.SEED }

/** The stage after [stage], or null at full canopy. */
fun nextTreeStage(stage: TreeStage): TreeStage? = TreeStage.entries.getOrNull(stage.ordinal + 1)

/** How many of the stage's details show at [totalPoints]. The last one shows before the next stage arrives. */
fun detailsShown(stage: TreeStage, totalPoints: Long): Int {
    if (stage.maxDetails == 0) return 0
    val past = (totalPoints - stage.thresholdPoints).coerceAtLeast(0L)
    val next = nextTreeStage(stage)
        ?: return (past / CANOPY_POINTS_PER_DETAIL).toInt().coerceAtMost(stage.maxDetails)
    val span = next.thresholdPoints - stage.thresholdPoints
    return (past * (stage.maxDetails + 1) / span).toInt().coerceAtMost(stage.maxDetails)
}

/** The stage the old time-won-back plant had reached, mapped to the tree — upgrades never see a smaller plant. */
fun legacyFloorStage(sinceStartedMs: Long): TreeStage {
    val hour = 3_600_000L
    return when {
        sinceStartedMs >= 72 * hour -> TreeStage.YOUNG_TREE
        sinceStartedMs >= 24 * hour -> TreeStage.SAPLING
        sinceStartedMs >= 12 * hour -> TreeStage.YOUNG_PLANT
        sinceStartedMs >= 3 * hour -> TreeStage.SEEDLING
        sinceStartedMs >= 30 * 60_000L -> TreeStage.SPROUT
        else -> TreeStage.SEED
    }
}

// ---- Ledger -------------------------------------------------------------------------------------

/** How a closed day went for the tree. Everything but [GREW] is a rest: nothing earned, nothing lost. */
enum class GrowthCode(val code: Char) { GREW('G'), OVER('O'), LIMIT_RAISED('L'), PAUSED('P'), NO_DATA('N') }

/** One closed day in the ledger. Only [GrowthCode.GREW] rows have points, and none are negative. */
data class GrowthDay(
    val dateKey: String,
    val code: GrowthCode,
    val dayPoints: Int = 0,
    val savedPoints: Int = 0,
    val swapPoints: Int = 0,
    val bonusPoints: Int = 0
) {
    val points: Int get() = dayPoints + savedPoints + swapPoints + bonusPoints
}

/** `2026-09-28=G:10:7:3:0;2026-09-29=O`. Unknown codes and malformed rows are dropped. */
fun parseGrowthLedger(raw: String?): Map<String, GrowthDay> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val eq = entry.indexOf('=')
        if (eq != 10) return@mapNotNull null
        val key = entry.substring(0, eq)
        val f = entry.substring(eq + 1).split(':')
        val code = GrowthCode.entries.firstOrNull { it.code == f[0].singleOrNull() } ?: return@mapNotNull null
        if (code != GrowthCode.GREW) return@mapNotNull key to GrowthDay(key, code)
        if (f.size != 5) return@mapNotNull null
        val n = f.drop(1).map { it.toIntOrNull()?.takeIf { v -> v >= 0 } ?: return@mapNotNull null }
        key to GrowthDay(key, code, n[0], n[1], n[2], n[3])
    }.toMap()

fun serializeGrowthLedger(rows: Map<String, GrowthDay>): String =
    rows.values.sortedBy { it.dateKey }.joinToString(";") { row ->
        if (row.code == GrowthCode.GREW) {
            "${row.dateKey}=G:${row.dayPoints}:${row.savedPoints}:${row.swapPoints}:${row.bonusPoints}"
        } else {
            "${row.dateKey}=${row.code.code}"
        }
    }

/** Append-only: a day already in the ledger is never replaced, whatever it would evaluate to now. */
fun addGrowthDays(existing: Map<String, GrowthDay>, new: Collection<GrowthDay>): Map<String, GrowthDay> =
    existing + new.filter { it.dateKey !in existing }.associateBy { it.dateKey }

/** Keeps the newest [keep] rows. The points of the dropped ones come back to be carried, so the total holds. */
fun trimLedger(rows: Map<String, GrowthDay>, keep: Int = GROWTH_LEDGER_KEEP): Pair<Map<String, GrowthDay>, Long> {
    if (rows.size <= keep) return rows to 0L
    val sorted = rows.values.sortedBy { it.dateKey }
    val dropped = sorted.dropLast(keep)
    return sorted.takeLast(keep).associateBy { it.dateKey } to dropped.sumOf { it.points.toLong() }
}

fun growthTotal(rows: Map<String, GrowthDay>, carried: Long): Long = carried + rows.values.sumOf { it.points.toLong() }

/**
 * Growing days to the next stage at the recent pace: the average of the last 14 grown days, or
 * 15 points with fewer than 3. Null at full canopy.
 */
fun growingDaysToNext(totalPoints: Long, rows: Map<String, GrowthDay>): Int? {
    val next = nextTreeStage(treeStage(totalPoints)) ?: return null
    return growingDaysTo(next, totalPoints, rows)
}

/** Growing days until [target] at the recent pace. */
fun growingDaysTo(target: TreeStage, totalPoints: Long, rows: Map<String, GrowthDay>): Int {
    val grew = rows.values.filter { it.code == GrowthCode.GREW }.sortedBy { it.dateKey }.takeLast(14)
    val pace = if (grew.size < 3) DEFAULT_PACE else grew.sumOf { it.points }.toDouble() / grew.size
    val remaining = target.thresholdPoints - totalPoints
    return ceil(remaining / pace.coerceAtLeast(1.0)).toInt().coerceAtLeast(1)
}

/** Every stage after the current one, with its growing-day estimate. */
fun upcomingStages(totalPoints: Long, rows: Map<String, GrowthDay>): List<Pair<TreeStage, Int>> =
    TreeStage.entries.filter { it.thresholdPoints > totalPoints }.map { it to growingDaysTo(it, totalPoints, rows) }

/** A branch comes with every this-many new leaves. */
const val SPRIGS_PER_BRANCH = 3

/** The most daily leaves a stage shows; the next stage's shape takes over from there. */
const val MAX_SPRIGS = 12

/**
 * Growing days since [stage] was reached, one leaf each, capped at [MAX_SPRIGS]. The day that
 * crossed into the stage counts as the stage-up itself, not a leaf.
 */
fun sprigsInStage(rows: Map<String, GrowthDay>, carried: Long, stage: TreeStage): Int {
    val reached = stageReachedOn(rows, carried, stage)
    return rows.values.count { it.code == GrowthCode.GREW && (reached == null || it.dateKey > reached) }.coerceAtMost(MAX_SPRIGS)
}

/** The next streak length that earns a boost: 3, 7, 14, 30, then every 30. */
fun nextStreakMilestone(streak: Int): Int = when {
    streak < 3 -> 3
    streak < 7 -> 7
    streak < 14 -> 14
    else -> (streak / 30 + 1) * 30
}

/** The ledger key whose row carried the total across [stage]'s threshold, or null if [carried] alone did. */
fun stageReachedOn(rows: Map<String, GrowthDay>, carried: Long, stage: TreeStage): String? {
    if (carried >= stage.thresholdPoints) return null
    var running = carried
    for (row in rows.values.sortedBy { it.dateKey }) {
        running += row.points
        if (running >= stage.thresholdPoints) return row.dateKey
    }
    return null
}

// ---- Logs: limit changes and swaps --------------------------------------------------------------

enum class LimitKind(val code: Char) { DAILY('D'), HOURLY('H'), MODE('M') }

/** One change to a limit. [from] and [to] are numbers for DAILY/HOURLY and [LimitMode] names for MODE. */
data class LimitChange(val timeMs: Long, val kind: LimitKind, val from: String, val to: String)

/**
 * A limit loosened on a day and not put back. [backTo] is the start-of-day number (DAILY/HOURLY);
 * for MODE, [backToMode] is the start-of-day mode and [droppedLimit] is "daily" or "hourly".
 */
data class LimitRaise(val kind: LimitKind, val backTo: Int = 0, val backToMode: LimitMode? = null, val droppedLimit: String? = null)

/** `timeMs,K,from,to;…`. Malformed entries are dropped. */
fun parseLimitChanges(raw: String?): List<LimitChange> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val f = entry.split(',')
        if (f.size != 4) return@mapNotNull null
        val time = f[0].toLongOrNull() ?: return@mapNotNull null
        val kind = LimitKind.entries.firstOrNull { it.code == f[1].singleOrNull() } ?: return@mapNotNull null
        val valid = when (kind) {
            LimitKind.MODE -> f[2] in LimitMode.entries.map { it.name } && f[3] in LimitMode.entries.map { it.name }
            else -> f[2].toIntOrNull() != null && f[3].toIntOrNull() != null
        }
        if (valid) LimitChange(time, kind, f[2], f[3]) else null
    }

/** Keeps [GROWTH_LOG_RETENTION_MS] before [nowMs], oldest first. */
fun serializeLimitChanges(changes: List<LimitChange>, nowMs: Long): String =
    changes.filter { it.timeMs > nowMs - GROWTH_LOG_RETENTION_MS }
        .sortedBy { it.timeMs }
        .joinToString(";") { "${it.timeMs},${it.kind.code},${it.from},${it.to}" }

/** `2026-09-29=2;…`, keyed by local midnight date. */
fun parseSwapsByDay(raw: String?): Map<String, Int> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val f = entry.split('=')
        if (f.size != 2 || f[0].length != 10) return@mapNotNull null
        val n = f[1].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        f[0] to n
    }.toMap()

/** Keeps dates from [keepFromKey] on. */
fun serializeSwapsByDay(swaps: Map<String, Int>, keepFromKey: String): String =
    swaps.filter { it.key >= keepFromKey && it.value > 0 }.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

/** The mode in force at [atMs]: the latest change at or before it, else the first later one's start, else [current]. */
fun modeAt(changes: List<LimitChange>, atMs: Long, current: LimitMode): LimitMode {
    val modes = changes.filter { it.kind == LimitKind.MODE }.sortedBy { it.timeMs }
    val stored = modes.lastOrNull { it.timeMs <= atMs }?.to ?: modes.firstOrNull { it.timeMs > atMs }?.from
    return stored?.let { LimitMode.fromStored(it) } ?: current
}

private fun List<LimitChange>.within(kind: LimitKind, dayStartMs: Long, dayEndMs: Long) =
    filter { it.kind == kind && it.timeMs >= dayStartMs && it.timeMs < dayEndMs }.sortedBy { it.timeMs }

/**
 * Whether a limit ended the day looser than it started: a limit switched off, or a number raised
 * that's enforced at the end of the day. Raising and putting it back the same day is no raise.
 */
fun limitRaiseOn(changes: List<LimitChange>, dayStartMs: Long, dayEndMs: Long, modeAtEnd: LimitMode): LimitRaise? {
    val modes = changes.within(LimitKind.MODE, dayStartMs, dayEndMs)
    if (modes.isNotEmpty()) {
        val from = LimitMode.fromStored(modes.first().from)
        val to = LimitMode.fromStored(modes.last().to)
        if (from.loosensTo(to)) {
            val dropped = if (from.usesDaily && !to.usesDaily) "daily" else "hourly"
            return LimitRaise(LimitKind.MODE, backToMode = from, droppedLimit = dropped)
        }
    }
    val daily = changes.within(LimitKind.DAILY, dayStartMs, dayEndMs)
    if (daily.isNotEmpty() && modeAtEnd.usesDaily) {
        val from = daily.first().from.toInt()
        if (daily.last().to.toInt() > from) return LimitRaise(LimitKind.DAILY, backTo = from)
    }
    val hourly = changes.within(LimitKind.HOURLY, dayStartMs, dayEndMs)
    if (hourly.isNotEmpty() && modeAtEnd.usesHourly) {
        val from = hourly.first().from.toInt()
        if (hourly.last().to.toInt() > from) return LimitRaise(LimitKind.HOURLY, backTo = from)
    }
    return null
}

/** The start-of-day daily limit if it ended the day lower, else null — tightening never rests the tree. */
fun loweredDailyFrom(changes: List<LimitChange>, dayStartMs: Long, dayEndMs: Long): Int? {
    val daily = changes.within(LimitKind.DAILY, dayStartMs, dayEndMs)
    if (daily.isEmpty()) return null
    val from = daily.first().from.toInt()
    return from.takeIf { daily.last().to.toInt() < it }
}

// ---- Pauses -------------------------------------------------------------------------------------

/** Paused time inside the day, overlaps merged, up to [nowMs]. */
fun pausedMsOn(log: List<PauseEntry>, dayStartMs: Long, dayEndMs: Long, nowMs: Long): Long =
    pausedIntervals(log, nowMs).mapNotNull { it.clipTo(dayStartMs..dayEndMs) }.merged().sumOf { it.lengthMs }

/** A Rest of today pause that has actually started in the day (a pending one isn't a rest yet). */
fun restOfTodayOn(log: List<PauseEntry>, dayStartMs: Long, dayEndMs: Long, nowMs: Long): Boolean =
    log.any { it.duration == PauseDuration.REST_OF_TODAY && it.startMs in dayStartMs until dayEndMs && it.startMs <= nowMs }

// ---- Day outcomes -------------------------------------------------------------------------------

private fun DailyRecord.totalReels(): Int = apps.values.sumOf { it.reels }

/**
 * Days missing from the history whose record shows the counter running (8+ tracked hours) and no
 * reels at all: nothing was watched, so nothing closed the day out. They count as within.
 */
fun quietDays(history: Map<String, DayOutcome>, records: Map<String, DailyRecord>): Set<String> =
    records.values
        .filter { it.dateKey !in history && it.trackedWakingMinutes >= MIN_TRACKED_MINUTES_TO_GROW && it.totalReels() == 0 }
        .map { it.dateKey }
        .toSet()

/** The history with quiet days filled in as within. Recorded outcomes always win. */
fun effectiveDayHistory(history: Map<String, DayOutcome>, records: Map<String, DailyRecord>): Map<String, DayOutcome> =
    quietDays(history, records).associateWith { DayOutcome.WITHIN } + history

// ---- Evaluating a closed day --------------------------------------------------------------------

data class GrowthInputs(
    val dateKey: String,
    /** From the effective history. */
    val outcome: DayOutcome?,
    val record: DailyRecord?,
    /** Saved on the day, or null when no baseline applies to it. */
    val savedMs: Long?,
    val raise: LimitRaise?,
    /** The start-of-day daily limit if it was lowered that day, else null. */
    val loweredDailyFrom: Int?,
    val restOfToday: Boolean,
    val pausedMs: Long,
    val swaps: Int,
    val streakThroughDay: Int,
    /** Upgrade backfill: days before recording began have no record, and that's not a lack of data. */
    val backfill: Boolean = false
)

/** 3→15, 7→30, 14→45, 30→60, then 60 at every further multiple of 30. */
fun milestoneBonus(streak: Int): Int = when {
    streak == 3 -> 15
    streak == 7 -> 30
    streak == 14 -> 45
    streak >= 30 && streak % 30 == 0 -> 60
    else -> 0
}

/**
 * A closed day's row. The first rest rule that matches wins (over, raised, paused, no data);
 * otherwise it grew: 10 for the day, time won back (or a flat 5 with no baseline), swaps and any
 * streak milestone. Never negative.
 */
fun evaluateGrowthDay(inputs: GrowthInputs): GrowthDay {
    val key = inputs.dateKey
    val record = inputs.record
    val tightened = inputs.loweredDailyFrom != null && record != null && record.totalReels() <= inputs.loweredDailyFrom
    val noData = inputs.outcome == null ||
        (record == null && !inputs.backfill) ||
        (record != null && record.trackedWakingMinutes < MIN_TRACKED_MINUTES_TO_GROW)
    return when {
        inputs.outcome == DayOutcome.OVER && !tightened -> GrowthDay(key, GrowthCode.OVER)
        inputs.raise != null -> GrowthDay(key, GrowthCode.LIMIT_RAISED)
        inputs.restOfToday || inputs.pausedMs >= PAUSE_REST_THRESHOLD_MS -> GrowthDay(key, GrowthCode.PAUSED)
        noData -> GrowthDay(key, GrowthCode.NO_DATA)
        else -> GrowthDay(
            dateKey = key,
            code = GrowthCode.GREW,
            dayPoints = DAY_POINTS,
            savedPoints = inputs.savedMs?.let { (it.coerceAtLeast(0L) / SAVED_MS_PER_POINT).toInt().coerceAtMost(MAX_SAVED_POINTS) }
                ?: NO_BASELINE_POINTS,
            swapPoints = inputs.swaps.coerceIn(0, SWAPS_THAT_GROW_PER_DAY) * SWAP_POINTS,
            bonusPoints = milestoneBonus(inputs.streakThroughDay)
        )
    }
}

// ---- Today --------------------------------------------------------------------------------------

/** Why the tree is resting today. Null means it's growing. */
enum class RestReason { OVER_LIMIT, LIMIT_RAISED, PAUSED_DAY, PAUSED_NOW, TRACKING_OFF }

/** Today's state, first match wins. Periods (a short pause, counting off) end on their own. */
fun todayRest(
    mode: LimitMode,
    todayCount: Int,
    dailyLimit: Int,
    extras: Int,
    hourlyBreaks: Int,
    loweredDailyFrom: Int?,
    raise: LimitRaise?,
    restOfTodayStarted: Boolean,
    pausedTodayMs: Long,
    pausedNow: Boolean,
    trackingOn: Boolean
): RestReason? {
    val tightened = loweredDailyFrom != null && todayCount <= loweredDailyFrom
    return when {
        !dayWithinLimit(mode, todayCount, dailyLimit, extras, hourlyBreaks) && !tightened -> RestReason.OVER_LIMIT
        raise != null -> RestReason.LIMIT_RAISED
        restOfTodayStarted || pausedTodayMs >= PAUSE_REST_THRESHOLD_MS -> RestReason.PAUSED_DAY
        pausedNow -> RestReason.PAUSED_NOW
        !trackingOn -> RestReason.TRACKING_OFF
        else -> null
    }
}

// ---- Call-outs ----------------------------------------------------------------------------------

enum class CalloutKind { RESTING, NEW_STAGE, MILESTONE, FIRST_DAY, BACK_AFTER_REST, GREW_OVERNIGHT, GROWING }

enum class CalloutGlyph { LEAF, MOON, STAR }

/** The one offer a call-out can carry: putting a raised limit back. Shown in the sheet, not on Home. */
data class CalloutAction(val setBack: LimitRaise)

data class TreeCallout(val kind: CalloutKind, val text: String, val glyph: CalloutGlyph, val action: CalloutAction? = null)

// Every tree line frames what's next, never what went wrong, and gives the tree no feelings about
// the user. {Name} starts a sentence, {name} sits mid-sentence. A unit test checks them for loss words.
internal val GROWING_LINES = listOf(
    "{Name} is growing today."
)
internal val GREW_OVERNIGHT_LINES = listOf(
    "{Name} grew a new leaf overnight."
)
internal const val GREW_BRANCH_LINE = "{Name} grew a new branch overnight."
internal val BACK_AFTER_REST_LINES = listOf(
    "Fresh day. {Name} is growing again.",
    "{Name} is awake and growing again.",
    "New day, new growth for {name}."
)
internal const val FIRST_DAY_LINE = "{Name} is planted. It grows each day under your limit."
internal const val MILESTONE_LINE = "{n} days under your limit. {Name} got an extra boost."
internal const val KEEPSAKE_LINE = "{n} days in a row! {visitor} joined {name}."
internal val OVER_LIMIT_LINES = listOf(
    "{Name} is taking today off. Tomorrow's a fresh start.",
    "{Name} is resting today."
)
internal const val RAISED_DAILY_LINE = "{Name} rests while the limit is higher. Set it to {backTo} to grow."
internal const val RAISED_HOURLY_LINE =
    "{Name} rests while the hourly limit is higher. Set it to {backTo} to grow."
internal const val RAISED_MODE_LINE = "{Name} rests while the {dropped} limit is off."
internal val PAUSED_DAY_LINES = listOf(
    "{Name} is resting today. Enjoy your time off.",
    "{Name} rests for the rest of today and grows again tomorrow."
)
internal val PAUSED_NOW_LINES = listOf(
    "{Name} rests while you're paused.",
    "{Name} is resting during your pause."
)
internal const val TRACKING_OFF_LINE = "{Name} grows again once counting is back on."

/** Every call-out template, for the tone test. */
internal val ALL_TREE_LINES: List<String> =
    GROWING_LINES + GREW_OVERNIGHT_LINES + GREW_BRANCH_LINE + BACK_AFTER_REST_LINES + FIRST_DAY_LINE + MILESTONE_LINE + KEEPSAKE_LINE + OVER_LIMIT_LINES +
        RAISED_DAILY_LINE + RAISED_HOURLY_LINE + RAISED_MODE_LINE + PAUSED_DAY_LINES + PAUSED_NOW_LINES + TRACKING_OFF_LINE

/** "Fern", or "Your tree" when unnamed — for the start of a sentence. */
fun treeNameTitle(name: String): String = name.trim().ifEmpty { "Your tree" }

/** "Fern", or "your tree" when unnamed — for mid-sentence. */
fun treeNameInline(name: String): String = name.trim().ifEmpty { "your tree" }

internal fun fillTreeLine(template: String, name: String): String =
    template.replace("{Name}", treeNameTitle(name)).replace("{name}", treeNameInline(name))

/** Days since 1970-01-01 for a `yyyy-MM-dd` key, read in UTC so it's the same everywhere. 0 when malformed. */
fun epochDay(dateKey: String): Long {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val date = runCatching { format.parse(dateKey) }.getOrNull() ?: return 0L
    return date.time / DAY_MS
}

/** The same line all day, a different one across days. */
internal fun pick(pool: List<String>, todayKey: String): String = pool[(epochDay(todayKey) % pool.size).toInt()]

/** The key of the day before [dateKey]. */
fun previousDateKey(dateKey: String): String {
    val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey) }.getOrNull() ?: return dateKey
    val cal = Calendar.getInstance().apply {
        time = date
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return dateKeyOf(cal)
}

/** The line for a resting day, by reason (and, for a raise, by which limit). */
fun restingLine(reason: RestReason, raise: LimitRaise?, name: String, todayKey: String): String = fillTreeLine(
    when (reason) {
        RestReason.OVER_LIMIT -> pick(OVER_LIMIT_LINES, todayKey)
        RestReason.LIMIT_RAISED -> when (raise?.kind) {
            LimitKind.HOURLY -> RAISED_HOURLY_LINE.replace("{backTo}", raise.backTo.toString())
            LimitKind.MODE -> RAISED_MODE_LINE.replace("{dropped}", raise.droppedLimit ?: "daily")
            else -> RAISED_DAILY_LINE.replace("{backTo}", (raise?.backTo ?: 0).toString())
        }
        RestReason.PAUSED_DAY -> pick(PAUSED_DAY_LINES, todayKey)
        RestReason.PAUSED_NOW -> pick(PAUSED_NOW_LINES, todayKey)
        RestReason.TRACKING_OFF -> TRACKING_OFF_LINE
    },
    name
)

/**
 * The one tree line for now. Resting outranks a celebration: the current state is what someone
 * needs to know. "The most recent closed row" is yesterday's; with none (the app was closed for
 * days), the rules that read it are skipped.
 */
fun treeCallout(
    todayRest: RestReason?,
    raise: LimitRaise?,
    stage: TreeStage,
    rows: Map<String, GrowthDay>,
    carried: Long,
    streak: Int,
    name: String,
    todayKey: String,
    localHour: Int,
    sprigs: Int = 0
): TreeCallout {
    if (todayRest != null) {
        val action = if (todayRest == RestReason.LIMIT_RAISED && raise != null) CalloutAction(raise) else null
        return TreeCallout(CalloutKind.RESTING, restingLine(todayRest, raise, name, todayKey), CalloutGlyph.MOON, action)
    }
    val yesterday = previousDateKey(todayKey)
    val last = rows[yesterday]
    if (last != null && stage != TreeStage.SEED && stageReachedOn(rows, carried, stage) == yesterday) {
        return TreeCallout(CalloutKind.NEW_STAGE, "${treeNameTitle(name)} ${stage.nowPhrase}!", CalloutGlyph.STAR)
    }
    if (last != null && last.bonusPoints > 0) {
        val visitor = keepsakeAt(streak)
        val line = if (visitor != null) {
            KEEPSAKE_LINE.replace("{n}", streak.toString()).replace("{visitor}", visitor.arrival)
        } else {
            MILESTONE_LINE.replace("{n}", streak.toString())
        }
        return TreeCallout(CalloutKind.MILESTONE, fillTreeLine(line, name), CalloutGlyph.STAR)
    }
    if (rows.values.none { it.code == GrowthCode.GREW } && carried == 0L) {
        return TreeCallout(CalloutKind.FIRST_DAY, fillTreeLine(FIRST_DAY_LINE, name), CalloutGlyph.LEAF)
    }
    if (last != null && last.code != GrowthCode.GREW) {
        return TreeCallout(CalloutKind.BACK_AFTER_REST, fillTreeLine(pick(BACK_AFTER_REST_LINES, todayKey), name), CalloutGlyph.LEAF)
    }
    if (last != null && localHour < 12) {
        val line = if (sprigs > 0 && sprigs % SPRIGS_PER_BRANCH == 0) GREW_BRANCH_LINE else pick(GREW_OVERNIGHT_LINES, todayKey)
        return TreeCallout(CalloutKind.GREW_OVERNIGHT, fillTreeLine(line, name), CalloutGlyph.LEAF)
    }
    return TreeCallout(CalloutKind.GROWING, fillTreeLine(pick(GROWING_LINES, todayKey), name), CalloutGlyph.LEAF)
}

// ---- What Home shows ----------------------------------------------------------------------------

/** One day in the tree sheet's strip. [code] is null for today (still open) and for days with no row. */
data class GrowthCell(val dateKey: String, val code: GrowthCode?, val isToday: Boolean)

data class TreeState(
    /** "" when unnamed. */
    val name: String,
    val totalPoints: Long,
    val stage: TreeStage,
    val details: Int,
    val nextStage: TreeStage?,
    /** Null at full canopy. */
    val fractionToNext: Float?,
    val growingDaysToNext: Int?,
    /** Null means growing today. */
    val todayRest: RestReason?,
    /** Today's raise, for the "Set it back" action. */
    val raise: LimitRaise?,
    val callout: TreeCallout,
    /** Oldest first; the last is today. */
    val last7: List<GrowthCell>,
    /** Grown days ever (in the ledger). */
    val grewDays: Int,
    /** Leaves grown since this stage began: one per growing day; every [SPRIGS_PER_BRANCH]th brings a branch. */
    val sprigs: Int,
    val plantedAtMs: Long?,
    /** The last 7 local days, today included. */
    val swapsThisWeek: Int,
    val hasBaseline: Boolean,
    /** Days in a row under the limit. */
    val streak: Int,
    /** Stages still ahead, with growing-day estimates. */
    val upcoming: List<Pair<TreeStage, Int>>,
    /** Visitors earned by streaks, kept for good. */
    val keepsakes: List<Keepsake>,
    val nextKeepsake: Keepsake?,
    /** Yesterday's key, so a celebration plays once per closed day. */
    val lastClosedKey: String
)

/** Assembles [TreeState] from the stored ledger and today's state. */
fun treeState(
    name: String,
    rows: Map<String, GrowthDay>,
    carried: Long,
    todayRest: RestReason?,
    raise: LimitRaise?,
    streak: Int,
    todayKey: String,
    localHour: Int,
    plantedAtMs: Long?,
    swapsByDay: Map<String, Int>,
    hasBaseline: Boolean,
    bestStreak: Int = streak
): TreeState {
    val total = growthTotal(rows, carried)
    val stage = treeStage(total)
    val next = nextTreeStage(stage)
    val keys = generateSequence(todayKey) { previousDateKey(it) }.take(7).toList().reversed()
    val sprigs = sprigsInStage(rows, carried, stage)
    return TreeState(
        name = name.trim(),
        totalPoints = total,
        stage = stage,
        details = detailsShown(stage, total),
        nextStage = next,
        fractionToNext = next?.let {
            ((total - stage.thresholdPoints).toFloat() / (it.thresholdPoints - stage.thresholdPoints)).coerceIn(0f, 1f)
        },
        growingDaysToNext = growingDaysToNext(total, rows),
        todayRest = todayRest,
        raise = raise.takeIf { todayRest == RestReason.LIMIT_RAISED },
        callout = treeCallout(todayRest, raise, stage, rows, carried, streak, name, todayKey, localHour, sprigs),
        sprigs = sprigs,
        last7 = keys.map { GrowthCell(it, if (it == todayKey) null else rows[it]?.code, it == todayKey) },
        grewDays = rows.values.count { it.code == GrowthCode.GREW },
        plantedAtMs = plantedAtMs,
        swapsThisWeek = keys.sumOf { swapsByDay[it] ?: 0 },
        hasBaseline = hasBaseline,
        streak = streak,
        upcoming = upcomingStages(total, rows),
        keepsakes = keepsakesFor(maxOf(bestStreak, streak)),
        nextKeepsake = nextKeepsake(maxOf(bestStreak, streak)),
        lastClosedKey = keys[keys.size - 2]
    )
}

/** "Fern · Sapling" — the call-out row's first line. */
fun treeTitleLine(tree: TreeState): String = "${treeNameTitle(tree.name)} · ${tree.stage.label}"

/**
 * The pause sheet's tree line for the selected length: a short pause only rests the tree while it
 * runs, unless today's pauses would reach 2 hours; Rest of today rests the whole day.
 */
fun pauseTreeLine(name: String, duration: PauseDuration, pausedTodayMs: Long): String {
    val title = treeNameTitle(name)
    val lengthMs = when (duration) {
        PauseDuration.FIFTEEN_MINUTES -> 15 * 60_000L
        PauseDuration.ONE_HOUR -> 60 * 60_000L
        PauseDuration.REST_OF_TODAY -> return "$title rests for the rest of today and grows again tomorrow."
    }
    return if (pausedTodayMs + lengthMs >= PAUSE_REST_THRESHOLD_MS) {
        "$title rests today (over 2 hours paused)."
    } else {
        "$title rests while paused."
    }
}

/** The raise confirms' added line, or null when the tree is already resting today for another reason. */
fun raiseTreeLine(name: String, rest: RestReason?, dropping: Boolean = false): String? =
    if (rest == null || rest == RestReason.PAUSED_NOW) {
        "${treeNameTitle(name)} rests for today if you ${if (dropping) "switch" else "raise it"}."
    } else {
        null
    }
