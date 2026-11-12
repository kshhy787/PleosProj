import json
import os

import pytest

from conftest import REPO, SOURCE, good_draft
from gildongmu_pipeline import check, cli, package, select, speech
from gildongmu_pipeline.geo import RouteGeometry
from gildongmu_pipeline.sources import Candidate
from gildongmu_pipeline.writer import Draft


def placed(route, title, lat, lon):
    candidate = Candidate(key=f"wikipedia:{title}", title=title, location=(lat, lon), text=SOURCE, source_title=f"위키백과: {title}", source_url=f"https://ko.wikipedia.org/wiki/{title}", kind="wikipedia")
    projection = RouteGeometry([tuple(p) for p in route["polyline"]]).project(candidate.location)
    return select.Placed(candidate, projection.route_km, projection.offset_km)


def draft():
    data = good_draft()
    return Draft(data["name"], data["area"], data["title"], data["short"], data["long"], data["quiz"])


def test_the_route_file_is_the_bundled_package_without_its_places(bundled, tmp_path):
    route = package.route_of(bundled)
    assert "pois" not in route and route["stops"] == bundled["stops"] and route["generalQuiz"] == bundled["generalQuiz"]
    path = tmp_path / "r.route.json"
    path.write_text(json.dumps(route, ensure_ascii=False), encoding="utf-8")
    assert package.load_route(path) == route


def test_the_committed_route_file_matches_the_bundled_package(bundled):
    committed = package.load_route(package.Path(__file__).resolve().parents[1] / "routes" / "seoul-gangneung.route.json")
    assert committed == package.route_of(bundled)


def test_a_route_file_must_have_a_route(tmp_path):
    path = tmp_path / "bad.json"
    path.write_text('{"id": "x", "title": "y"}', encoding="utf-8")
    with pytest.raises(ValueError, match="origin, destination, polyline"):
        package.load_route(path)


def test_assembles_a_package_the_app_will_load(bundled, tmp_path):
    route = package.route_of(bundled)
    written = [
        (placed(route, "대관령", 37.688, 128.757), draft()),   # given out of route order on purpose
        (placed(route, "영릉", 37.308, 127.603), draft()),
    ]
    result = package.assemble(route, written, model="some-model")

    assert check.package_problems(result) == []
    assert [p["id"] for p in result["pois"]] == ["poi-01", "poi-02"]
    assert result["pois"][0]["sources"][0]["title"] == "위키백과: 영릉", "places are in the order the car reaches them"
    first = result["pois"][0]
    assert [v["seconds"] for v in first["story"]["variants"]] == [
        -(-speech.estimate_seconds(text) // 1) for text in (draft().short, draft().long)
    ]
    assert first["quiz"][0]["id"] == "poi-01-q1"
    assert first["quiz"][0]["evidence"], "the sentence each answer rests on is kept in the package"
    assert "choices" not in first["quiz"][0]
    assert result["generator"].endswith("some-model")

    out = tmp_path / "nested" / "package.json"
    package.write(result, out)
    assert json.loads(out.read_text(encoding="utf-8")) == result
    assert cli.main(["check", str(out)]) == 0


# Read by the app's own parser in app/src/test/java/app/gildongmu/core/RoutePackageParserTest.kt.
APP_FIXTURE = REPO / "app" / "src" / "test" / "resources" / "pipeline-package.json"


def test_the_package_the_app_tests_load_is_what_the_pipeline_writes(bundled):
    """The pipeline and the app are written in different languages and share only the package
    format, so both are tested against one file: this test checks that the file is still exactly
    what the pipeline writes, and the app's test checks that the app reads it.

    After changing the format on purpose, rewrite the file by running this test with
    GILDONGMU_UPDATE_FIXTURES=1, then run the app's tests.
    """
    route = package.route_of(bundled)
    data = good_draft()
    data["quiz"].append({
        "prompt": "대관령은 어디와 어디를 잇는 관문일까요?",
        "answers": ["영동과 영서"],
        "choices": ["영동과 영서", "서울과 부산"],
        "explanation": "영동과 영서를 잇는 관문이에요.",
        "evidence": "높이 832m로 영동과 영서를 잇는 관문이다.",
    })
    written = Draft(data["name"], data["area"], data["title"], data["short"], data["long"], data["quiz"])
    result = package.assemble(route, [(placed(route, "대관령", 37.688, 128.757), written)], model="fixture")
    result["generatedOn"] = "2026-01-01"  # the one field that would otherwise change every day

    if os.environ.get("GILDONGMU_UPDATE_FIXTURES"):
        package.write(result, APP_FIXTURE)
    assert json.loads(APP_FIXTURE.read_text(encoding="utf-8")) == result


def test_refuses_to_assemble_something_the_app_would_reject(bundled):
    route = package.route_of(bundled)
    bad = draft()
    bad.quiz = [bad.quiz[0] | {"answers": []}]
    with pytest.raises(ValueError, match="needs at least one answer"):
        package.assemble(route, [(placed(route, "대관령", 37.688, 128.757), bad)], model="m")


def test_check_command_reports_problems(bundled, tmp_path, capsys):
    bundled["pois"][0]["quiz"][0]["answers"] = []
    path = tmp_path / "broken.json"
    path.write_text(json.dumps(bundled, ensure_ascii=False), encoding="utf-8")
    assert cli.main(["check", str(path)]) == 1
    assert "needs at least one answer" in capsys.readouterr().out
