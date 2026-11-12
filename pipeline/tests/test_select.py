from gildongmu_pipeline import select
from gildongmu_pipeline.geo import RouteGeometry
from gildongmu_pipeline.sources import Candidate

from test_geo import north

ROUTE = RouteGeometry([north(0), north(100)])


def candidate(title, km, text_length=600, offset_degrees=0.0, kind="wikipedia"):
    lat, lon = north(km)
    return Candidate(key=f"{kind}:{title}", title=title, location=(lat, lon + offset_degrees), text="가" * text_length, kind=kind)


def test_drops_roads_stations_schools_and_districts():
    dull = ["여주 나들목", "호법 분기점", "영동고속도로", "횡성휴게소", "강릉역", "둔내면", "원주시", "봉평초등학교", "둔내터널"]
    keep = ["오죽헌", "치악산", "신륵사", "에버랜드", "대관령", "이효석문학관"]
    placed = select.place([candidate(t, 10 + i) for i, t in enumerate(dull + keep)], ROUTE)
    assert sorted(p.candidate.title for p in placed) == sorted(keep)


def test_drops_places_far_from_the_road_and_duplicates():
    near = candidate("가까운 곳", 30, offset_degrees=0.05)   # about 4.4 km off
    far = candidate("먼 곳", 30, offset_degrees=0.15)        # about 13 km off
    placed = select.place([near, far, near], ROUTE)
    assert [p.candidate.title for p in placed] == ["가까운 곳"]
    assert placed[0].route_km == __import__("pytest").approx(30, abs=0.1)


def test_chooses_the_richest_places_but_keeps_them_apart():
    placed = select.place([
        candidate("가", 20, text_length=1500),
        candidate("나", 25, text_length=1400),   # rich, but 5 km from a richer one
        candidate("다", 50, text_length=400),
        candidate("라", 80, text_length=900),
        candidate("마", 90, text_length=100),    # too little text to write from
    ], ROUTE)
    chosen = select.choose(placed, limit=3, min_gap_km=15)
    assert [p.candidate.title for p in chosen] == ["가", "다", "라"]   # in route order


def test_respects_the_limit():
    placed = select.place([candidate(f"곳{i}", 10 + 20 * i) for i in range(5)], ROUTE)
    assert len(select.choose(placed, limit=2)) == 2


def test_a_curated_source_outranks_an_equal_wikipedia_article():
    placed = select.place([candidate("위키", 40), candidate("관광공사", 42, kind="tourapi")], ROUTE)
    assert [p.candidate.title for p in select.choose(placed, limit=1)] == ["관광공사"]


def test_alternatives_are_near_the_rejected_place_and_clear_of_the_others():
    placed = select.place([
        candidate("가", 20, text_length=1500),
        candidate("나", 25, text_length=1400),
        candidate("다", 50, text_length=1500),
        candidate("라", 45, text_length=1300),   # near "다", so it cannot replace "가"
    ], ROUTE)
    chosen = select.choose(placed, limit=2)
    rejected = next(p for p in chosen if p.candidate.title == "가")
    assert [p.candidate.title for p in select.alternatives(placed, chosen, rejected)] == ["나"]
