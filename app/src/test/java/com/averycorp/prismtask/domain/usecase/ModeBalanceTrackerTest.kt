package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.TaskMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ModeBalanceTrackerTest {
    private val tracker = ModeBalanceTracker()
    private val utc = TimeZone.getTimeZone("UTC")

    // 2026-04-11 00:00 UTC
    private val now = 1_775_779_200_000L
    private val oneDay = 24L * 60 * 60 * 1000

    private fun task(
        id: Long,
        mode: TaskMode?,
        dueDate: Long = now - oneDay
    ): TaskEntity = TaskEntity(
        id = id,
        title = "task $id",
        dueDate = dueDate,
        createdAt = dueDate,
        updatedAt = dueDate,
        taskMode = mode?.name
    )

    @Test
    fun `empty list produces empty mode balance`() {
        val state = tracker.compute(emptyList(), now = now, timeZone = utc)
        assertEquals(0, state.totalTracked)
        assertEquals(TaskMode.UNCATEGORIZED, state.dominantMode)
    }

    @Test
    fun `uncategorized tasks are excluded from counts`() {
        val tasks = listOf(
            task(1, null),
            task(2, null),
            task(3, TaskMode.RELAX)
        )
        val state = tracker.compute(tasks, now = now, timeZone = utc)
        assertEquals(1, state.totalTracked)
        assertEquals(TaskMode.RELAX, state.dominantMode)
    }

    @Test
    fun `ratios normalize to one across the three tracked modes`() {
        val tasks = listOf(
            task(1, TaskMode.WORK),
            task(2, TaskMode.WORK),
            task(3, TaskMode.PLAY),
            task(4, TaskMode.RELAX)
        )
        val state = tracker.compute(tasks, now = now, timeZone = utc)
        assertEquals(0.5f, state.currentRatios[TaskMode.WORK]!!, 0.001f)
        assertEquals(0.25f, state.currentRatios[TaskMode.PLAY]!!, 0.001f)
        assertEquals(0.25f, state.currentRatios[TaskMode.RELAX]!!, 0.001f)
        assertEquals(TaskMode.WORK, state.dominantMode)
    }

    @Test
    fun `tasks outside the 7 day window are excluded from current ratios`() {
        val tasks = listOf(
            task(1, TaskMode.WORK, dueDate = now - 30 * oneDay),
            task(2, TaskMode.PLAY, dueDate = now - 2 * oneDay)
        )
        val state = tracker.compute(tasks, now = now, timeZone = utc)
        assertEquals(1, state.totalTracked)
        assertEquals(1f, state.currentRatios[TaskMode.PLAY]!!, 0.001f)
    }

    @Test
    fun `rolling ratios include older tasks up to 28 days`() {
        val tasks = listOf(
            task(1, TaskMode.WORK, dueDate = now - 20 * oneDay),
            task(2, TaskMode.RELAX, dueDate = now - 2 * oneDay)
        )
        val state = tracker.compute(tasks, now = now, timeZone = utc)
        assertEquals(0.5f, state.rollingRatios[TaskMode.WORK]!!, 0.001f)
        assertEquals(0.5f, state.rollingRatios[TaskMode.RELAX]!!, 0.001f)
    }

    @Test
    fun `4AM SoD includes tasks on the logical previous day before midnight`() {
        // now = 2026-04-11 02:30 UTC, before 4 AM SoD → logical day = 2026-04-10.
        // 7-day window cutoff = 2026-04-04 04:00 UTC.
        val nowAt0230 = now + 2L * 3600 * 1000 + 30L * 60 * 1000
        val taskAt0500 = now - 7 * oneDay + 5L * 3600 * 1000
        val taskAt0300 = now - 7 * oneDay + 3L * 3600 * 1000
        val tasks = listOf(
            task(1, TaskMode.PLAY, dueDate = taskAt0500),
            task(2, TaskMode.WORK, dueDate = taskAt0300)
        )

        val sodState = tracker.compute(
            tasks,
            now = nowAt0230,
            timeZone = utc,
            dayStartHour = 4
        )
        assertEquals(1, sodState.totalTracked)
        assertEquals(1f, sodState.currentRatios[TaskMode.PLAY]!!, 0.001f)

        val midnightState = tracker.compute(tasks, now = nowAt0230, timeZone = utc)
        assertEquals(0, midnightState.totalTracked)
    }

    @Test
    fun `config isValid true when sums to 1`() {
        assertEquals(true, ModeBalanceConfig().isValid())
        assertEquals(true, ModeBalanceConfig(0.5f, 0.3f, 0.2f).isValid())
    }

    @Test
    fun `config isValid false when sum is wrong`() {
        assertEquals(false, ModeBalanceConfig(0.5f, 0.5f, 0.5f).isValid())
    }
}
