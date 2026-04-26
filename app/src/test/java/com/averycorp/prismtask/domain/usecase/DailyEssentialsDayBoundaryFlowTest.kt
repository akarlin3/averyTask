package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.util.DayBoundary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED-gate regression test for `DailyEssentialsUseCase`'s SoD-boundary
 * bug (`docs/audits/UTIL_DAYBOUNDARY_SWEEP_AUDIT.md` § 5).
 *
 * **Phase-1 form** — assertion encodes the bug-exists state. Reconstructs
 * the snapshot pattern from `observeToday()`'s flatMapLatest:
 *
 *   `getDayStartHour().flatMapLatest { hour ->`
 *   `    val todayStart = startOfCurrentDay(hour)        // SNAPSHOT`
 *   `    val todayLocal = currentLocalDateString(hour)   // SNAPSHOT`
 *   `    val windowStart = calendarMidnightOfCurrentDay(hour) // SNAPSHOT`
 *   `    val windowEnd = calendarMidnightOfNextDay(hour)  // SNAPSHOT`
 *   `    combine(...) { ... uses snapshots ... }`
 *   `}`
 *
 * Asserts the four locals are captured ONCE per `getDayStartHour()`
 * emission and don't refresh on wall-clock advance — that's the bug.
 *
 * After migration, this file is inverted to assert the bug-fixed
 * contract via `LocalDateFlow.observe(...)` driving re-emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyEssentialsDayBoundaryFlowTest {

    @Test
    fun observeToday_snapshotPattern_locksFourLocalsAcrossWallClockAdvance() = runTest {
        val now11pm = millisAt(2026, 4, 25, 23, 0)
        val now5amNextDay = millisAt(2026, 4, 26, 5, 0)

        // Sanity: the helpers themselves return different values for the two `now`s.
        val sodStartAt11pm = DayBoundary.startOfCurrentDay(4, now11pm)
        val sodStartAt5am = DayBoundary.startOfCurrentDay(4, now5amNextDay)
        assertEquals(
            "Sanity: SoD-anchored start differs across the boundary",
            millisAt(2026, 4, 25, 4, 0), sodStartAt11pm
        )
        assertEquals(millisAt(2026, 4, 26, 4, 0), sodStartAt5am)

        // Reconstruct the EXACT flatMapLatest shape from
        // DailyEssentialsUseCase.observeToday(). The four locals get
        // snapshotted once per upstream emission of getDayStartHour().
        // We pin the upstream to a single emission so the inner `combine`
        // sees the same locals forever.
        val sodHour = MutableStateFlow(4)
        var localsCapturedTodayStart: Long = -1L
        val composed: StateFlow<Long> = sodHour.flatMapLatest { hour ->
            // Snapshots — locked to the moment of this lambda execution.
            val todayStart = DayBoundary.startOfCurrentDay(hour, now11pm)
            localsCapturedTodayStart = todayStart
            // The combine downstream uses the snapshot — re-emits when its
            // sources emit, but always with the same stale `todayStart`.
            combine(flowOf(Unit)) { _ -> todayStart }
        }.stateIn(backgroundScope, SharingStarted.Eagerly, -1L)

        runCurrent()
        val before = composed.value
        assertEquals(sodStartAt11pm, before)

        // 6h advance — past SoD. Snapshot is locked, no re-fire.
        advanceTimeBy(6 * 60 * 60 * 1000L)
        runCurrent()

        assertEquals(
            "BUG: snapshot pattern locks the SoD-anchored start across wall-clock advance — " +
                "even though the helper would return a different value if called now",
            sodStartAt11pm, composed.value
        )
        assertEquals(
            "Sanity: locals never recaptured (lambda only fires on upstream emission)",
            sodStartAt11pm, localsCapturedTodayStart
        )
    }

    private fun millisAt(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        java.time.LocalDateTime.of(y, m, d, h, min)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
