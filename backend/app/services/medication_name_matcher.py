"""Pure-function deterministic medication-name resolver.

Mirrors `MedicationNameMatcher.kt` (Android) and
`web/src/features/batch/medicationNameMatcher.ts` byte-for-byte: the same
command + medication list MUST produce the same `MatchResult` on every
surface, otherwise the audit's failure-mode #6 (case/whitespace/unicode
brittleness) reopens.

NEVER does fuzzy or substring matching. Typos and partial words return
`NoMatch` so the caller falls through to the Haiku batch path. This is the
audit's failure-mode #2 firewall: silent typo confidence is exactly the bug
we are guarding against, so a stricter "exact whole-word match or nothing"
contract is the safe default.

Normalization (must stay byte-identical with Kotlin + TS twins):
    1. Unicode NFC normalize
    2. strip
    3. lowercase
    4. strip trailing ASCII punctuation [.,!?;:]
"""

from __future__ import annotations

import unicodedata
from dataclasses import dataclass
from typing import Iterable, Optional, Union

_TRAILING_PUNCT = ".,!?;:"


@dataclass(frozen=True)
class Medication:
    id: str
    name: str
    display_label: Optional[str] = None


@dataclass(frozen=True)
class AmbiguousPhrase:
    phrase: str
    candidate_entity_ids: list[str]


@dataclass(frozen=True)
class NoMatch:
    pass


@dataclass(frozen=True)
class Unambiguous:
    """`matches` is keyed by the normalized phrase that hit the medication."""

    matches: dict[str, str]


@dataclass(frozen=True)
class Ambiguous:
    phrases: list[AmbiguousPhrase]


@dataclass(frozen=True)
class Mixed:
    unambiguous: dict[str, str]
    ambiguous: list[AmbiguousPhrase]


MatchResult = Union[NoMatch, Unambiguous, Ambiguous, Mixed]


def normalize(s: str) -> str:
    """Apply the four-step normalization. Must match the Kotlin + TS twins."""

    nfc = unicodedata.normalize("NFC", s)
    trimmed = nfc.strip()
    lower = trimmed.lower()
    end = len(lower)
    while end > 0 and lower[end - 1] in _TRAILING_PUNCT:
        end -= 1
    return lower[:end]


def _is_word_char(ch: str) -> bool:
    return ch.isalnum() or ch == "_"


def _build_key_index(medications: Iterable[Medication]) -> dict[str, list[str]]:
    """Map normalized name/label -> ordered list of medication ids that share it.

    The list preserves insertion order so two meds named "Wellbutrin" come back
    in a predictable order (matches the Kotlin + TS twins).
    """

    key_to_ids: dict[str, list[str]] = {}
    for med in medications:
        for raw in (med.name, med.display_label):
            if raw is None:
                continue
            key = normalize(raw)
            if not key:
                continue
            ids = key_to_ids.setdefault(key, [])
            if med.id not in ids:
                ids.append(med.id)
    return key_to_ids


def _find_longest_key_at(haystack: str, start: int, keys: list[str]) -> Optional[str]:
    for k in keys:
        end = start + len(k)
        if end > len(haystack):
            continue
        if end < len(haystack) and _is_word_char(haystack[end]):
            continue
        if haystack[start:end] == k:
            return k
    return None


def _classify(
    unambiguous: dict[str, str],
    ambiguous_map: dict[str, list[str]],
) -> MatchResult:
    ambiguous_list = [
        AmbiguousPhrase(phrase=phrase, candidate_entity_ids=sorted(ids))
        for phrase, ids in ambiguous_map.items()
    ]
    if not unambiguous and not ambiguous_list:
        return NoMatch()
    if not ambiguous_list:
        return Unambiguous(matches=unambiguous)
    if not unambiguous:
        return Ambiguous(phrases=ambiguous_list)
    return Mixed(unambiguous=unambiguous, ambiguous=ambiguous_list)


def match(command_text: str, medications: list[Medication]) -> MatchResult:
    if not command_text or not command_text.strip() or not medications:
        return NoMatch()
    normalized_command = normalize(command_text)
    if not normalized_command:
        return NoMatch()

    key_to_ids = _build_key_index(medications)
    if not key_to_ids:
        return NoMatch()

    keys = sorted(key_to_ids.keys(), key=len, reverse=True)
    unambiguous: dict[str, str] = {}
    ambiguous_map: dict[str, list[str]] = {}

    i = 0
    while i < len(normalized_command):
        at_word_start = _is_word_char(normalized_command[i]) and (
            i == 0 or not _is_word_char(normalized_command[i - 1])
        )
        if not at_word_start:
            i += 1
            continue
        matched_key = _find_longest_key_at(normalized_command, i, keys)
        if matched_key is not None:
            ids = key_to_ids[matched_key]
            if len(ids) == 1:
                unambiguous[matched_key] = ids[0]
            else:
                bucket = ambiguous_map.setdefault(matched_key, [])
                for med_id in ids:
                    if med_id not in bucket:
                        bucket.append(med_id)
            i += len(matched_key)
        else:
            while i < len(normalized_command) and _is_word_char(normalized_command[i]):
                i += 1

    return _classify(unambiguous, ambiguous_map)
