package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInsightTest {

    private val ig = TargetApps.INSTAGRAM
    private val yt = TargetApps.YOUTUBE
    private val labels = mapOf(ig to "Instagram", yt to "YouTube")

    private fun week(igMinutes: Int, ytMinutes: Int, days: Int = 7) =
        List(days) { DailyRecord("2026-01-%02d".format(it + 1), mapOf(ig to AppDay(igMinutes, 0), yt to AppDay(ytMinutes, 0)), 960) }

    private fun choose(
        last7: List<DailyRecord>,
        prev7: List<DailyRecord> = emptyList(),
        baseline: Map<String, Double> = mapOf(ig to 60.0, yt to 40.0),
        gateOn: (String) -> Boolean = { true },
        hasSaved: Boolean = true,
        nickname: String = ""
    ) = chooseInsight(hasSaved, last7, prev7, baseline, gateOn, usesDailyLimit = true, nickname = nickname, appLabel = { labels.getValue(it) })

    @Test
    fun settlingWithoutSavedFiguresOrThreeDays() {
        assertEquals(InsightKind.SETTLING, choose(week(60, 40), hasSaved = false).kind)
        assertEquals(InsightKind.SETTLING, choose(week(60, 40, days = 2)).kind)
    }

    @Test
    fun oneAppDrivingTheIncreaseBeatsTheGeneralRule() {
        // Spec: +20% this week, YouTube 80% of the increase → insight #2, not #4.
        val insight = choose(last7 = week(64, 56), prev7 = week(60, 40), gateOn = { it != yt })
        assertEquals(InsightKind.APP_DRIVEN, insight.kind)
        assertEquals(InsightAction.TURN_ON_GATE, insight.action)
        assertEquals(yt, insight.actionApp)
        assertEquals("Turn on for YouTube", insight.actionLabel)
    }

    @Test
    fun anAppAlreadyPausingIsNotOfferedAgain() {
        val insight = choose(last7 = week(64, 56), prev7 = week(60, 40))
        assertEquals(InsightKind.HEAVIER, insight.kind)
        assertEquals("This week's been a bit heavier (about +20%). No stress — one 2-minute swap can reset the day.", insight.text)
    }

    @Test
    fun aBigJumpOffersALowerLimit() {
        // Spec: 35% above the previous week → insight #3 with "Try 10 fewer reels".
        val insight = choose(last7 = week(81, 54), prev7 = week(60, 40))
        assertEquals(InsightKind.CREPT_UP, insight.kind)
        assertEquals(InsightAction.LOWER_LIMIT, insight.action)
        assertEquals("Try 10 fewer reels", insight.actionLabel)
        assertTrue(insight.text.contains("about +35%"))
    }

    @Test
    fun cutsAgainstTheBaselineAreCelebratedWithRounding() {
        // Spec: 22% under baseline → insight #6, "about 20% less".
        val insight = choose(last7 = week(47, 31), baseline = mapOf(ig to 60.0, yt to 40.0))
        assertEquals(InsightKind.CUT, insight.kind)
        assertEquals("You're scrolling about 20% less than when you started. Good job.", insight.text)
        assertNull(insight.actionLabel)
    }

    @Test
    fun aBigCutUsesTheNicknameOnce() {
        val insight = choose(last7 = week(40, 25), nickname = "Gobi")
        assertEquals(InsightKind.CUT_BIG, insight.kind)
        assertEquals("You've cut your scrolling by about 35%. That's a real change — nice work, Gobi.", insight.text)
    }

    @Test
    fun smallChangesHoldSteady() {
        // +12% but only +6 min a day: under the 10-minute floor.
        val insight = choose(last7 = week(34, 22), prev7 = week(30, 20), baseline = mapOf(ig to 36.0, yt to 20.0))
        assertEquals(InsightKind.STEADY, insight.kind)
        assertEquals(InsightAction.START_SWAP, insight.action)
    }

    @Test
    fun increasesNeverUseAlarmingWords() {
        val texts = listOf(
            choose(last7 = week(81, 54), prev7 = week(60, 40)),
            choose(last7 = week(64, 56), prev7 = week(60, 40))
        ).map { it.text.lowercase() }
        texts.forEach { text -> listOf("warning", "fail", "bad", "too much").forEach { assertTrue(it !in text) } }
    }
}
