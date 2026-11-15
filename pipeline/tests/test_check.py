import copy
import re

from conftest import LONG, REPO, SHORT, SOURCE, good_draft
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


def test_a_package_without_spots_or_fun_questions_is_valid(bundled):
    # Both are optional: a package written before they existed still loads in the app.
    del bundled["spots"], bundled["funQuiz"]
    assert check.package_problems(bundled) == []


def test_reports_broken_spots_and_fun_questions(bundled):
    broken = copy.deepcopy(bundled)
    spots, fun = broken["spots"], broken["funQuiz"]
    spots[0]["category"] = "bar"
    spots[1]["near"] = "nowhere"
    spots[2]["sources"] = []
    spots[3]["reason"] = "강릉 커피(원두)를 마셔요."
    spots[4]["reason"] = "여기는 관동팔경의 누각이에요."
    spots[5]["id"] = broken["pois"][0]["id"]
    spots[6]["lat"] = 137.0
    fun[0]["kind"] = "joke"
    fun[1]["answers"] = []
    fun[2]["prompt"] = "모자를 쓴 것은? (힌트: 숲)"
    problems = "\n".join(check.package_problems(broken))
    for fragment in (
        "category 'bar'", "near 'nowhere'", "needs at least one source", "unspeakable characters ( )",
        "opens with '여기는'", f"spot id {broken['pois'][0]['id']!r} is blank or used twice", "needs a name, lat and lon",
        "kind 'joke'", "question 'riddle-soap' needs at least one answer", "funQuiz question 'riddle-chair': unspeakable",
    ):
        assert fragment in problems, fragment


def test_an_illustration_must_be_one_the_app_can_draw(bundled):
    # The app shows the picture of the content's kind for a key it does not know, which would hide
    # a typing mistake; the check names the keys there are.
    assert any("illustration" in poi for poi in bundled["pois"]) and any("illustration" in spot for spot in bundled["spots"])
    broken = copy.deepcopy(bundled)
    broken["pois"][0]["illustration"] = "mountain"
    broken["spots"][0]["illustration"] = "Sea"
    broken["spots"][1]["illustration"] = None
    problems = "\n".join(check.package_problems(broken))
    for fragment in (
        f"poi {broken['pois'][0]['id']!r} has illustration 'mountain'", f"spot {broken['spots'][0]['id']!r} has illustration 'Sea'",
        f"spot {broken['spots'][1]['id']!r} has illustration None", "use one of: mountains, bowl, landmark, star, cup, road, sea,",
    ):
        assert fragment in problems, fragment


def test_an_illustration_is_optional_and_every_known_key_passes(bundled):
    for item in (*bundled["pois"], *bundled["spots"]):
        item.pop("illustration", None)
    assert check.package_problems(bundled) == []
    for key in check.ILLUSTRATIONS:
        bundled["pois"][0]["illustration"] = bundled["spots"][0]["illustration"] = key
        assert check.package_problems(bundled) == [], key


def test_the_illustration_keys_are_the_ones_the_app_draws():
    # The list is kept by hand in two languages; this reads the app's from its source.
    source = (REPO / "app/src/main/java/app/gildongmu/core/Signals.kt").read_text(encoding="utf-8")
    declared = source[source.index("enum class Illustration"):source.index("companion object", source.index("enum class Illustration"))]
    assert re.findall(r'^\s+[A-Z]+\("([a-z]+)"\)', declared, flags=re.MULTILINE) == list(check.ILLUSTRATIONS)


def test_a_stops_spot_must_not_meet_the_route_well_before_the_stop(bundled):
    # The app counts a spot as passed 2 km after the car goes by where it meets the route.
    broken = copy.deepcopy(bundled)
    stop = next(s for s in broken["stops"] if s["id"] == "hoengseong")
    spot = broken["spots"][0]
    spot["near"] = "hoengseong"
    spot["lat"], spot["lon"] = 37.398, 127.972          # on the route, about 20 km before 횡성휴게소
    problems = "\n".join(check.package_problems(broken))
    assert "before stop 'hoengseong'" in problems
    spot["lat"], spot["lon"] = stop["lat"], stop["lon"]  # at the stop itself: fine
    assert "before stop" not in "\n".join(check.package_problems(broken))
