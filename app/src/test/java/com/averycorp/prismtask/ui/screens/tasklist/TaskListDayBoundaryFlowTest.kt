package com.averycorp.prismtask.ui.screens.tasklist

import com.averycorp.prismtask.util.DayBoundary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED-gate regression test for `TaskListViewModel`'s SoD-boundary bug
 * (`docs/audits/UTIL_DAYBOUNDARY_SWEEP_AUDIT.md` § 3).
 *
 * **Phase-1 form** — assertion encodes the bug-exists state. Reconstructs
 * the exact `dayStartFlow` shape from `TaskListViewModel.kt:291-294`:
 *
 *   `getDayStartHour().map { startOfCurrentDay(it) }.stateIn(...)`
 *
 * and asserts it locks the value at construction time. This passes today
 * — that's the bug.
 *
 * After migration to `LocalDateFlow` this file will be inverted to the
 * Phase-5 form (passing = bug fixed). The inverted assertions are the
 * regression gate against re-introduction of the snapshot pattern.
 *
 * Same shape as `TodayDayBoundaryFlowTest`'s Phase-1 form.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListDayBoundaryFlowTest {

    @Test
    fun dayStartFlow_snapshotPattern_locksValueAtConstructionTime() = runTest {
        // 11pm Apr 25 with SoD = 4am → SoD-anchored start = today (Apr 25) 4am.
        // 6h later (5am Apr 26) the SoD-anchored start would be Apr 26 4am — IF
        // the flow ticked. The bug shape doesn't tick.
        val now11pm = millisAt(2026, 4, 25, 23, 0)
        val sodStartAt11pm = DayBoundary.startOfCurrentDay(4, now11pm)
        val now5amNextDay = millisAt(2026, 4, 26, 5, 0)
        val sodStartAt5am = DayBoundary.startOfCurrentDay(4, now5amNextDay)
        // Sanity: the helper itself returns different values for the two `now`s.
        assertEquals(
            "Sanity: 11pm Apr 25 with SoD=4 yields Apr 25 04:00 as SoD-anchored start",
            millisAt(2026, 4, 25, 4, 0), sodStartAt11pm
        )
        assertEquals(
            "Sanity: 5am Apr 26 with SoD=4 yields Apr 26 04:00 as SoD-anchored start",
            millisAt(2026, 4, 26, 4, 0), sodStartAt5am
        )

        val sodHour = MutableStateFlow(4)
        val dayStartFlow: StateFlow<Long> = sodHour
            .map { hour -> DayBoundary.startOfCurrentDay(hour, now11pm) }
            .stateIn(backgroundScope, SharingStarted.Eagerly, sodStartAt11pm)

        runCurrent()
        val before = dayStartFlow.value
        assertEquals(sodStartAt11pm, before)

        // 6h wall-clock advance with no upstream re-emission. The bug shape
        // locks the value — no clock-tick refresh.
        advanceTimeBy(6 * 60 * 60 * 1000L)
        runCurrent()

        assertEquals(
            "BUG: dayStartFlow locked to construction-time value despite 6h wall-clock advance",
            before, dayStartFlow.value
        )
    }

    private fun millisAt(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        java.time.LocalDateTime.of(y, m, d, h, min)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
