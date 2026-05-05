package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.domain.model.LifeCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class LifeCategoryClassifierTest {
    private val classifier = LifeCategoryClassifier()

    @Test
    fun `work keywords classify as WORK`() {
        assertEquals(LifeCategory.WORK, classifier.classify("Prepare standup report"))
        assertEquals(LifeCategory.WORK, classifier.classify("Client meeting at 3pm"))
        assertEquals(LifeCategory.WORK, classifier.classify("Review PR for sprint"))
    }

    @Test
    fun `personal keywords classify as PERSONAL`() {
        assertEquals(LifeCategory.PERSONAL, classifier.classify("Grocery shopping"))
        assertEquals(LifeCategory.PERSONAL, classifier.classify("Pay rent"))
        assertEquals(LifeCategory.PERSONAL, classifier.classify("Laundry"))
    }

    @Test
    fun `self care keywords classify as SELF_CARE`() {
        assertEquals(LifeCategory.SELF_CARE, classifier.classify("Morning yoga"))
        assertEquals(LifeCategory.SELF_CARE, classifier.classify("Meditate for 10 minutes"))
        assertEquals(LifeCategory.SELF_CARE, classifier.classify("Go for a walk"))
    }

    @Test
    fun `health keywords classify as HEALTH`() {
        assertEquals(LifeCategory.HEALTH, classifier.classify("Refill prescription"))
        assertEquals(LifeCategory.HEALTH, classifier.classify("Dentist appointment"))
        assertEquals(LifeCategory.HEALTH, classifier.classify("Take medication"))
    }

    @Test
    fun `no matching keywords returns UNCATEGORIZED`() {
        assertEquals(LifeCategory.UNCATEGORIZED, classifier.classify("Think about stuff"))
        assertEquals(LifeCategory.UNCATEGORIZED, classifier.classify("Foobar"))
    }

    @Test
    fun `empty input returns UNCATEGORIZED`() {
        assertEquals(LifeCategory.UNCATEGORIZED, classifier.classify(""))
        assertEquals(LifeCategory.UNCATEGORIZED, classifier.classify("   "))
    }

    @Test
    fun `classification is case insensitive`() {
        assertEquals(LifeCategory.WORK, classifier.classify("MEETING WITH CLIENT"))
        assertEquals(LifeCategory.SELF_CARE, classifier.classify("YOGA"))
    }

    @Test
    fun `description keywords contribute to classification`() {
        val result = classifier.classify(
            title = "Quick session",
            description = "Meditate and stretch before bed"
        )
        assertEquals(LifeCategory.SELF_CARE, result)
    }

    @Test
    fun `most-hit category wins over single hit`() {
        // "meeting" (work) + "client" (work) + "walk" (self-care) → WORK wins
        val result = classifier.classify("Client meeting then a walk")
        assertEquals(LifeCategory.WORK, result)
    }

    @Test
    fun `health tie-breaks over work`() {
        // "doctor" + "meeting" → tied at 1 each, HEALTH wins
        val result = classifier.classify("Doctor meeting")
        assertEquals(LifeCategory.HEALTH, result)
    }

    @Test
    fun `word boundary prevents partial matches`() {
        // "yogawear" should not match "yoga" — word-boundary required.
        assertEquals(LifeCategory.UNCATEGORIZED, classifier.classify("Browse yogawear"))
    }

    // ── Custom-keyword merging ────────────────────────────────────────────
    //
    // Regression tests for the wiring bug where every classifier call site
    // instantiated `LifeCategoryClassifier()` with default keywords and
    // ignored the user's customizations from Settings → Advanced Tuning.

    @Test
    fun `custom WORK keyword classifies task that defaults reject`() {
        // "tournament" is not in DEFAULT_KEYWORDS — without customization
        // this would be UNCATEGORIZED.
        assertEquals(LifeCategory.UNCATEGORIZED, classifier.classify("Run a tournament"))

        val custom = com.averycorp.prismtask.data.preferences.LifeCategoryCustomKeywords(
            work = "tournament"
        )
        val customized = LifeCategoryClassifier.withCustomKeywords(custom)
        assertEquals(LifeCategory.WORK, customized.classify("Run a tournament"))
    }

    @Test
    fun `custom keywords merge with defaults instead of replacing`() {
        // Adding a custom WORK keyword must NOT drop the built-in keywords —
        // tasks that already classified under defaults should still classify.
        val custom = com.averycorp.prismtask.data.preferences.LifeCategoryCustomKeywords(
            work = "tournament"
        )
        val customized = LifeCategoryClassifier.withCustomKeywords(custom)
        // Built-in keyword still works.
        assertEquals(LifeCategory.WORK, customized.classify("Prepare standup report"))
        // Custom keyword also works.
        assertEquals(LifeCategory.WORK, customized.classify("Run a tournament"))
    }

    @Test
    fun `custom CSV with multiple keywords splits and trims correctly`() {
        // The factory accepts comma-separated strings; whitespace trimming
        // and blank-skipping is on the factory, not the caller.
        val custom = com.averycorp.prismtask.data.preferences.LifeCategoryCustomKeywords(
            personal = " hobbies , knitting ,, board-game "
        )
        val customized = LifeCategoryClassifier.withCustomKeywords(custom)
        assertEquals(LifeCategory.PERSONAL, customized.classify("Friday knitting"))
        assertEquals(LifeCategory.PERSONAL, customized.classify("New hobbies"))
    }
}
