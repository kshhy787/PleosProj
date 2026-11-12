"""Where candidate places come from: Wikipedia, and TourAPI when a key is available."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Any

from .geo import Point
from . import http
from .http import GetJson

WIKIPEDIA_API = "https://ko.wikipedia.org/w/api.php"
TOURAPI_BASE = "https://apis.data.go.kr/B551011/KorService2"
TOURAPI_DATASET = "https://www.data.go.kr/data/15101578/openapi.do"

# TourAPI content types worth telling a story about: tourist attractions and cultural facilities.
TOURAPI_CONTENT_TYPES = (12, 14)


@dataclass
class Candidate:
    """A place near the route, with the text a story about it must be grounded in."""

    key: str  # unique across sources, e.g. "wikipedia:12345"
    title: str
    location: Point
    text: str = ""
    source_title: str = ""
    source_url: str = ""
    kind: str = ""  # "wikipedia" or "tourapi"
    extra: dict[str, Any] = field(default_factory=dict)


class Wikipedia:
    """Korean Wikipedia: articles with coordinates near a point, and their opening sections."""

    def __init__(self, fetch: GetJson | None = None):
        self._fetch = fetch or http.get_json

    def near(self, point: Point, radius_m: int = 10_000, limit: int = 50) -> list[Candidate]:
        data = self._fetch(WIKIPEDIA_API, {
            "action": "query", "format": "json", "formatversion": 2,
            "list": "geosearch", "gscoord": f"{point[0]}|{point[1]}",
            "gsradius": min(radius_m, 10_000),  # the API's maximum
            "gslimit": limit,
        })
        return [
            Candidate(
                key=f"wikipedia:{item['pageid']}",
                title=item["title"],
                location=(item["lat"], item["lon"]),
                kind="wikipedia",
                extra={"pageid": item["pageid"]},
            )
            for item in data.get("query", {}).get("geosearch", [])
        ]

    def fill_text(self, candidates: list[Candidate]) -> None:
        """Fetches the opening section and URL of each Wikipedia candidate, 20 at a time."""
        wanted = [c for c in candidates if c.kind == "wikipedia" and not c.text]
        for start in range(0, len(wanted), 20):
            batch = wanted[start:start + 20]
            data = self._fetch(WIKIPEDIA_API, {
                "action": "query", "format": "json", "formatversion": 2,
                "prop": "extracts|info", "exintro": 1, "explaintext": 1, "exlimit": 20, "inprop": "url",
                "pageids": "|".join(str(c.extra["pageid"]) for c in batch),
            })
            pages = {page["pageid"]: page for page in data.get("query", {}).get("pages", [])}
            for candidate in batch:
                page = pages.get(candidate.extra["pageid"], {})
                candidate.text = (page.get("extract") or "").strip()
                candidate.source_title = f"위키백과: {candidate.title}"
                candidate.source_url = page.get("fullurl", "")


class TourApi:
    """The Korea Tourism Organization's TourAPI (KorService2) on data.go.kr.

    Needs a service key from data.go.kr; pass the "Decoding" form of the key, which this class
    URL-encodes itself. NOTE: written from the published parameter tables and not yet run against
    the live service, because no key was available. Check the first real run.
    """

    def __init__(self, service_key: str, fetch: GetJson | None = None):
        self._key = service_key
        self._fetch = fetch or http.get_json

    @classmethod
    def from_environment(cls, fetch: GetJson | None = None) -> "TourApi | None":
        key = os.environ.get("TOURAPI_KEY", "").strip()
        return cls(key, fetch) if key else None

    def _call(self, operation: str, params: dict[str, Any]) -> list[dict[str, Any]]:
        data = self._fetch(f"{TOURAPI_BASE}/{operation}", {
            "serviceKey": self._key, "MobileOS": "ETC", "MobileApp": "gildongmu", "_type": "json", **params,
        })
        response = data.get("response", {})
        code = response.get("header", {}).get("resultCode")
        if code != "0000":
            raise RuntimeError(f"TourAPI {operation} failed: {response.get('header', {}).get('resultMsg', data)}")
        items = response.get("body", {}).get("items") or {}
        found = items.get("item", []) if isinstance(items, dict) else []
        return [found] if isinstance(found, dict) else list(found)

    def near(self, point: Point, radius_m: int = 10_000, limit: int = 30) -> list[Candidate]:
        candidates: list[Candidate] = []
        for content_type in TOURAPI_CONTENT_TYPES:
            for item in self._call("locationBasedList2", {
                "mapX": point[1], "mapY": point[0], "radius": min(radius_m, 20_000),  # the API's maximum
                "arrange": "E", "contentTypeId": content_type, "numOfRows": limit, "pageNo": 1,
            }):
                try:
                    location = (float(item["mapy"]), float(item["mapx"]))
                except (KeyError, TypeError, ValueError):
                    continue
                candidates.append(Candidate(
                    key=f"tourapi:{item['contentid']}",
                    title=item.get("title", "").strip(),
                    location=location,
                    kind="tourapi",
                    extra={"contentid": item["contentid"], "address": item.get("addr1", "")},
                ))
        return candidates

    def fill_text(self, candidates: list[Candidate]) -> None:
        """Fetches the overview of each TourAPI candidate. One request each, so call it on a short list."""
        for candidate in candidates:
            if candidate.kind != "tourapi" or candidate.text:
                continue
            items = self._call("detailCommon2", {"contentId": candidate.extra["contentid"]})
            if not items:
                continue
            candidate.text = _strip_tags(items[0].get("overview") or "").strip()
            candidate.source_title = f"한국관광공사 TourAPI: {candidate.title} (contentId {candidate.extra['contentid']})"
            candidate.source_url = TOURAPI_DATASET


def _strip_tags(text: str) -> str:
    """TourAPI overviews carry the odd <br> tag."""
    import re
    return re.sub(r"<[^>]+>", " ", text).replace("&nbsp;", " ")
