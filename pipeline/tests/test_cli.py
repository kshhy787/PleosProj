"""The whole build, with stand-ins for Wikipedia and Claude so that it needs no network and no key."""

import json
import sys
from types import SimpleNamespace

from conftest import SOURCE, good_draft
from gildongmu_pipeline import check, cli
from gildongmu_pipeline.sources import WIKIPEDIA_API


def test_build_writes_a_valid_package(bundled, tmp_path, monkeypatch):
    route_file = tmp_path / "route.json"
    route_file.write_text(json.dumps({k: bundled[k] for k in ("id", "title", "origin", "destination", "polyline", "stops")}, ensure_ascii=False), encoding="utf-8")

    # Wikipedia: the same three articles wherever along the route it is asked.
    articles = [
        {"pageid": 1, "title": "영릉", "lat": 37.308, "lon": 127.603},
        {"pageid": 2, "title": "대관령", "lat": 37.688, "lon": 128.757},
        {"pageid": 3, "title": "여주 나들목", "lat": 37.265, "lon": 127.645},   # a junction: never chosen
    ]

    # An article's opening section; long enough to be worth writing from.
    extract = SOURCE + (
        " 고개 아래로는 옛길이 남아 있어 지금도 걸어서 넘을 수 있다. 겨울에는 눈이 많이 내리는 곳으로도 알려져 있다."
        " 고갯마루에는 바람이 세게 불어 풍력 발전기가 늘어서 있고, 주변 목장에서는 양과 소를 기른다."
    )
    assert len(extract) >= 200

    def fake_get_json(url, params, **_):
        assert url == WIKIPEDIA_API
        if params.get("list") == "geosearch":
            return {"query": {"geosearch": articles}}
        ids = [int(i) for i in params["pageids"].split("|")]
        return {"query": {"pages": [{"pageid": i, "extract": extract, "fullurl": f"https://ko.wikipedia.org/?curid={i}"} for i in ids]}}

    monkeypatch.setattr("gildongmu_pipeline.http.get_json", fake_get_json)

    # Claude: the first place is declined as unusable, the second gets a sound draft.
    replies = [good_draft(usable=False, reason="테스트"), good_draft()]
    requests = []

    class FakeAnthropic:
        def __init__(self):
            self.messages = self

        def create(self, **request):
            requests.append(request)
            return SimpleNamespace(stop_reason="end_turn", content=[SimpleNamespace(type="text", text=json.dumps(replies.pop(0), ensure_ascii=False))])

    monkeypatch.setitem(sys.modules, "anthropic", SimpleNamespace(Anthropic=FakeAnthropic))
    monkeypatch.delenv("TOURAPI_KEY", raising=False)

    out = tmp_path / "out.json"
    assert cli.main(["build", str(route_file), "--out", str(out), "--model", "test-model", "--every-km", "60"]) == 0

    result = json.loads(out.read_text(encoding="utf-8"))
    assert check.package_problems(result) == []
    assert [p["name"] for p in result["pois"]] == ["대관령"], "one place was declined, the junction was never a candidate"
    assert result["stops"] == bundled["stops"]
    assert len(requests) == 2 and all(r["model"] == "test-model" for r in requests)
