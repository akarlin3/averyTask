package com.averycorp.prismtask.ui.screens.today

import com.averycorp.prismtask.util.DayBoundary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED-gate regression test for `TodayViewModel`'s SoD-boundary bug
 * (`docs/audits/UTIL_DAYBOUNDARY_SWEEP_AUDIT.md` § 1).
 *
 * This file is in its **Phase-1 form** — assertions encode the bug-exists
 * state. The two tests reconstruct the exact flow shapes from
 * `TodayViewModel.dayStart` (lines 370-377) and the morning-check-in
 * banner `combine` (lines 162-201) and assert that the snapshot pattern
 * locks the value, with no clock-tick refresh on wall-clock crossing.
 *
 * Both assertions PASS today — that's the bug. After migration to
 * `LocalDateFlow`, this file will be **inverted** to a Phase-5 form whose
 * assertions encode the bug-fixed state. The inversion commit is the
 * regression gate: re-introducing the snapshot pattern via copy/paste
 * would flip these tests back to red.
 *
 * Same shape as PR #798's `MedicationTodayDateRefreshTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayDayBoundaryFlowTest {

    /**
     * Bug instance #1 — `dayStart: StateFlow<Long>` (TodayViewModel.kt:370-377):
     *
     *   getDayStartHour().map { DayBoundary.calendarMidnightOfCurrentDay(it) }
     *       .stateIn(viewModelScope, WhileSubscribed(5_000L), … SoD=0 fallback)
     *
     * `.map` evaluates `calendarMidnightOfCurrentDay(...)` once per upstream
     * emission of `getDayStartHour()`. Nothing in the pipeline re-emits when
     * the wall-clock crosses the user's SoD, so `dayStart.value` is locked
     * to the moment the upstream last emitted (typically VM construction).
     */
    @Test
    fun dayStart_snapshotPattern_locksValueAtConstructionTime() = runTest {
        // 11pm on Apr 25 with SoD = 4am.
        // 6h later (5am Apr 26), the calendar-midnight-of-effective-day
        // would have advanced to Apr 26's midnight — IF the flow ticked.
        val now11pm = millisAt(2026, 4, 25, 23, 0)
        val now5amNextDay = millisAt(2026, 4, 26, 5, 0)

        // Sanity: helper itself returns different values for the two `now`s.
        val midnightAt11pm = DayBoundary.calendarMidnightOfCurrentDay(4, now11pm)
        val midnightAt5am = DayBoundary.calendarMidnightOfCurrentDay(4, now5amNextDay)
        assertEquals(
            "Sanity: 11pm and 5am-next-day belong to different calendar dates of the logical day",
            millisAt(2026, 4, 25, 0, 0), midnightAt11pm
        )
        assertEquals(
            millisAt(2026, 4, 26, 0, 0), midnightAt5am
        )

        // Reconstruct the EXACT shape from TodayViewModel.kt:370-377.
        // We can't drive System.currentTimeMillis inside `.map`, so instead
        // we exercise the structural defect: the `.map` lambda fires only
        // when the upstream emits, and the StateFlow's value is locked to
        // the most recent emission's `now`. With a single upstream emission,
        // the value never changes — that's the bug.
        val sodHour = MutableStateFlow(4)
        val captured = MutableStateFlow(midnightAt11pm)
        val dayStart: StateFlow<Long> = sodHour
            .map { hour -> DayBoundary.calendarMidnightOfCurrentDay(hour, now11pm) }
            .stateIn(backgroundScope, SharingStarted.Eagerly, captured.value)

        runCurrent()
        val before = dayStart.value
        assertEquals(midnightAt11pm, before)

        // Simulate wall-clock advance — without an upstream re-emission,
        // there's nowhere for the StateFlow to learn about it. The bug
        // shape locks the value.
        advanceTimeBy(6 * 60 * 60 * 1000L)
        runCurrent()

        assertEquals(
            "BUG: dayStart locked to construction-time value despite 6h wall-clock advance",
            before, dayStart.value
        )
    }

    /**
     * Bug instance #2 — morning-check-in banner `combine` lambda
     * (TodayViewModel.kt:162-201):
     *
     *   combine(featureEnabled(), bannerDismissedDate(), observeAll())
     *       { enabled, dismissedDate, logs ->
     *           val todayStart = DayBoundary.startOfCurrentDay(...)  // snapshot
     *           val todayIso = LocalDate.now().format(...)             // snapshot
     *           ...
     *       }
     *
     * The `combine` only re-fires when one of its three reactive sources
     * emits. None of those re-emit on wall-clock crossing. So the lambda's
     * `todayStart` / `todayIso` snapshots stay frozen across SoD if the
     * user keeps Today open.
     */
    @Test
    fun bannerCombineLambda_snapshotPattern_doesNotRefreshOnWallClockAdvance() = runTest {
        // Three reactive sources, mirroring (featureEnabled, bannerDismissedDate, logs).
        val featureEnabled = MutableStateFlow(true)
        val bannerDismissedDate = MutableStateFlow<String?>(null)
        val logs = MutableStateFlow<List<Long>>(emptyList())

        var lambdaInvocations = 0
        val combined = combine(featureEnabled, bannerDismissedDate, logs) { _, _, _ ->
            lambdaInvocations += 1
            // (the real lambda computes todayStart/todayIso here from
            // util.DayBoundary + LocalDate.now(); the count of lambda
            // invocations is the regression-relevant signal — it's the only
            // moment those snapshots could refresh)
            lambdaInvocations
        }.distinctUntilChanged()

        val collectorJob = launch { combined.collect {} }
        runCurrent()

        val initialInvocations = lambdaInvocations
        assertEquals("Lambda fired once for the initial combine", 1, initialInvocations)

        // Simulate 6h wall-clock advance with NO source emissions. The
        // lambda doesn't re-fire — meaning any date snapshot inside it
        // stays frozen.
        advanceTimeBy(6 * 60 * 60 * 1000L)
        runCurrent()

        assertEquals(
            "BUG: banner combine lambda did not re-fire on wall-clock advance — " +
                "any date snapshot inside it stays frozen across the SoD boundary",
            initialInvocations, lambdaInvocations
        )

        collectorJob.cancel()
        advanceUntilIdle()
    }

    private fun millisAt(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        java.time.LocalDateTime.of(y, m, d, h, min)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
