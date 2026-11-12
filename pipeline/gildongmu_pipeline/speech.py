"""Speaking-time estimate and checks for text that will be read aloud.

`estimate_seconds` uses the same numbers as app/.../core/Speech.kt. The app picks a story version
by its stated length, so the two must agree.
"""

from __future__ import annotations

import re

SYLLABLES_PER_SECOND = 5.2
SENTENCE_PAUSE_SECONDS = 0.35
DIGIT_SYLLABLES = 1.6

_SENTENCE_END = re.compile(r"(?<=[.!?…])\s+|\n+")

# Read out badly, or not at all, by speech synthesis.
UNSPEAKABLE = set("()[]{}<>《》「」·%&/*#")


def sentences(text: str) -> list[str]:
    return [s.strip() for s in _SENTENCE_END.split(text.strip()) if s.strip()]


def estimate_seconds(text: str) -> float:
    syllables = 0.0
    for c in text:
        if c.isdigit():
            syllables += DIGIT_SYLLABLES
        elif c.isalpha():
            syllables += 1.0
    return syllables / SYLLABLES_PER_SECOND + len(sentences(text)) * SENTENCE_PAUSE_SECONDS


def unspeakable_characters(text: str) -> list[str]:
    """Characters in `text` that should not be sent to speech synthesis, without duplicates."""
    found: list[str] = []
    for c in text:
        if (c in UNSPEAKABLE or "一" <= c <= "鿿") and c not in found:
            found.append(c)
    return found
