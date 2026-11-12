import pytest

from gildongmu_pipeline import speech


def test_splits_sentences():
    assert speech.sentences("하나예요. 둘이에요! 셋일까요?\n넷…  다섯") == ["하나예요.", "둘이에요!", "셋일까요?", "넷…", "다섯"]


def test_estimate_counts_syllables_digits_and_pauses():
    # 10 syllables at 5.2 per second, plus one sentence pause.
    assert speech.estimate_seconds("가나다라마바사아자차.") == pytest.approx(10 / 5.2 + 0.35)
    # Three digits count 1.6 each; spaces and punctuation count nothing.
    assert speech.estimate_seconds("832 미터") == pytest.approx((3 * 1.6 + 2) / 5.2 + 0.35)


def test_stated_lengths_in_the_bundled_package_match_the_estimate(bundled):
    # The app picks a story version by its stated seconds; they must be what this module computes.
    for poi in bundled["pois"]:
        for variant in poi["story"]["variants"]:
            estimate = speech.estimate_seconds(variant["text"])
            assert variant["seconds"] * 0.7 <= estimate <= variant["seconds"] * 1.15, poi["id"]


def test_finds_characters_speech_cannot_read():
    assert speech.unspeakable_characters("치악산(雉岳山)은 100% 좋아요") == ["(", "雉", "岳", "山", ")", "%"]
    assert speech.unspeakable_characters("이제 대관령을 넘어요. 높이가 832미터나 돼요!") == []
