package com.averykarlin.averytask.domain

import com.averykarlin.averytask.domain.usecase.NaturalLanguageParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class NaturalLanguageParserTest {

    private lateinit var parser: NaturalLanguageParser

    private fun LocalDate.toMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun Long.toLocalDate(): LocalDate =
        java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun Long.toLocalTime(): LocalTime =
        java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()

    private val today: LocalDate get() = LocalDate.now()

    @Before
    fun setup() {
        parser = NaturalLanguageParser()
    }

    // --- Basic extraction ---

    @Test
    fun test_simpleTitle() {
        val result = parser.parse("Buy milk")
        assertEquals("Buy milk", result.title)
        assertNull(result.dueDate)
        assertTrue(result.tags.isEmpty())
        assertEquals(0, result.priority)
    }

    @Test
    fun test_titleWithTag() {
        val result = parser.parse("Buy milk #groceries")
        assertEquals("Buy milk", result.title)
        assertEquals(listOf("groceries"), result.tags)
    }

    @Test
    fun test_titleWithMultipleTags() {
        val result = parser.parse("Review PR #work #urgent")
        assertEquals("Review PR", result.title)
        assertTrue(result.tags.contains("work"))
        assertTrue(result.tags.contains("urgent"))
        assertEquals(2, result.tags.size)
    }

    @Test
    fun test_titleWithProject() {
        val result = parser.parse("Fix bug @AveryTask")
        assertEquals("Fix bug", result.title)
        assertEquals("AveryTask", result.projectName)
    }

    @Test
    fun test_titleWithPriority() {
        val result = parser.parse("Call doctor !high")
        assertEquals("Call doctor", result.title)
        assertEquals(3, result.priority)
    }

    @Test
    fun test_titleWithBangShorthand() {
        val result = parser.parse("Call doctor !!")
        assertEquals("Call doctor", result.title)
        assertEquals(2, result.priority)
    }

    @Test
    fun test_titleWithEverything() {
        val result = parser.parse("Buy groceries tomorrow at 3pm #errands @home !high")
        assertEquals("Buy groceries", result.title)
        assertNotNull(result.dueDate)
        assertEquals(today.plusDays(1), result.dueDate!!.toLocalDate())
        assertNotNull(result.dueTime)
        assertEquals(LocalTime.of(15, 0), result.dueTime!!.toLocalTime())
        assertEquals(listOf("errands"), result.tags)
        assertEquals("home", result.projectName)
        assertEquals(3, result.priority)
    }

    // --- Date parsing ---

    @Test
    fun test_today() {
        val result = parser.parse("Do laundry today")
        assertEquals("Do laundry", result.title)
        assertNotNull(result.dueDate)
        assertEquals(today, result.dueDate!!.toLocalDate())
    }

    @Test
    fun test_tomorrow() {
        val result = parser.parse("Pay bills tomorrow")
        assertEquals("Pay bills", result.title)
        assertNotNull(result.dueDate)
        assertEquals(today.plusDays(1), result.dueDate!!.toLocalDate())
    }

    @Test
    fun test_tmrw() {
        val result = parser.parse("Pay bills tmrw")
        assertEquals("Pay bills", result.title)
        assertNotNull(result.dueDate)
        assertEquals(today.plusDays(1), result.dueDate!!.toLocalDate())
    }

    @Test
    fun test_nextMonday() {
        val result = parser.parse("Meeting next monday")
        assertEquals("Meeting", result.title)
        assertNotNull(result.dueDate)
        val parsedDate = result.dueDate!!.toLocalDate()
        assertEquals(DayOfWeek.MONDAY, parsedDate.dayOfWeek)
        assertTrue(parsedDate.isAfter(today))
    }

    @Test
    fun test_dayName() {
        val result = parser.parse("Meeting wednesday")
        assertEquals("Meeting", result.title)
        assertNotNull(result.dueDate)
        val parsedDate = result.dueDate!!.toLocalDate()
        assertEquals(DayOfWeek.WEDNESDAY, parsedDate.dayOfWeek)
        assertTrue(parsedDate.isAfter(today) || parsedDate.isEqual(today.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY))))
    }

    @Test
    fun test_inNDays() {
        val result = parser.parse("Review in 3 days")
        assertEquals("Review", result.title)
        assertNotNull(result.dueDate)
        assertEquals(today.plusDays(3), result.dueDate!!.toLocalDate())
    }

    @Test
    fun test_inNWeeks() {
        val result = parser.parse("Deploy in 2 weeks")
        assertEquals("Deploy", result.title)
        assertNotNull(result.dueDate)
        assertEquals(today.plusWeeks(2), result.dueDate!!.toLocalDate())
    }

    @Test
    fun test_absoluteDate() {
        val result = parser.parse("Deadline jan 15")
        assertEquals("Deadline", result.title)
        assertNotNull(result.dueDate)
        val parsedDate = result.dueDate!!.toLocalDate()
        assertEquals(1, parsedDate.monthValue)
        assertEquals(15, parsedDate.dayOfMonth)
        assertTrue(parsedDate >= today)
    }

    @Test
    fun test_slashDate() {
        val result = parser.parse("Due 5/20")
        assertEquals("Due", result.title)
        assertNotNull(result.dueDate)
        val parsedDate = result.dueDate!!.toLocalDate()
        assertEquals(5, parsedDate.monthValue)
        assertEquals(20, parsedDate.dayOfMonth)
    }

    // --- Time parsing ---

    @Test
    fun test_atTime() {
        val result = parser.parse("Call at 3pm")
        assertEquals("Call", result.title)
        assertNotNull(result.dueTime)
        assertEquals(LocalTime.of(15, 0), result.dueTime!!.toLocalTime())
        // Time without date should default to today
        assertNotNull(result.dueDate)
        assertEquals(today, result.dueDate!!.toLocalDate())
    }

    @Test
    fun test_at24hr() {
        val result = parser.parse("Deploy at 15:00")
        assertEquals("Deploy", result.title)
        assertNotNull(result.dueTime)
        assertEquals(LocalTime.of(15, 0), result.dueTime!!.toLocalTime())
    }

    @Test
    fun test_atTimeWithMinutes() {
        val result = parser.parse("Meeting at 2:30pm")
        assertEquals("Meeting", result.title)
        assertNotNull(result.dueTime)
        assertEquals(LocalTime.of(14, 30), result.dueTime!!.toLocalTime())
    }

    @Test
    fun test_noon() {
        val result = parser.parse("Lunch at noon")
        assertEquals("Lunch", result.title)
        assertNotNull(result.dueTime)
        assertEquals(LocalTime.of(12, 0), result.dueTime!!.toLocalTime())
    }

    @Test
    fun test_midnight() {
        val result = parser.parse("Deploy at midnight")
        assertEquals("Deploy", result.title)
        assertNotNull(result.dueTime)
        assertEquals(LocalTime.of(0, 0), result.dueTime!!.toLocalTime())
    }

    // --- Recurrence ---

    @Test
    fun test_daily() {
        val result = parser.parse("Meditate every day")
        assertEquals("Meditate", result.title)
        assertEquals("daily", result.recurrenceHint)
    }

    @Test
    fun test_weekly() {
        val result = parser.parse("Team sync weekly")
        assertEquals("Team sync", result.title)
        assertEquals("weekly", result.recurrenceHint)
    }

    // --- Edge cases ---

    @Test
    fun test_emptyInput() {
        val result = parser.parse("")
        assertEquals("", result.title)
        assertNull(result.dueDate)
        assertTrue(result.tags.isEmpty())
    }

    @Test
    fun test_onlyTags() {
        val result = parser.parse("#work #urgent")
        assertTrue(result.tags.contains("work"))
        assertTrue(result.tags.contains("urgent"))
    }

    @Test
    fun test_hashInMiddle() {
        // "C#" has # immediately after a letter — should not parse as tag
        val result = parser.parse("C# programming")
        assertEquals("C# programming", result.title)
        assertFalse(result.tags.contains(""))
    }

    @Test
    fun test_emailNotProject() {
        val result = parser.parse("Email john@gmail.com")
        // @ in email (preceded by alphanumeric) should not be parsed as project
        assertNull(result.projectName)
        assertTrue(result.title.contains("john@gmail.com"))
    }

    // --- Priority variants ---

    @Test
    fun test_urgentPriority() {
        val result = parser.parse("Fix now !urgent")
        assertEquals(4, result.priority)
    }

    @Test
    fun test_numberedPriority() {
        val result = parser.parse("Task !3")
        assertEquals(3, result.priority)
    }

    @Test
    fun test_fourBangs() {
        val result = parser.parse("Task !!!!")
        assertEquals(4, result.priority)
    }

    @Test
    fun test_lowPriority() {
        val result = parser.parse("Someday !low")
        assertEquals(1, result.priority)
    }

    // --- Combined complex inputs ---

    @Test
    fun test_complexInput() {
        val result = parser.parse("Deploy v2.0 in 2 weeks !urgent #release @backend")
        assertEquals("Deploy v2.0", result.title)
        assertEquals(today.plusWeeks(2), result.dueDate!!.toLocalDate())
        assertEquals(4, result.priority)
        assertTrue(result.tags.contains("release"))
        assertEquals("backend", result.projectName)
    }

    @Test
    fun test_titleWithNumber() {
        val result = parser.parse("Read chapter 5 !low #reading")
        assertEquals("Read chapter 5", result.title)
        assertEquals(1, result.priority)
        assertTrue(result.tags.contains("reading"))
    }

    @Test
    fun test_colonAndCommasPreserved() {
        val result = parser.parse("Groceries: eggs, milk, bread #errands")
        assertTrue(result.title.contains("eggs, milk, bread"))
        assertTrue(result.tags.contains("errands"))
    }

    @Test
    fun test_whitespaceOnly() {
        val result = parser.parse("   ")
        assertEquals("", result.title)
    }

    @Test
    fun test_monthly() {
        val result = parser.parse("Pay rent monthly")
        assertEquals("Pay rent", result.title)
        assertEquals("monthly", result.recurrenceHint)
    }
}
