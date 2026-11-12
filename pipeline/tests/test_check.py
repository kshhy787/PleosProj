import copy

from conftest import LONG, SHORT, SOURCE, good_draft
from gildongmu_pipeline import check


def test_a_faithful_story_passes():
    assert check.story_problems(SHORT, LONG, SOURCE) == []


def test_flags_a_number_that_is_not_in_the_source():
    problems = check.story_problems(SHORT.replace("832", "1832"), LONG, SOURCE)
    assert any("1832" in p and "short" in p for p in problems)


def test_flags_unspeakable_characters_and_wrong_lengths():
    problems = check.story_problems("대관령(大關嶺)이에요.", " ".join([LONG] * 3), SOURCE)
    assert any("short" in p and "(" in p for p in problems)
    assert any("short" in p and "읽는 시간" in p for p in problems)
    assert any("long" in p and "읽는 시간" in p for p in problems)


def test_flags_an_opening_that_only_makes_sense_on_the_spot():
    # The host also tells a story before the car gets there and while it is parked.
    for opening in ("지금 지나는 평창에는 대관령이 있어요.", "여기는 대관령이에요.", "이제 대관령을 넘어요."):
        short = opening + SHORT[SHORT.index(".") + 1:]
        problems = check.story_problems(short, LONG, SOURCE)
        assert any("short" in p and "그 자리에" in p for p in problems), opening
    # The same words later in the story are fine: "지금도" below is about time, not place.
    assert check.story_problems(SHORT + " 옛길은 지금도 걸어서 넘을 수 있어요.", LONG, SOURCE) == []


def test_bundled_stories_can_be_told_anywhere(bundled):
    for poi in bundled["pois"]:
        short, long = (variant["text"] for variant in poi["story"]["variants"])
        problems = [p for p in check.story_problems(short, long, "") if "그 자리에" in p]
        assert problems == [], poi["id"]


def test_a_grounded_question_passes():
    assert check.question_problems(good_draft()["quiz"][0], SOURCE) == []


def test_evidence_must_be_quoted_from_the_source():
    question = good_draft()["quiz"][0] | {"evidence": "대관령의 높이는 832미터이다."}
    assert any("evidence" in p for p in check.question_problems(question, SOURCE))


def test_evidence_may_differ_in_whitespace_only():
    question = good_draft()["quiz"][0] | {"evidence": "높이 832m로  영동과\n영서를 잇는 관문이다."}
    assert check.question_problems(question, SOURCE) == []


def test_the_answer_must_appear_in_the_source():
    question = good_draft()["quiz"][0] | {"answers": ["1832"]}
    assert any("정답이 근거 문서에" in p for p in check.question_problems(question, SOURCE))


def test_choices_need_exactly_one_right_answer():
    question = good_draft()["quiz"][0] | {"choices": ["332", "832", "1832"], "answers": ["832"]}
    assert check.question_problems(question, SOURCE) == []
    question = question | {"answers": ["832", "332"]}
    assert any("정확히 하나" in p for p in check.question_problems(question, SOURCE))


def test_the_bundled_package_is_valid(bundled):
    assert check.package_problems(bundled) == []


def test_reports_what_the_app_would_reject(bundled):
    broken = copy.deepcopy(bundled)
    broken["schema"] = 2
    broken["polyline"] = broken["polyline"][:1]
    broken["pois"][0]["story"]["variants"][0]["seconds"] = 90       # far longer than the text
    broken["pois"][1]["id"] = broken["pois"][0]["id"]               # duplicate id
    broken["pois"][2]["quiz"][0]["answers"] = []
    broken["generalQuiz"][0]["choices"] = ["서해", "남해"]           # the right answer is not among them
    problems = "\n".join(check.package_problems(broken))
    for fragment in ("schema must be 1", "at least two points", "says 90s", "used twice", "needs at least one answer", "exactly one of them"):
        assert fragment in problems, fragment
