package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.domain.usecase.MedicationNameMatcher.AmbiguousPhrase
import com.averycorp.prismtask.domain.usecase.MedicationNameMatcher.MatchResult
import com.averycorp.prismtask.domain.usecase.MedicationNameMatcher.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Twin-checked against the Python and TS matchers — the same input must
 * produce the same `MatchResult` shape on every surface.
 */
class MedicationNameMatcherTest {

    private val wellbutrin = Medication(id = "10", name = "Wellbutrin XL", displayLabel = "Bupropion")
    private val adderall = Medication(id = "20", name = "Adderall")
    private val twoWellbutrins = listOf(
        Medication(id = "30", name = "Wellbutrin"),
        Medication(id = "31", name = "Wellbutrin")
    )

    @Test
    fun `exact match returns unambiguous`() {
        val result = MedicationNameMatcher.match("I took my Wellbutrin XL today", listOf(wellbutrin, adderall))
        assertEquals(MatchResult.Unambiguous(mapOf("wellbutrin xl" to "10")), result)
    }

    @Test
    fun `case mismatch normalizes`() {
        val result = MedicationNameMatcher.match("WELLBUTRIN xl", listOf(wellbutrin))
        assertEquals(MatchResult.Unambiguous(mapOf("wellbutrin xl" to "10")), result)
    }

    @Test
    fun `trailing whitespace normalizes`() {
        val result = MedicationNameMatcher.match("  took adderall   ", listOf(adderall))
        assertEquals(MatchResult.Unambiguous(mapOf("adderall" to "20")), result)
    }

    @Test
    fun `unicode smart quote normalizes`() {
        // The smart-quote stays as-is after NFC; the matcher normalizes case
        // and trailing punctuation only — quotes inside the command don't
        // break a whole-word match because they're non-word boundaries.
        val result = MedicationNameMatcher.match("“took adderall”", listOf(adderall))
        assertEquals(MatchResult.Unambiguous(mapOf("adderall" to "20")), result)
    }

    @Test
    fun `typo returns no match`() {
        // "wellbutrn" is one letter off — exact contract refuses to guess.
        val result = MedicationNameMatcher.match("took wellbutrn", listOf(wellbutrin))
        assertEquals(MatchResult.NoMatch, result)
    }

    @Test
    fun `two wellbutrins returns ambiguous`() {
        val result = MedicationNameMatcher.match("took my Wellbutrin", twoWellbutrins)
        assertTrue(result is MatchResult.Ambiguous)
        result as MatchResult.Ambiguous
        assertEquals(1, result.phrases.size)
        assertEquals(AmbiguousPhrase("wellbutrin", listOf("30", "31")), result.phrases[0])
    }

    @Test
    fun `display label match when name does not`() {
        val result = MedicationNameMatcher.match("finished bupropion today", listOf(wellbutrin))
        assertEquals(MatchResult.Unambiguous(mapOf("bupropion" to "10")), result)
    }

    @Test
    fun `mixed command with one unambiguous and one ambiguous returns mixed`() {
        val meds = listOf(adderall) + twoWellbutrins
        val result = MedicationNameMatcher.match("took my Wellbutrin and Adderall", meds)
        assertTrue(result is MatchResult.Mixed)
        result as MatchResult.Mixed
        assertEquals(mapOf("adderall" to "20"), result.unambiguous)
        assertEquals(1, result.ambiguous.size)
        assertEquals(AmbiguousPhrase("wellbutrin", listOf("30", "31")), result.ambiguous[0])
    }

    @Test
    fun `trailing punctuation is stripped`() {
        val result = MedicationNameMatcher.match("took my Adderall.", listOf(adderall))
        assertEquals(MatchResult.Unambiguous(mapOf("adderall" to "20")), result)
    }

    @Test
    fun `longest key wins on overlap`() {
        val plain = Medication(id = "100", name = "Wellbutrin")
        val xl = Medication(id = "101", name = "Wellbutrin XL")
        val result = MedicationNameMatcher.match("took my Wellbutrin XL", listOf(plain, xl))
        assertEquals(MatchResult.Unambiguous(mapOf("wellbutrin xl" to "101")), result)
    }

    @Test
    fun `substring inside another word does not match`() {
        // "addy" is a slang for Adderall, but the matcher must not infer it
        // — substring/fuzzy match is failure mode #2, which is the bug.
        val result = MedicationNameMatcher.match("took my addy", listOf(adderall))
        assertEquals(MatchResult.NoMatch, result)
    }

    @Test
    fun `empty command returns no match`() {
        assertEquals(MatchResult.NoMatch, MedicationNameMatcher.match("", listOf(adderall)))
        assertEquals(MatchResult.NoMatch, MedicationNameMatcher.match("   ", listOf(adderall)))
    }

    @Test
    fun `empty medication list returns no match`() {
        assertEquals(MatchResult.NoMatch, MedicationNameMatcher.match("took adderall", emptyList()))
    }
}
