"""Command line: list candidate places, build a route package, or check one."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from . import check, package, select
from .geo import RouteGeometry
from .sources import Candidate, TourApi, Wikipedia
from .writer import DEFAULT_MODEL, Writer


def _say(message: str) -> None:
    print(message, file=sys.stderr)


def gather(route: dict[str, Any], *, every_km: float, wikipedia: Wikipedia, tourapi: TourApi | None) -> list[select.Placed]:
    """Every usable candidate near the route, with its text and its position along the route."""
    geometry = RouteGeometry([tuple(p) for p in route["polyline"]])
    found: dict[str, Candidate] = {}
    points = geometry.sample(every_km)
    for number, point in enumerate(points, start=1):
        _say(f"장소 찾는 중 {number}/{len(points)}")
        for candidate in wikipedia.near(point):
            found.setdefault(candidate.key, candidate)
        if tourapi is not None:
            for candidate in tourapi.near(point):
                found.setdefault(candidate.key, candidate)

    placed = select.place(list(found.values()), geometry)
    _say(f"경로 근처 후보 {len(placed)}곳의 설명을 가져오는 중")
    wikipedia.fill_text([p.candidate for p in placed])
    if tourapi is not None:
        # One request per place, so only for the ones close enough to the road to be chosen.
        tourapi.fill_text([p.candidate for p in placed if p.offset_km <= 5.0])
    return placed


def command_candidates(args: argparse.Namespace) -> int:
    route = package.load_route(args.route)
    placed = gather(route, every_km=args.every_km, wikipedia=Wikipedia(), tourapi=None if args.no_tourapi else TourApi.from_environment())
    chosen = {p.candidate.key for p in select.choose(placed, limit=args.max_pois, min_gap_km=args.min_gap_km)}
    for p in sorted(placed, key=lambda p: p.route_km):
        mark = "*" if p.candidate.key in chosen else " "
        print(f"{mark} {p.route_km:6.1f}km  +{p.offset_km:3.1f}km  {len(p.candidate.text):5d}자  {p.candidate.title}  [{p.candidate.kind}]")
    _say(f"* 표시가 이야기를 쓸 {len(chosen)}곳입니다.")
    return 0


def command_build(args: argparse.Namespace) -> int:
    import anthropic  # imported here so that `check` and `candidates` work without the SDK installed

    route = package.load_route(args.route)
    placed = gather(route, every_km=args.every_km, wikipedia=Wikipedia(), tourapi=None if args.no_tourapi else TourApi.from_environment())
    chosen = select.choose(placed, limit=args.max_pois, min_gap_km=args.min_gap_km)
    if not chosen:
        _say("이야기를 쓸 만한 장소를 찾지 못했습니다.")
        return 1

    writer = Writer(anthropic.Anthropic(), model=args.model, log=_say)
    written = []
    for slot in list(chosen):
        # If a place turns out to be unusable, try the next best one near the same stretch of road.
        for option in [slot, *select.alternatives(placed, chosen, slot, min_gap_km=args.min_gap_km)[:2]]:
            _say(f"이야기 쓰는 중: {option.candidate.title} ({option.route_km:.0f}km)")
            draft = writer.write(option.candidate, route["title"])
            if draft is not None:
                written.append((option, draft))
                break

    if not written:
        _say("검사를 통과한 이야기가 없습니다.")
        return 1
    result = package.assemble(route, written, model=args.model)
    package.write(result, args.out)
    _say(f"{args.out}: 장소 {len(result['pois'])}곳, 퀴즈 {sum(len(p['quiz']) for p in result['pois'])}문제")
    return 0


def command_check(args: argparse.Namespace) -> int:
    problems = check.package_problems(json.loads(args.package.read_text(encoding="utf-8")))
    for problem in problems:
        print(f"- {problem}")
    print("문제 없음" if not problems else f"문제 {len(problems)}건")
    return 1 if problems else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="gildongmu-pipeline", description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    def route_options(p: argparse.ArgumentParser) -> None:
        p.add_argument("route", type=Path, help="route file (see routes/)")
        p.add_argument("--max-pois", type=int, default=8, help="how many places to write stories for")
        p.add_argument("--min-gap-km", type=float, default=15.0, help="minimum distance between two places along the route")
        p.add_argument("--every-km", type=float, default=8.0, help="how often along the route to look for places")
        p.add_argument("--no-tourapi", action="store_true", help="use Wikipedia only, even if TOURAPI_KEY is set")

    p = commands.add_parser("candidates", help="list the places found along a route, without calling the model")
    route_options(p)
    p.set_defaults(run=command_candidates)

    p = commands.add_parser("build", help="write a route package (needs ANTHROPIC_API_KEY)")
    route_options(p)
    p.add_argument("--out", type=Path, required=True, help="where to write the package JSON")
    p.add_argument("--model", default=DEFAULT_MODEL, help=f"Claude model id (default {DEFAULT_MODEL})")
    p.set_defaults(run=command_build)

    p = commands.add_parser("check", help="check a route package the way the app will")
    p.add_argument("package", type=Path)
    p.set_defaults(run=command_check)

    args = parser.parse_args(argv)
    return args.run(args)
