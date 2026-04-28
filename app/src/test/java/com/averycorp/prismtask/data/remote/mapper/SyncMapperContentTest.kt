package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.AssignmentEntity
import com.averycorp.prismtask.data.local.entity.AttachmentEntity
import com.averycorp.prismtask.data.local.entity.CheckInLogEntity
import com.averycorp.prismtask.data.local.entity.DailyEssentialSlotCompletionEntity
import com.averycorp.prismtask.data.local.entity.FocusReleaseLogEntity
import com.averycorp.prismtask.data.local.entity.MedicationRefillEntity
import com.averycorp.prismtask.data.local.entity.MoodEnergyLogEntity
import com.averycorp.prismtask.data.local.entity.StudyLogEntity
import com.averycorp.prismtask.data.local.entity.TaskTimingEntity
import com.averycorp.prismtask.data.local.entity.WeeklyReviewEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip tests for the v1.4.38 content-entity sync mappers — one
 * per new collection: check_in_logs, mood_energy_logs, focus_release_logs,
 * medication_refills, weekly_reviews, daily_essential_slot_completions,
 * assignments, attachments, study_logs.
 *
 * For FK-bearing entities (focus_release_log, assignment, attachment,
 * study_log), the tests supply cloud-id strings on push and matching
 * local ids on pull, then assert that the FK columns round-trip via
 * those caller-supplied bindings.
 */
class SyncMapperContentTest {

    @Test
    fun checkInLog_roundTripsAllFields() {
        val source = CheckInLogEntity(
            id = 1,
            date = 1_700_000_000_000L,
            stepsCompletedCsv = "WELCOME,BALANCE,MEDS,TASKS,HABITS",
            medicationsConfirmed = 2,
            tasksReviewed = 5,
            habitsCompleted = 3,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.checkInLogToMap(source)
        val decoded = SyncMapper.mapToCheckInLog(map, localId = source.id, cloudId = "c1")
        assertEquals("c1", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c1"), decoded)
    }

    @Test
    fun moodEnergyLog_roundTripsAllFields() {
        val source = MoodEnergyLogEntity(
            id = 2,
            date = 1_700_000_000_000L,
            mood = 4,
            energy = 5,
            notes = "Great sleep",
            timeOfDay = "morning",
            createdAt = 10L,
            updatedAt = 20L
        )
        val map = SyncMapper.moodEnergyLogToMap(source)
        val decoded = SyncMapper.mapToMoodEnergyLog(map, localId = source.id, cloudId = "c2")
        assertEquals("c2", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c2"), decoded)
    }

    @Test
    fun focusReleaseLog_roundTripsWithTaskFkResolution() {
        // taskId = 42 is the local task row id; the push side resolves it
        // to a cloud id via syncMetadataDao.
        val source = FocusReleaseLogEntity(
            id = 3,
            eventType = "stuck_detected",
            taskId = 42,
            context = "today_screen",
            createdAt = 100L,
            updatedAt = 200L
        )
        // Push: caller resolves taskId(42) → "task-cloud-xyz".
        val map = SyncMapper.focusReleaseLogToMap(source, taskCloudId = "task-cloud-xyz")
        assertEquals("task-cloud-xyz", map["taskId"])
        // Pull: caller resolves "task-cloud-xyz" → 42 locally.
        val decoded = SyncMapper.mapToFocusReleaseLog(
            map,
            localId = source.id,
            taskLocalId = 42,
            cloudId = "c3"
        )
        assertEquals("c3", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c3"), decoded)
    }

    @Test
    fun focusReleaseLog_roundTripsWhenTaskFkIsNull() {
        val source = FocusReleaseLogEntity(
            id = 4,
            eventType = "celebration_fired",
            taskId = null,
            context = null,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.focusReleaseLogToMap(source, taskCloudId = null)
        assertEquals(null, map["taskId"])
        val decoded = SyncMapper.mapToFocusReleaseLog(map, source.id, taskLocalId = null, "c4")
        assertEquals(source.copy(cloudId = "c4"), decoded)
    }

    @Test
    fun medicationRefill_roundTripsAllFields() {
        val source = MedicationRefillEntity(
            id = 5,
            medicationName = "Adderall XR",
            pillCount = 24,
            pillsPerDose = 1,
            dosesPerDay = 1,
            lastRefillDate = 1_700_000_000_000L,
            pharmacyName = "CVS",
            pharmacyPhone = "+1-555-0100",
            reminderDaysBefore = 5,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.medicationRefillToMap(source)
        val decoded = SyncMapper.mapToMedicationRefill(map, localId = source.id, cloudId = "c5")
        assertEquals("c5", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c5"), decoded)
    }

    @Test
    fun weeklyReview_roundTripsAllFields() {
        val source = WeeklyReviewEntity(
            id = 6,
            weekStartDate = 1_700_000_000_000L,
            metricsJson = """{"tasksDone":27,"habitStreakDays":14}""",
            aiInsightsJson = """{"summary":"Great week","suggestions":[]}""",
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.weeklyReviewToMap(source)
        val decoded = SyncMapper.mapToWeeklyReview(map, localId = source.id, cloudId = "c6")
        assertEquals("c6", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c6"), decoded)
    }

    @Test
    fun dailyEssentialSlotCompletion_roundTripsAllFields() {
        val source = DailyEssentialSlotCompletionEntity(
            id = 7,
            date = 1_700_000_000_000L,
            slotKey = "09:00",
            medIdsJson = """["specific_time:adderall","specific_time:lipitor"]""",
            takenAt = 1_700_000_100_000L,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.dailyEssentialSlotCompletionToMap(source)
        val decoded = SyncMapper.mapToDailyEssentialSlotCompletion(map, source.id, "c7")
        assertEquals("c7", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c7"), decoded)
    }

    @Test
    fun assignment_roundTripsWithCourseFkResolution() {
        // courseId = 101 is the local course row id.
        val source = AssignmentEntity(
            id = 8,
            courseId = 101,
            title = "Essay draft",
            dueDate = 1_700_000_000_000L,
            completed = false,
            completedAt = null,
            notes = "Double-spaced, 5 pages",
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.assignmentToMap(source, courseCloudId = "course-cloud-abc")
        assertEquals("course-cloud-abc", map["courseId"])
        val decoded = SyncMapper.mapToAssignment(
            map,
            localId = source.id,
            courseLocalId = 101,
            cloudId = "c8"
        )
        assertEquals("c8", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c8"), decoded)
    }

    @Test
    fun attachment_roundTripsWithTaskFkResolution() {
        val source = AttachmentEntity(
            id = 9,
            taskId = 7,
            type = "link",
            uri = "https://example.com/doc",
            fileName = "doc",
            thumbnailUri = null,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.attachmentToMap(source, taskCloudId = "task-cloud-7")
        assertEquals("task-cloud-7", map["taskId"])
        val decoded = SyncMapper.mapToAttachment(
            map,
            localId = source.id,
            taskLocalId = 7,
            cloudId = "c9"
        )
        assertEquals("c9", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c9"), decoded)
    }

    @Test
    fun studyLog_roundTripsWithDualFkResolution() {
        val source = StudyLogEntity(
            id = 10,
            date = 1_700_000_000_000L,
            coursePick = 101,
            studyDone = true,
            assignmentPick = 202,
            assignmentDone = false,
            startedAt = 1_700_000_100_000L,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.studyLogToMap(
            source,
            coursePickCloudId = "course-cloud-abc",
            assignmentPickCloudId = "asgn-cloud-xyz"
        )
        assertEquals("course-cloud-abc", map["coursePick"])
        assertEquals("asgn-cloud-xyz", map["assignmentPick"])

        val decoded = SyncMapper.mapToStudyLog(
            map,
            localId = source.id,
            coursePickLocalId = 101,
            assignmentPickLocalId = 202,
            cloudId = "c10"
        )
        assertEquals("c10", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c10"), decoded)
    }

    @Test
    fun studyLog_nullFksRoundTripCleanly() {
        val source = StudyLogEntity(
            id = 11,
            date = 1_700_000_000_000L,
            coursePick = null,
            studyDone = false,
            assignmentPick = null,
            assignmentDone = false,
            startedAt = null,
            createdAt = 1L,
            updatedAt = 2L
        )
        val map = SyncMapper.studyLogToMap(source, coursePickCloudId = null, assignmentPickCloudId = null)
        assertEquals(null, map["coursePick"])
        assertEquals(null, map["assignmentPick"])
        val decoded = SyncMapper.mapToStudyLog(
            map,
            localId = source.id,
            coursePickLocalId = null,
            assignmentPickLocalId = null,
            cloudId = "c11"
        )
        assertEquals(source.copy(cloudId = "c11"), decoded)
    }

    @Test
    fun taskTiming_roundTripsWithTaskFkResolution() {
        // taskId = 42 is the local task row id; the push side resolves it
        // to a cloud id via syncMetadataDao.
        val source = TaskTimingEntity(
            id = 12,
            taskId = 42,
            startedAt = 1_700_000_000_000L,
            endedAt = 1_700_000_900_000L,
            durationMinutes = 15,
            source = TaskTimingEntity.SOURCE_MANUAL,
            notes = "Reviewed PR comments",
            createdAt = 1_700_000_950_000L
        )
        val map = SyncMapper.taskTimingToMap(source, taskCloudId = "task-cloud-xyz")
        assertEquals("task-cloud-xyz", map["taskId"])
        assertEquals(15, map["durationMinutes"])
        assertEquals("manual", map["source"])

        val decoded = SyncMapper.mapToTaskTiming(
            map,
            localId = source.id,
            taskLocalId = 42,
            cloudId = "c12"
        )
        assertEquals("c12", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c12"), decoded)
    }

    @Test
    fun taskTiming_pomodoroSourceRoundTrips() {
        val source = TaskTimingEntity(
            id = 13,
            taskId = 7,
            startedAt = null,
            endedAt = null,
            durationMinutes = 25,
            source = TaskTimingEntity.SOURCE_POMODORO,
            notes = null,
            createdAt = 1_700_000_000_000L
        )
        val map = SyncMapper.taskTimingToMap(source, taskCloudId = "task-cloud-7")
        assertEquals("pomodoro", map["source"])
        assertEquals(null, map["startedAt"])

        val decoded = SyncMapper.mapToTaskTiming(map, localId = source.id, taskLocalId = 7, cloudId = "c13")
        assertEquals(source.copy(cloudId = "c13"), decoded)
    }

    @Test
    fun taskTiming_unknownSourceFallsBackToManual() {
        // Defensive: a future client could write an unknown source string;
        // current clients treat the field as free-form. Pull-side guard:
        // when "source" is missing entirely, decoder falls back to "manual".
        val map = mapOf<String, Any?>(
            "localId" to 14L,
            "taskId" to "task-cloud-xyz",
            "startedAt" to null,
            "endedAt" to null,
            "durationMinutes" to 30,
            // "source" intentionally omitted
            "notes" to null,
            "createdAt" to 1_700_000_000_000L
        )
        val decoded = SyncMapper.mapToTaskTiming(map, localId = 14, taskLocalId = 42, cloudId = "c14")
        assertEquals(TaskTimingEntity.SOURCE_MANUAL, decoded.source)
        assertEquals(30, decoded.durationMinutes)
    }
}
