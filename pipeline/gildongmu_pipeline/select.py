"""Choosing which places along the route are worth a story."""

from __future__ import annotations

import re
from dataclasses import dataclass

from .geo import RouteGeometry
from .sources import Candidate

# Articles that have coordinates but make poor stories for a family driving past: roads and their
# junctions, stations, schools, administrative areas, companies, and lists.
_DULL_TITLE = re.compile(
    r"(나들목|분기점|요금소|휴게소|고속도로|국도|지방도|터널|교차로|정류장|"
    r"역$|역 \(|초등학교|중학교|고등학교|대학교|"
    r"[읍면동리]$|[시군구]$|[읍면동] \(|우체국|경찰서|소방서|주민센터|행정복지센터|"
    r"아파트|주식회사|\(기업\)|목록$)"
)


@dataclass(frozen=True)
class Placed:
    """A candidate with its position along the route."""

    candidate: Candidate
    route_km: float
    offset_km: float

    @property
    def score(self) -> float:
        # More source text means more to tell; a place far from the road is less "what we are passing".
        richness = min(len(self.candidate.text), 1500) / 1500
        curated = 0.3 if self.candidate.kind == "tourapi" else 0.0
        return richness + curated - self.offset_km / 20.0


def place(candidates: list[Candidate], route: RouteGeometry, *, max_offset_km: float = 8.0) -> list[Placed]:
    """Drops candidates too far from the road or without a usable subject, and locates the rest."""
    placed: list[Placed] = []
    seen: set[str] = set()
    for candidate in candidates:
        if candidate.key in seen or _DULL_TITLE.search(candidate.title):
            continue
        seen.add(candidate.key)
        projection = route.project(candidate.location)
        if projection.offset_km <= max_offset_km:
            placed.append(Placed(candidate, projection.route_km, projection.offset_km))
    return placed


def choose(placed: list[Placed], *, limit: int, min_gap_km: float = 15.0, min_text: int = 200) -> list[Placed]:
    """The best places, at least `min_gap_km` apart along the route, in the order the car reaches them.

    Spacing matters as much as quality: two stories triggered five kilometres apart would talk over
    each other, and a long stretch with nothing leaves the host silent.
    """
    ranked = sorted((p for p in placed if len(p.candidate.text) >= min_text), key=lambda p: p.score, reverse=True)
    chosen: list[Placed] = []
    for candidate in ranked:
        if len(chosen) >= limit:
            break
        if all(abs(candidate.route_km - other.route_km) >= min_gap_km for other in chosen):
            chosen.append(candidate)
    return sorted(chosen, key=lambda p: p.route_km)


def alternatives(placed: list[Placed], chosen: list[Placed], rejected: Placed, *, min_gap_km: float = 15.0, min_text: int = 200) -> list[Placed]:
    """Other candidates that could take `rejected`'s slot, best first."""
    others = [c for c in chosen if c is not rejected]
    taken = {c.candidate.key for c in chosen}
    pool = [
        p for p in placed
        if p.candidate.key not in taken and len(p.candidate.text) >= min_text
        and all(abs(p.route_km - other.route_km) >= min_gap_km for other in others)
        and abs(p.route_km - rejected.route_km) < min_gap_km
    ]
    return sorted(pool, key=lambda p: p.score, reverse=True)
