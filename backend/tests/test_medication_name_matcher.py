"""Twin-checked against MedicationNameMatcher.kt and medicationNameMatcher.ts.

If a test name here changes, the same name must change on the other two
surfaces — the audit's failure-mode #6 firewall depends on identical
behavior across all three.
"""

from app.services.medication_name_matcher import (
    Ambiguous,
    AmbiguousPhrase,
    Medication,
    Mixed,
    NoMatch,
    Unambiguous,
    match,
    normalize,
)

WELLBUTRIN = Medication(id="10", name="Wellbutrin XL", display_label="Bupropion")
ADDERALL = Medication(id="20", name="Adderall")
TWO_WELLBUTRINS = [
    Medication(id="30", name="Wellbutrin"),
    Medication(id="31", name="Wellbutrin"),
]


def test_exact_match_returns_unambiguous():
    result = match("I took my Wellbutrin XL today", [WELLBUTRIN, ADDERALL])
    assert result == Unambiguous(matches={"wellbutrin xl": "10"})


def test_case_mismatch_normalizes():
    assert match("WELLBUTRIN xl", [WELLBUTRIN]) == Unambiguous(
        matches={"wellbutrin xl": "10"}
    )


def test_trailing_whitespace_normalizes():
    assert match("  took adderall   ", [ADDERALL]) == Unambiguous(
        matches={"adderall": "20"}
    )


def test_unicode_smart_quote_normalizes():
    # Smart quotes are non-word chars, so a quoted name still resolves.
    assert match("“took adderall”", [ADDERALL]) == Unambiguous(
        matches={"adderall": "20"}
    )


def test_typo_returns_no_match():
    # No fuzzy match — the contract refuses to guess.
    assert match("took wellbutrn", [WELLBUTRIN]) == NoMatch()


def test_two_wellbutrins_returns_ambiguous():
    result = match("took my Wellbutrin", TWO_WELLBUTRINS)
    assert isinstance(result, Ambiguous)
    assert len(result.phrases) == 1
    assert result.phrases[0] == AmbiguousPhrase(
        phrase="wellbutrin", candidate_entity_ids=["30", "31"]
    )


def test_display_label_match_when_name_does_not():
    assert match("finished bupropion today", [WELLBUTRIN]) == Unambiguous(
        matches={"bupropion": "10"}
    )


def test_mixed_command_with_one_unambiguous_and_one_ambiguous_returns_mixed():
    meds = [ADDERALL, *TWO_WELLBUTRINS]
    result = match("took my Wellbutrin and Adderall", meds)
    assert isinstance(result, Mixed)
    assert result.unambiguous == {"adderall": "20"}
    assert len(result.ambiguous) == 1
    assert result.ambiguous[0] == AmbiguousPhrase(
        phrase="wellbutrin", candidate_entity_ids=["30", "31"]
    )


def test_trailing_punctuation_is_stripped():
    assert match("took my Adderall.", [ADDERALL]) == Unambiguous(
        matches={"adderall": "20"}
    )


def test_longest_key_wins_on_overlap():
    plain = Medication(id="100", name="Wellbutrin")
    xl = Medication(id="101", name="Wellbutrin XL")
    assert match("took my Wellbutrin XL", [plain, xl]) == Unambiguous(
        matches={"wellbutrin xl": "101"}
    )


def test_substring_inside_another_word_does_not_match():
    # "addy" is slang for Adderall; the matcher must not infer it.
    assert match("took my addy", [ADDERALL]) == NoMatch()


def test_empty_command_returns_no_match():
    assert match("", [ADDERALL]) == NoMatch()
    assert match("   ", [ADDERALL]) == NoMatch()


def test_empty_medication_list_returns_no_match():
    assert match("took adderall", []) == NoMatch()


def test_normalize_idempotent():
    s = "  Wellbutrin XL!  "
    once = normalize(s)
    twice = normalize(once)
    assert once == twice
    assert once == "wellbutrin xl"
