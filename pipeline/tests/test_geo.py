import pytest

from gildongmu_pipeline.geo import RouteGeometry, distance_km

KM_PER_DEGREE = 111.19508  # of latitude, for the earth radius in geo.py


def north(km):
    return (37.0 + km / KM_PER_DEGREE, 127.0)


def test_distance_matches_a_known_pair():
    # Seoul City Hall to Busan City Hall is about 325 km in a straight line.
    assert distance_km((37.5663, 126.9779), (35.1798, 129.0750)) == pytest.approx(325, abs=3)


def test_route_length_and_points_along_it():
    route = RouteGeometry([north(0), north(40), north(100)])
    assert route.length_km == pytest.approx(100, abs=0.01)
    assert route.point_at(25)[0] == pytest.approx(north(25)[0], abs=1e-6)
    assert route.point_at(500) == pytest.approx(north(100))


def test_projects_a_place_beside_the_road_onto_it():
    route = RouteGeometry([north(0), north(100)])
    lat, lon = north(42)
    projection = route.project((lat, lon + 0.03))
    assert projection.route_km == pytest.approx(42, abs=0.05)
    assert projection.offset_km == pytest.approx(2.66, abs=0.1)


def test_sampling_covers_both_ends_at_even_spacing():
    route = RouteGeometry([north(0), north(100)])
    points = route.sample(8)
    assert points[0] == pytest.approx(north(0))
    assert points[-1] == pytest.approx(north(100))
    assert len(points) == 14  # 13 gaps of about 7.7 km
    gaps = [distance_km(a, b) for a, b in zip(points, points[1:])]
    assert max(gaps) <= 8.0 and min(gaps) == pytest.approx(max(gaps), abs=0.01)


def test_a_route_needs_two_points():
    with pytest.raises(ValueError):
        RouteGeometry([north(0)])


def test_agrees_with_the_app_on_the_bundled_route(bundled):
    # The Kotlin test asserts the same figure (about 205.5 km) for the same polyline.
    route = RouteGeometry([tuple(p) for p in bundled["polyline"]])
    assert route.length_km == pytest.approx(205.5, abs=1.0)
    kms = [route.project((poi["lat"], poi["lon"])).route_km for poi in bundled["pois"]]
    assert kms == sorted(kms)
