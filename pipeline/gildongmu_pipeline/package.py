"""Reading route files and assembling route packages (schema 1, the format the app loads)."""

from __future__ import annotations

import datetime
import json
import math
from pathlib import Path
from typing import Any

from . import __version__, check, speech
from .select import Placed
from .writer import Draft

ROUTE_KEYS = ("id", "title", "origin", "destination", "polyline")


def load_route(path: Path) -> dict[str, Any]:
    """A route file: where the trip goes, without any places yet.

    {"id", "title", "origin": {name, lat, lon}, "destination": {...}, "polyline": [[lat, lon], ...],
     "stops": [{id, name, lat, lon, charger}], "generalQuiz": [...], "geometryNote": "...",
     "spots": [{id, category, name, area, lat, lon, reason, near, sources}], "funQuiz": [...]}

    Spots and fun questions are written by hand, like the general questions, and are copied into
    the package unchanged.
    """
    route = json.loads(path.read_text(encoding="utf-8"))
    missing = [key for key in ROUTE_KEYS if key not in route]
    if missing:
        raise ValueError(f"{path} is missing: {', '.join(missing)}")
    if len(route["polyline"]) < 2:
        raise ValueError(f"{path}: polyline needs at least two points")
    return route


def route_of(package: dict[str, Any]) -> dict[str, Any]:
    """The route file that would produce `package`: everything except the places."""
    keep = (*ROUTE_KEYS, "stops", "generalQuiz", "geometryNote", "spots", "funQuiz")
    return {key: package[key] for key in keep if key in package}


def assemble(route: dict[str, Any], written: list[tuple[Placed, Draft]], *, model: str) -> dict[str, Any]:
    """The finished package. Raises ValueError if the app would refuse to load it."""
    pois = []
    for index, (placed, draft) in enumerate(sorted(written, key=lambda pair: pair[0].route_km), start=1):
        poi_id = f"poi-{index:02d}"
        candidate = placed.candidate
        pois.append({
            "id": poi_id,
            "name": draft.name,
            "area": draft.area,
            "lat": round(candidate.location[0], 5),
            "lon": round(candidate.location[1], 5),
            "story": {
                "title": draft.title,
                "variants": [
                    {"seconds": math.ceil(speech.estimate_seconds(text)), "text": text}
                    for text in (draft.short, draft.long)
                ],
            },
            "quiz": [
                {
                    "id": f"{poi_id}-q{number}",
                    "prompt": question["prompt"].strip(),
                    "answers": [a.strip() for a in question["answers"] if a.strip()],
                    **({"choices": [c.strip() for c in question["choices"]]} if question.get("choices") else {}),
                    "explanation": question.get("explanation", "").strip(),
                    "evidence": question.get("evidence", "").strip(),
                }
                for number, question in enumerate(draft.quiz, start=1)
            ],
            "sources": [{"title": candidate.source_title, "url": candidate.source_url}],
        })

    package = {
        "schema": 1,
        "id": route["id"],
        "title": route["title"],
        "language": "ko",
        "generator": f"gildongmu-pipeline {__version__} · {model}",
        "generatedOn": datetime.date.today().isoformat(),
        **({"geometryNote": route["geometryNote"]} if route.get("geometryNote") else {}),
        "origin": route["origin"],
        "destination": route["destination"],
        "polyline": route["polyline"],
        "stops": route.get("stops", []),
        "pois": pois,
        **({"spots": route["spots"]} if route.get("spots") else {}),
        "generalQuiz": route.get("generalQuiz", []),
        **({"funQuiz": route["funQuiz"]} if route.get("funQuiz") else {}),
    }
    problems = check.package_problems(package)
    if problems:
        raise ValueError("The assembled package is not valid:\n" + "\n".join(f"- {p}" for p in problems))
    return package


def write(package: dict[str, Any], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
