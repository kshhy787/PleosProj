"""Checks on generated content, and on whole route packages.

The model writes the stories; these checks decide whether they are used. A story may only contain
numbers that appear in its source text, every quiz question must quote the sentence it rests on,
and everything must be speakable. The same structural rules the app enforces when it loads a
package (app/.../core/RoutePackageParser.kt) are enforced here before one is written.

One rule is stricter here than in the app. A place or a spot may name the picture for its card
with `illustration`, one of ILLUSTRATIONS. The app reads a key it does not know as no picture, so
that a package written for a newer app still loads; here an unknown key is a problem, because for
whoever is writing a package it is almost always a typing mistake, and the app would hide it by
quietly showing the picture of the content's kind instead.
"""

from __future__ import annotations

import re
from typing import Any

from . import speech
from .geo import RouteGeometry

SHORT_SECONDS = (9.0, 22.0)
LONG_SECONDS = (28.0, 60.0)

_NUMBER = re.compile(r"\d[\d,]*")

# The host also tells a story before the car reaches the place, and while it is parked at a rest
# area. An opening that only makes sense on the spot would be wrong there.
_ON_THE_SPOT_OPENINGS = ("지금 지나", "지금 보이", "지금 달리", "여기는", "여기가", "여기에", "이곳", "이 근처", "이제 ")

# The same values the app accepts (core/Model.kt: SpotCategory, QuizKind, Spot.NEAR_DESTINATION).
SPOT_CATEGORIES = ("food", "sight", "cafe")

# The pictures the app can draw (core/Signals.kt: Illustration). A place or a spot may pick one.
ILLUSTRATIONS = ("mountains", "bowl", "landmark", "star", "cup", "road", "sea", "flower", "pottery", "wheel", "coffee")

# The app leaves out a spot once the car is this far past where the spot meets the route
# (core/SpotPicker.kt), so a rest stop's spot must not meet the route earlier than this before the stop.
PASSED_SPOT_KM = 2.0
FUN_KINDS = ("riddle", "nonsense")
NEAR_DESTINATION = "destination"


def _squash(text: str) -> str:
    return re.sub(r"\s+", "", text)


def _numbers(text: str) -> set[str]:
    return {m.group().replace(",", "") for m in _NUMBER.finditer(text)}


def story_problems(short: str, long: str, source_text: str) -> list[str]:
    """What is wrong with a generated story, in words the model can act on. Empty when it is fine."""
    problems: list[str] = []
    for label, text, (low, high) in (("short", short, SHORT_SECONDS), ("long", long, LONG_SECONDS)):
        if not text.strip():
            problems.append(f"{label}: 비어 있습니다.")
            continue
        seconds = speech.estimate_seconds(text)
        if not low <= seconds <= high:
            problems.append(f"{label}: 읽는 시간이 약 {seconds:.0f}초입니다. {low:.0f}~{high:.0f}초가 되도록 길이를 맞추세요.")
        bad = speech.unspeakable_characters(text)
        if bad:
            problems.append(f"{label}: 음성으로 읽을 수 없는 문자가 있습니다: {' '.join(bad)}")
        opening = next((words for words in _ON_THE_SPOT_OPENINGS if text.lstrip().startswith(words)), None)
        if opening:
            problems.append(f"{label}: \"{opening.strip()}\"처럼 차가 그 자리에 있어야 맞는 말로 시작합니다. 장소나 지역의 이름으로 시작하세요.")
        # Any number that is not in the source is treated as invented. Single digits are too common
        # in ordinary sentences ("1번", "2명") to be worth flagging.
        invented = sorted(n for n in _numbers(text) - _numbers(source_text) if len(n) >= 2)
        if invented:
            problems.append(f"{label}: 근거 문서에 없는 숫자가 있습니다: {', '.join(invented)}")
    return problems


def question_problems(question: dict[str, Any], source_text: str) -> list[str]:
    """What is wrong with one generated quiz question. Empty when it is fine."""
    problems: list[str] = []
    prompt = question.get("prompt", "").strip()
    answers = [a.strip() for a in question.get("answers", []) if a.strip()]
    choices = [c.strip() for c in question.get("choices", []) if c.strip()]
    evidence = question.get("evidence", "").strip()
    source = _squash(source_text)

    if not prompt:
        problems.append("문제가 비어 있습니다.")
    if not answers:
        problems.append("정답이 없습니다.")
    if not evidence or _squash(evidence) not in source:
        problems.append("evidence가 근거 문서에 그대로 나오는 문장이 아닙니다.")
    elif answers and not any(_squash(a) in _squash(evidence) or _squash(a) in source for a in answers):
        problems.append("정답이 근거 문서에 나오지 않습니다.")
    if choices:
        if len(choices) < 2:
            problems.append("보기는 두 개 이상이어야 합니다.")
        if sum(1 for c in choices if c in answers) != 1:
            problems.append("보기 가운데 정확히 하나만 answers에 있어야 합니다.")
    for text in (prompt, question.get("explanation", ""), *answers, *choices):
        bad = speech.unspeakable_characters(text)
        if bad:
            problems.append(f"음성으로 읽을 수 없는 문자가 있습니다: {' '.join(bad)}")
            break
    return problems


def package_problems(package: dict[str, Any]) -> list[str]:
    """Everything wrong with a route package. The app would refuse to load any of these."""
    problems: list[str] = []

    if package.get("schema") != 1:
        problems.append(f"schema must be 1, found {package.get('schema')!r}")
    for key in ("id", "title"):
        if not str(package.get(key, "")).strip():
            problems.append(f"{key} is missing")
    for key in ("origin", "destination"):
        place = package.get(key) or {}
        if not place.get("name") or not _is_position(place):
            problems.append(f"{key} needs a name, lat and lon")

    polyline = package.get("polyline") or []
    if len(polyline) < 2:
        problems.append("polyline needs at least two points")
    for index, pair in enumerate(polyline):
        if not (isinstance(pair, list) and len(pair) == 2 and _is_position({"lat": pair[0], "lon": pair[1]})):
            problems.append(f"polyline[{index}] should be [latitude, longitude]")

    ids: set[str] = set()

    def unique(identifier: Any, what: str) -> None:
        if not isinstance(identifier, str) or not identifier.strip() or identifier in ids:
            problems.append(f"{what} id {identifier!r} is blank or used twice")
        else:
            ids.add(identifier)

    for stop in package.get("stops") or []:
        unique(stop.get("id"), "stop")
        if not stop.get("name") or not _is_position(stop):
            problems.append(f"stop {stop.get('id')!r} needs a name, lat and lon")

    questions: list[dict[str, Any]] = list(package.get("generalQuiz") or [])
    for poi in package.get("pois") or []:
        label = poi.get("id")
        unique(label, "poi")
        if not poi.get("name") or not _is_position(poi):
            problems.append(f"poi {label!r} needs a name, lat and lon")
        story = poi.get("story") or {}
        variants = story.get("variants") or []
        if not story.get("title") or not variants:
            problems.append(f"poi {label!r} needs a story title and at least one variant")
        for variant in variants:
            text = variant.get("text", "")
            seconds = variant.get("seconds", 0)
            if not text.strip() or not isinstance(seconds, int) or seconds <= 0:
                problems.append(f"poi {label!r} has an empty story variant")
                continue
            estimate = speech.estimate_seconds(text)
            if not seconds * 0.7 <= estimate <= seconds * 1.15:
                problems.append(f"poi {label!r}: a variant says {seconds}s but is estimated at {estimate:.0f}s")
            bad = speech.unspeakable_characters(text)
            if bad:
                problems.append(f"poi {label!r}: unspeakable characters {' '.join(bad)}")
        problems.extend(_illustration_problems(poi, f"poi {label!r}"))
        questions.extend(poi.get("quiz") or [])

    stop_ids = {stop.get("id") for stop in package.get("stops") or []}
    stops_by_id = {stop.get("id"): stop for stop in package.get("stops") or [] if _is_position(stop)}
    try:
        geometry = RouteGeometry([tuple(p) for p in polyline]) if len(polyline) >= 2 else None
    except (TypeError, ValueError):
        geometry = None
    for spot in package.get("spots") or []:
        label = spot.get("id")
        unique(label, "spot")
        if not str(spot.get("name", "")).strip() or not _is_position(spot):
            problems.append(f"spot {label!r} needs a name, lat and lon")
        if spot.get("category") not in SPOT_CATEGORIES:
            problems.append(f"spot {label!r} has category {spot.get('category')!r} (use food, sight or cafe)")
        reason = str(spot.get("reason", ""))
        if not reason.strip():
            problems.append(f"spot {label!r} has no reason")
        bad = speech.unspeakable_characters(f"{spot.get('name', '')} {reason}")
        if bad:
            problems.append(f"spot {label!r}: unspeakable characters {' '.join(bad)}")
        # The reason is said when the spot is recommended, usually long before the car is there.
        opening = next((words for words in _ON_THE_SPOT_OPENINGS if reason.lstrip().startswith(words)), None)
        if opening:
            problems.append(f"spot {label!r}: the reason opens with {opening.strip()!r}, which only makes sense on the spot")
        near = spot.get("near")
        if near is not None and near != NEAR_DESTINATION and near not in stop_ids:
            problems.append(f"spot {label!r} is near {near!r}, which is neither a stop id nor {NEAR_DESTINATION!r}")
        sources = spot.get("sources") or []
        if not sources or not all(source.get("title") and source.get("url") for source in sources):
            problems.append(f"spot {label!r} needs at least one source with a title and url")
        problems.extend(_illustration_problems(spot, f"spot {label!r}"))
        stop = stops_by_id.get(near)
        if geometry and stop and _is_position(spot):
            before = geometry.project((stop["lat"], stop["lon"])).route_km - geometry.project((spot["lat"], spot["lon"])).route_km
            if before > PASSED_SPOT_KM:
                problems.append(
                    f"spot {label!r} meets the route {before:.1f} km before stop {near!r}, so the app counts it as "
                    f"passed before the car gets there; drop its near or move it"
                )

    for question in package.get("funQuiz") or []:
        label = question.get("id")
        if question.get("kind") not in FUN_KINDS:
            problems.append(f"funQuiz question {label!r} has kind {question.get('kind')!r} (use riddle or nonsense)")
        texts = [question.get("prompt", ""), question.get("explanation", ""), *(question.get("answers") or [])]
        bad = speech.unspeakable_characters(" ".join(str(text) for text in texts))
        if bad:
            problems.append(f"funQuiz question {label!r}: unspeakable characters {' '.join(bad)}")
        questions.append(question)

    for question in questions:
        label = question.get("id")
        unique(label, "question")
        answers = question.get("answers") or []
        choices = question.get("choices") or []
        if not str(question.get("prompt", "")).strip():
            problems.append(f"question {label!r} has no prompt")
        if not answers or not all(str(a).strip() for a in answers):
            problems.append(f"question {label!r} needs at least one answer")
        if choices and (len(choices) < 2 or sum(1 for c in choices if c in answers) != 1):
            problems.append(f"question {label!r} must have two or more choices, exactly one of them in answers")
    return problems


def _illustration_problems(item: dict[str, Any], what: str) -> list[str]:
    """The key is optional, so leaving it out is fine; one the app cannot draw is not (see the module docstring)."""
    if "illustration" not in item or item["illustration"] in ILLUSTRATIONS:
        return []
    return [f"{what} has illustration {item['illustration']!r} (use one of: {', '.join(ILLUSTRATIONS)})"]


def _is_position(value: dict[str, Any]) -> bool:
    lat, lon = value.get("lat"), value.get("lon")
    return isinstance(lat, (int, float)) and isinstance(lon, (int, float)) and -90 <= lat <= 90 and -180 <= lon <= 180
