package io.github.gobi12b.reclaimlife.data

import java.util.Calendar
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    private fun msOf(year: Int, month: Int, day: Int, h: Int = 0, m: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, h, m, 0)
        }.timeInMillis

    private fun record(key: String, tracked: Int = 960, reels: Int = 0) =
        DailyRecord(key, mapOf("com.instagram.android" to AppDay(30, reels)), tracked)

    private val key = "2026-09-28"

    private fun inputs(
        outcome: DayOutcome? = DayOutcome.WITHIN,
        record: DailyRecord? = record(key, reels = 20),
        savedMs: Long? = 45 * minute,
        raise: LimitRaise? = null,
        lowered: Int? = null,
        restOfToday: Boolean = false,
        pausedMs: Long = 0L,
        swaps: Int = 0,
        streak: Int = 1,
        backfill: Boolean = false
    ) = GrowthInputs(key, outcome, record, savedMs, raise, lowered, restOfToday, pausedMs, swaps, streak, backfill)

    // ---- Task 1: day outcome gaps ----

    @Test
    fun quietDaysNeedTrackingAndNoReelsAndNoOutcome() {
        val records = mapOf(
            "2026-09-20" to record("2026-09-20", tracked = 600),
            "2026-09-21" to record("2026-09-21", tracked = 300),
            "2026-09-22" to record("2026-09-22", tracked = 600, reels = 3),
            "2026-09-23" to record("2026-09-23", tracked = 600)
        )
        val history = mapOf("2026-09-23" to DayOutcome.OVER)
        assertEquals(setOf("2026-09-20"), quietDays(history, records))
    }

    @Test
    fun effectiveHistoryAddsQuietDaysAndKeepsRecordedOutcomes() {
        val records = mapOf("2026-09-20" to record("2026-09-20"), "2026-09-23" to record("2026-09-23"))
        val effective = effectiveDayHistory(mapOf("2026-09-23" to DayOutcome.OVER), records)
        assertEquals(DayOutcome.WITHIN, effective["2026-09-20"])
        assertEquals(DayOutcome.OVER, effective["2026-09-23"])
    }

    // ---- Task 2: logs ----

    @Test
    fun limitChangesRoundTripAndDropMalformed() {
        val now = msOf(2026, 9, 29, 12)
        val changes = listOf(
            LimitChange(now - hour, LimitKind.DAILY, "30", "40"),
            LimitChange(now - minute, LimitKind.MODE, "BOTH", "DAILY")
        )
        assertEquals(changes, parseLimitChanges(serializeLimitChanges(changes, now)))
        val raw = "junk;1,D,30;2,X,1,2;3,D,a,4;4,M,BOTH,NOPE;${now},H,20,30"
        assertEquals(listOf(LimitChange(now, LimitKind.HOURLY, "20", "30")), parseLimitChanges(raw))
    }

    @Test
    fun limitChangesKeepTwoWeeks() {
        val now = msOf(2026, 9, 29, 12)
        val old = LimitChange(now - 15 * DAY_MS, LimitKind.DAILY, "30", "40")
        val recent = LimitChange(now - 13 * DAY_MS, LimitKind.DAILY, "40", "30")
        assertEquals(listOf(recent), parseLimitChanges(serializeLimitChanges(listOf(old, recent), now)))
    }

    @Test
    fun swapsRoundTripAndTrimByDate() {
        val swaps = mapOf("2026-09-10" to 1, "2026-09-20" to 2, "2026-09-29" to 3)
        assertEquals(swaps, parseSwapsByDay(serializeSwapsByDay(swaps, "2026-09-01")))
        assertEquals(mapOf("2026-09-20" to 2, "2026-09-29" to 3), parseSwapsByDay(serializeSwapsByDay(swaps, "2026-09-15")))
        assertEquals(mapOf("2026-09-29" to 1), parseSwapsByDay("2026-09-29=1;bad;2026-09-30=x;2026-10-01=0"))
    }

    // ---- Task 3: evaluation ----

    @Test
    fun withinWithSavedTimeGrows() {
        assertEquals(GrowthDay(key, GrowthCode.GREW, 10, 7, 0, 0), evaluateGrowthDay(inputs()))
    }

    @Test
    fun savedPointsAreCapped() {
        assertEquals(20, evaluateGrowthDay(inputs(savedMs = 3 * hour)).savedPoints)
    }

    @Test
    fun noBaselineGivesFlatFive() {
        assertEquals(5, evaluateGrowthDay(inputs(savedMs = null)).savedPoints)
    }

    @Test
    fun swapsCountUpToThree() {
        assertEquals(9, evaluateGrowthDay(inputs(swaps = 4)).swapPoints)
        assertEquals(3, evaluateGrowthDay(inputs(swaps = 1)).swapPoints)
    }

    @Test
    fun milestones() {
        assertEquals(30, evaluateGrowthDay(inputs(streak = 7)).bonusPoints)
        assertEquals(60, evaluateGrowthDay(inputs(streak = 60)).bonusPoints)
        assertEquals(0, evaluateGrowthDay(inputs(streak = 45)).bonusPoints)
        assertEquals(listOf(15, 30, 45, 60, 60, 60, 0, 0), listOf(3, 7, 14, 30, 90, 120, 31, 0).map(::milestoneBonus))
    }

    @Test
    fun overRestsWithNoPoints() {
        val row = evaluateGrowthDay(inputs(outcome = DayOutcome.OVER))
        assertEquals(GrowthCode.OVER, row.code)
        assertEquals(0, row.points)
    }

    @Test
    fun loweringTheLimitNeverRests() {
        assertEquals(GrowthCode.GREW, evaluateGrowthDay(inputs(outcome = DayOutcome.OVER, record = record(key, reels = 35), lowered = 40)).code)
        assertEquals(GrowthCode.OVER, evaluateGrowthDay(inputs(outcome = DayOutcome.OVER, record = record(key, reels = 45), lowered = 40)).code)
    }

    private val start = msOf(2026, 9, 28)
    private val end = msOf(2026, 9, 29)

    private fun change(h: Int, kind: LimitKind, from: String, to: String) = LimitChange(start + h * hour, kind, from, to)

    private fun raiseFor(changes: List<LimitChange>, mode: LimitMode = LimitMode.DAILY) = limitRaiseOn(changes, start, end, mode)

    @Test
    fun raisesRestUnlessPutBack() {
        val raised = raiseFor(listOf(change(14, LimitKind.DAILY, "30", "40")))
        assertEquals(LimitRaise(LimitKind.DAILY, backTo = 30), raised)
        assertEquals(GrowthCode.LIMIT_RAISED, evaluateGrowthDay(inputs(raise = raised)).code)
        assertNull(raiseFor(listOf(change(14, LimitKind.DAILY, "30", "40"), change(18, LimitKind.DAILY, "40", "30"))))
    }

    @Test
    fun hourlyRaiseOnlyCountsWhenHourlyIsEnforced() {
        assertNull(raiseFor(listOf(change(10, LimitKind.HOURLY, "20", "40")), LimitMode.DAILY))
        assertEquals(LimitKind.HOURLY, raiseFor(listOf(change(10, LimitKind.HOURLY, "20", "40")), LimitMode.BOTH)?.kind)
    }

    @Test
    fun droppingALimitRestsAddingOneDoesNot() {
        val dropped = raiseFor(listOf(change(9, LimitKind.MODE, "BOTH", "DAILY")))
        assertEquals(LimitRaise(LimitKind.MODE, backToMode = LimitMode.BOTH, droppedLimit = "hourly"), dropped)
        assertNull(raiseFor(listOf(change(9, LimitKind.MODE, "DAILY", "BOTH")), LimitMode.BOTH))
    }

    @Test
    fun modeAtReadsTheLog() {
        val changes = listOf(change(9, LimitKind.MODE, "DAILY", "BOTH"))
        assertEquals(LimitMode.DAILY, modeAt(changes, start, LimitMode.BOTH))
        assertEquals(LimitMode.BOTH, modeAt(changes, end, LimitMode.HOURLY))
        assertEquals(LimitMode.HOURLY, modeAt(emptyList(), end, LimitMode.HOURLY))
    }

    @Test
    fun pausesRestOnlyWhenLongOrRestOfToday() {
        assertEquals(GrowthCode.PAUSED, evaluateGrowthDay(inputs(restOfToday = true)).code)
        assertEquals(GrowthCode.GREW, evaluateGrowthDay(inputs(pausedMs = 119 * minute)).code)
        assertEquals(GrowthCode.PAUSED, evaluateGrowthDay(inputs(pausedMs = 120 * minute)).code)
    }

    @Test
    fun pausedMsMergesAndClipsToTheDay() {
        val log = listOf(
            PauseEntry(start + 10 * hour, start + 11 * hour, PauseReason.WORK, PauseDuration.ONE_HOUR),
            PauseEntry(start + 10 * hour + 30 * minute, start + 11 * hour + 30 * minute, PauseReason.WORK, PauseDuration.ONE_HOUR),
            PauseEntry(start - hour, start + hour, PauseReason.OTHER, PauseDuration.ONE_HOUR)
        )
        assertEquals(150 * minute, pausedMsOn(log, start, end, end))
        assertTrue(restOfTodayOn(listOf(PauseEntry(start + hour, end, PauseReason.OTHER, PauseDuration.REST_OF_TODAY)), start, end, end))
        assertFalse(restOfTodayOn(listOf(PauseEntry(start + hour, end, PauseReason.OTHER, PauseDuration.REST_OF_TODAY)), start, end, start))
    }

    @Test
    fun noDataRests() {
        assertEquals(GrowthCode.NO_DATA, evaluateGrowthDay(inputs(record = record(key, tracked = 479))).code)
        assertEquals(GrowthCode.NO_DATA, evaluateGrowthDay(inputs(record = null)).code)
        assertEquals(GrowthCode.NO_DATA, evaluateGrowthDay(inputs(outcome = null)).code)
    }

    @Test
    fun backfillAcceptsAMissingRecord() {
        val row = evaluateGrowthDay(inputs(record = null, savedMs = null, backfill = true))
        assertEquals(GrowthCode.GREW, row.code)
        assertEquals(5, row.savedPoints)
    }

    @Test
    fun overOutranksRaiseAndPause() {
        val row = evaluateGrowthDay(inputs(outcome = DayOutcome.OVER, raise = LimitRaise(LimitKind.DAILY, 30), restOfToday = true))
        assertEquals(GrowthCode.OVER, row.code)
    }

    // ---- Ledger ----

    @Test
    fun ledgerRoundTripsAndDropsUnknownCodes() {
        val rows = mapOf(
            "2026-09-28" to GrowthDay("2026-09-28", GrowthCode.GREW, 10, 7, 3, 0),
            "2026-09-29" to GrowthDay("2026-09-29", GrowthCode.OVER),
            "2026-09-30" to GrowthDay("2026-09-30", GrowthCode.PAUSED)
        )
        assertEquals("2026-09-28=G:10:7:3:0;2026-09-29=O;2026-09-30=P", serializeGrowthLedger(rows))
        assertEquals(rows, parseGrowthLedger(serializeGrowthLedger(rows)))
        assertEquals(emptyMap<String, GrowthDay>(), parseGrowthLedger("2026-09-28=X;2026-09-29=G:1:2;2026-09-30=G:-1:0:0:0;junk"))
    }

    @Test
    fun addingNeverReplacesARow() {
        val existing = mapOf(key to GrowthDay(key, GrowthCode.GREW, 10, 0, 0, 0))
        val merged = addGrowthDays(existing, listOf(GrowthDay(key, GrowthCode.OVER), GrowthDay("2026-09-29", GrowthCode.NO_DATA)))
        assertEquals(GrowthCode.GREW, merged[key]?.code)
        assertEquals(2, merged.size)
    }

    private fun keys(n: Int): List<String> {
        val cal = Calendar.getInstance().apply { timeInMillis = msOf(2025, 1, 1, 12) }
        return List(n) {
            val k = dateKeyOf(cal)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            k
        }
    }

    @Test
    fun trimmingCarriesExactlyTheDroppedPoints() {
        val all = keys(401).mapIndexed { i, k -> GrowthDay(k, GrowthCode.GREW, 10, i % 7, 0, 0) }.associateBy { it.dateKey }
        val (kept, carried) = trimLedger(all)
        assertEquals(400, kept.size)
        assertEquals(all.values.minBy { it.dateKey }.points.toLong(), carried)
        assertEquals(growthTotal(all, 0L), growthTotal(kept, carried))
    }

    @Test
    fun totalNeverDecreasesOverRandomAdds() {
        val random = Random(42)
        val pool = keys(600)
        repeat(1000) {
            var rows = emptyMap<String, GrowthDay>()
            var carried = 0L
            var last = 0L
            repeat(random.nextInt(1, 30)) {
                val batch = List(random.nextInt(1, 40)) {
                    val k = pool[random.nextInt(pool.size)]
                    val code = GrowthCode.entries[random.nextInt(GrowthCode.entries.size)]
                    if (code == GrowthCode.GREW) GrowthDay(k, code, 10, random.nextInt(21), random.nextInt(10), random.nextInt(61)) else GrowthDay(k, code)
                }
                val (kept, dropped) = trimLedger(addGrowthDays(rows, batch), keep = random.nextInt(1, 500))
                rows = kept
                carried += dropped
                val total = growthTotal(rows, carried)
                assertTrue(total >= last)
                last = total
            }
        }
    }

    @Test
    fun growingDaysToNextUsesThePace() {
        assertEquals(2, growingDaysToNext(TreeStage.SEEDLING.thresholdPoints - 30, emptyMap()))
        val fast = keys(3).associateWith { GrowthDay(it, GrowthCode.GREW, 10, 20, 0, 0) }
        assertEquals(1, growingDaysToNext(TreeStage.SEEDLING.thresholdPoints - 30, fast))
        assertNull(growingDaysToNext(TreeStage.FULL_CANOPY.thresholdPoints, fast))
    }

    @Test
    fun stageReachedOnFindsTheCrossingRow() {
        val k = keys(3)
        val rows = k.associateWith { GrowthDay(it, GrowthCode.GREW, 10, 10, 0, 0) }
        assertEquals(k[2], stageReachedOn(rows, 0, TreeStage.SEEDLING))
        assertEquals(k[0], stageReachedOn(rows, 0, TreeStage.SPROUT))
        assertNull(stageReachedOn(rows, 60, TreeStage.SEEDLING))
    }

    // ---- Task 4: today and call-outs ----

    private fun rest(
        count: Int = 10,
        lowered: Int? = null,
        raise: LimitRaise? = null,
        restOfToday: Boolean = false,
        pausedMs: Long = 0L,
        pausedNow: Boolean = false,
        trackingOn: Boolean = true
    ) = todayRest(LimitMode.DAILY, count, 30, 0, 0, lowered, raise, restOfToday, pausedMs, pausedNow, trackingOn)

    @Test
    fun todayRestPriority() {
        val raise = LimitRaise(LimitKind.DAILY, 30)
        assertEquals(RestReason.OVER_LIMIT, rest(count = 31, raise = raise, restOfToday = true, pausedNow = true, trackingOn = false))
        assertEquals(RestReason.LIMIT_RAISED, rest(raise = raise, restOfToday = true))
        assertEquals(RestReason.PAUSED_DAY, rest(restOfToday = true, pausedNow = true))
        assertEquals(RestReason.PAUSED_DAY, rest(pausedMs = 2 * hour))
        assertEquals(RestReason.PAUSED_NOW, rest(pausedNow = true, trackingOn = false))
        assertEquals(RestReason.TRACKING_OFF, rest(trackingOn = false))
        assertNull(rest())
        assertNull(rest(count = 35, lowered = 40))
    }

    private val today = "2026-09-29"
    private val yesterday = "2026-09-28"

    private fun callout(
        rest: RestReason? = null,
        rows: Map<String, GrowthDay>,
        carried: Long = 0L,
        name: String = "",
        hour: Int = 15,
        streak: Int = 0
    ): TreeCallout {
        val stage = treeStage(growthTotal(rows, carried))
        return treeCallout(rest, null, stage, rows, carried, streak, name, today, hour)
    }

    private val crossedYesterday = mapOf(
        "2026-09-27" to GrowthDay("2026-09-27", GrowthCode.GREW, 10, 0, 0, 0),
        yesterday to GrowthDay(yesterday, GrowthCode.GREW, 10, 0, 0, 15)
    )

    @Test
    fun restingBeatsANewStage() {
        assertEquals(CalloutKind.RESTING, callout(RestReason.OVER_LIMIT, crossedYesterday).kind)
        assertEquals(CalloutGlyph.MOON, callout(RestReason.OVER_LIMIT, crossedYesterday).glyph)
    }

    @Test
    fun newStageBeatsMilestone() {
        val c = callout(rows = crossedYesterday, name = "Fern")
        assertEquals(CalloutKind.NEW_STAGE, c.kind)
        assertEquals("Fern has sprouted!", c.text)
        val milestone = callout(rows = crossedYesterday, carried = 5, streak = 3)
        assertEquals(CalloutKind.MILESTONE, milestone.kind)
        assertEquals("3 days in a row! A ladybug joined your tree.", milestone.text)
    }

    @Test
    fun firstDayBackAfterRestAndOvernight() {
        assertEquals(CalloutKind.FIRST_DAY, callout(rows = emptyMap()).kind)
        assertEquals(CalloutKind.FIRST_DAY, callout(rows = mapOf(yesterday to GrowthDay(yesterday, GrowthCode.OVER))).kind)
        val rested = mapOf(yesterday to GrowthDay(yesterday, GrowthCode.OVER))
        assertEquals(CalloutKind.BACK_AFTER_REST, callout(rows = rested, carried = 120).kind)
        val grew = mapOf(yesterday to GrowthDay(yesterday, GrowthCode.GREW, 10, 0, 0, 0))
        assertEquals(CalloutKind.GREW_OVERNIGHT, callout(rows = grew, carried = 120, hour = 9).kind)
        assertEquals(CalloutKind.GROWING, callout(rows = grew, carried = 120, hour = 13).kind)
    }

    @Test
    fun raisedLimitCarriesSetBack() {
        val raise = LimitRaise(LimitKind.DAILY, backTo = 30)
        val c = treeCallout(RestReason.LIMIT_RAISED, raise, TreeStage.SEED, emptyMap(), 0, 0, "", today, 15)
        assertEquals("Your tree rests while the limit is higher. Set it to 30 to grow.", c.text)
        assertEquals(CalloutAction(raise), c.action)
        val mode = LimitRaise(LimitKind.MODE, backToMode = LimitMode.BOTH, droppedLimit = "hourly")
        assertEquals(
            "Fern rests while the hourly limit is off.",
            restingLine(RestReason.LIMIT_RAISED, mode, "Fern", today)
        )
    }

    @Test
    fun rotationIsStablePerDayAndVaries() {
        assertEquals(pick(GROWING_LINES, today), pick(GROWING_LINES, today))
        assertNotEquals(pick(OVER_LIMIT_LINES, "2026-09-29"), pick(OVER_LIMIT_LINES, "2026-09-30"))
        assertEquals(1L, epochDay("1970-01-02"))
    }

    @Test
    fun nameSubstitution() {
        assertEquals("Your tree is growing today.", fillTreeLine("{Name} is growing today.", ""))
        assertEquals("Fern is growing today.", fillTreeLine("{Name} is growing today.", "Fern"))
        assertEquals("Another growing day for your tree.", fillTreeLine("Another growing day for {name}.", "  "))
        assertEquals("Another growing day for Fern.", fillTreeLine("Another growing day for {name}.", "Fern"))
    }

    @Test
    fun noTreeLineUsesALossWord() {
        val banned = Regex(
            "\\b(die|dead|dying|wilt|shrink|lost|lose|fail|kill|sad|hungry|thirsty|miss|guilt|shame|warning|broke|ruin)",
            RegexOption.IGNORE_CASE
        )
        val lines = ALL_TREE_LINES + TreeStage.entries.flatMap { listOf(it.nowPhrase, it.note, it.label) }
        lines.forEach { assertFalse("\"$it\" uses a loss word", banned.containsMatchIn(it)) }
    }

    @Test
    fun treeStateBuildsTheWeekAndProgress() {
        val rows = mapOf(yesterday to GrowthDay(yesterday, GrowthCode.GREW, 10, 5, 0, 0), "2026-09-27" to GrowthDay("2026-09-27", GrowthCode.OVER))
        val state = treeState("Fern", rows, 0, null, null, 1, today, 9, null, mapOf(today to 2, "2026-09-20" to 5), true)
        assertEquals(TreeStage.SPROUT, state.stage)
        assertEquals(7, state.last7.size)
        assertEquals(today, state.last7.last().dateKey)
        assertTrue(state.last7.last().isToday)
        assertEquals(GrowthCode.GREW, state.last7[5].code)
        assertEquals(GrowthCode.OVER, state.last7[4].code)
        assertNull(state.last7[0].code)
        assertEquals(2, state.swapsThisWeek)
        assertEquals(1, state.grewDays)
        assertEquals(0f, state.fractionToNext!!, 0.001f)
        assertEquals(yesterday, state.lastClosedKey)
    }

    @Test
    fun pauseAndRaiseLines() {
        assertEquals("Your tree rests while paused.", pauseTreeLine("", PauseDuration.FIFTEEN_MINUTES, 0))
        assertEquals("Fern rests today (over 2 hours paused).", pauseTreeLine("Fern", PauseDuration.ONE_HOUR, 90 * minute))
        assertEquals("Fern rests for the rest of today and grows again tomorrow.", pauseTreeLine("Fern", PauseDuration.REST_OF_TODAY, 0))
        assertEquals("Your tree rests for today if you raise it.", raiseTreeLine("", null))
        assertEquals("Fern rests for today if you switch.", raiseTreeLine("Fern", RestReason.PAUSED_NOW, dropping = true))
        assertNull(raiseTreeLine("Fern", RestReason.OVER_LIMIT))
    }

    @Test
    fun nextStreakMilestoneMatchesTheBonusDays() {
        assertEquals(3, nextStreakMilestone(0))
        assertEquals(7, nextStreakMilestone(3))
        assertEquals(14, nextStreakMilestone(10))
        assertEquals(30, nextStreakMilestone(14))
        assertEquals(60, nextStreakMilestone(30))
        (0..100).forEach { assertTrue(milestoneBonus(nextStreakMilestone(it)) > 0) }
    }
}
