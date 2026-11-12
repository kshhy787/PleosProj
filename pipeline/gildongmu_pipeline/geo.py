"""Distances and positions along a route. Mirrors app/.../core/Geo.kt so both agree on kilometres."""

from __future__ import annotations

import math
from dataclasses import dataclass

EARTH_RADIUS_KM = 6371.0088

Point = tuple[float, float]  # (latitude, longitude) in degrees


def distance_km(a: Point, b: Point) -> float:
    """Great-circle distance in kilometres."""
    d_lat = math.radians(b[0] - a[0])
    d_lon = math.radians(b[1] - a[1])
    h = math.sin(d_lat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(d_lon / 2) ** 2
    return 2 * EARTH_RADIUS_KM * math.asin(min(1.0, math.sqrt(h)))


@dataclass(frozen=True)
class Projection:
    route_km: float
    offset_km: float


class RouteGeometry:
    """The route as a polyline with cumulative distance."""

    def __init__(self, points: list[Point]):
        if len(points) < 2:
            raise ValueError("A route needs at least two points")
        self.points = [(float(lat), float(lon)) for lat, lon in points]
        self._cumulative = [0.0]
        for previous, current in zip(self.points, self.points[1:]):
            self._cumulative.append(self._cumulative[-1] + distance_km(previous, current))
        self.length_km = self._cumulative[-1]
        if self.length_km <= 0:
            raise ValueError("A route needs a non-zero length")

    def point_at(self, km: float) -> Point:
        """The position `km` kilometres along the route, clamped to its ends."""
        target = min(max(km, 0.0), self.length_km)
        i = 1
        while i < len(self.points) - 1 and self._cumulative[i] < target:
            i += 1
        segment = self._cumulative[i] - self._cumulative[i - 1]
        t = 0.0 if segment <= 0 else (target - self._cumulative[i - 1]) / segment
        a, b = self.points[i - 1], self.points[i]
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

    def project(self, p: Point) -> Projection:
        """How far along the route `p` is, and how far off to the side."""
        best: Projection | None = None
        for i in range(1, len(self.points)):
            a, b = self.points[i - 1], self.points[i]
            km_per_deg_lat = 110.574
            km_per_deg_lon = 111.320 * math.cos(math.radians(a[0]))
            bx, by = (b[1] - a[1]) * km_per_deg_lon, (b[0] - a[0]) * km_per_deg_lat
            px, py = (p[1] - a[1]) * km_per_deg_lon, (p[0] - a[0]) * km_per_deg_lat
            length_sq = bx * bx + by * by
            t = 0.0 if length_sq <= 0 else min(1.0, max(0.0, (px * bx + py * by) / length_sq))
            offset = math.hypot(px - bx * t, py - by * t)
            along = self._cumulative[i - 1] + (self._cumulative[i] - self._cumulative[i - 1]) * t
            if best is None or offset < best.offset_km:
                best = Projection(along, offset)
        assert best is not None
        return best

    def sample(self, every_km: float) -> list[Point]:
        """Points spaced `every_km` apart along the route, including both ends."""
        if every_km <= 0:
            raise ValueError("every_km must be positive")
        count = max(1, math.ceil(self.length_km / every_km))
        return [self.point_at(self.length_km * i / count) for i in range(count + 1)]
