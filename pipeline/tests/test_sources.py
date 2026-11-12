import pytest

from gildongmu_pipeline.sources import TOURAPI_BASE, WIKIPEDIA_API, TourApi, Wikipedia


class FakeHttp:
    """Answers each request from a table keyed by (url, a distinguishing parameter)."""

    def __init__(self, answers):
        self.answers = answers
        self.calls = []

    def __call__(self, url, params):
        self.calls.append((url, params))
        for (wanted_url, key, value), answer in self.answers.items():
            if url == wanted_url and str(params.get(key)) == str(value):
                return answer
        raise AssertionError(f"Unexpected request: {url} {params}")


def test_wikipedia_finds_places_and_fills_in_their_text():
    http = FakeHttp({
        (WIKIPEDIA_API, "list", "geosearch"): {"query": {"geosearch": [
            {"pageid": 11, "title": "오죽헌", "lat": 37.779, "lon": 128.879, "dist": 800.0},
            {"pageid": 22, "title": "경포대", "lat": 37.795, "lon": 128.896, "dist": 2400.0},
        ]}},
        (WIKIPEDIA_API, "prop", "extracts|info"): {"query": {"pages": [
            {"pageid": 11, "title": "오죽헌", "extract": " 오죽헌은 강릉에 있는 집이다. ", "fullurl": "https://ko.wikipedia.org/wiki/오죽헌"},
            {"pageid": 22, "title": "경포대"},  # an article with no opening text
        ]}},
    })
    wikipedia = Wikipedia(http)
    candidates = wikipedia.near((37.78, 128.88), radius_m=50_000)
    assert [c.key for c in candidates] == ["wikipedia:11", "wikipedia:22"]
    assert candidates[0].location == (37.779, 128.879)
    assert http.calls[0][1]["gscoord"] == "37.78|128.88"
    assert http.calls[0][1]["gsradius"] == 10_000, "the API allows at most 10 km"

    wikipedia.fill_text(candidates)
    assert candidates[0].text == "오죽헌은 강릉에 있는 집이다."
    assert candidates[0].source_url == "https://ko.wikipedia.org/wiki/오죽헌"
    assert candidates[0].source_title == "위키백과: 오죽헌"
    assert candidates[1].text == ""
    assert http.calls[1][1]["pageids"] == "11|22"


def test_wikipedia_asks_for_text_twenty_pages_at_a_time():
    pages = [{"pageid": i, "title": f"곳{i}", "lat": 37.0, "lon": 127.0} for i in range(45)]
    http = FakeHttp({
        (WIKIPEDIA_API, "list", "geosearch"): {"query": {"geosearch": pages}},
        (WIKIPEDIA_API, "prop", "extracts|info"): {"query": {"pages": []}},
    })
    wikipedia = Wikipedia(http)
    wikipedia.fill_text(wikipedia.near((37.0, 127.0)))
    batches = [len(params["pageids"].split("|")) for _, params in http.calls[1:]]
    assert batches == [20, 20, 5]


def ok(items):
    return {"response": {"header": {"resultCode": "0000", "resultMsg": "OK"}, "body": {"items": items, "totalCount": 1}}}


def test_tourapi_finds_places_and_fills_in_their_overview():
    http = FakeHttp({
        (f"{TOURAPI_BASE}/locationBasedList2", "contentTypeId", 12): ok({"item": [
            {"contentid": "126508", "contenttypeid": "12", "title": "오죽헌", "addr1": "강원특별자치도 강릉시 율곡로", "mapx": "128.879", "mapy": "37.779"},
            {"contentid": "999", "title": "좌표 없는 곳", "mapx": "", "mapy": ""},
        ]}),
        # A single result comes back as an object rather than a list; no results as an empty string.
        (f"{TOURAPI_BASE}/locationBasedList2", "contentTypeId", 14): ok(""),
        (f"{TOURAPI_BASE}/detailCommon2", "contentId", "126508"): ok({"item": {"contentid": "126508", "overview": "오죽헌은 <br>신사임당이 태어난 집이다.&nbsp;"}}),
    })
    tourapi = TourApi("KEY", http)
    candidates = tourapi.near((37.78, 128.88))
    assert [c.key for c in candidates] == ["tourapi:126508"]
    assert candidates[0].location == (37.779, 128.879)
    first = http.calls[0][1]
    assert (first["mapX"], first["mapY"]) == (128.88, 37.78), "mapX is longitude, mapY is latitude"
    assert first["serviceKey"] == "KEY" and first["_type"] == "json" and first["MobileOS"] == "ETC"

    tourapi.fill_text(candidates)
    assert candidates[0].text == "오죽헌은  신사임당이 태어난 집이다."
    assert "126508" in candidates[0].source_title


def test_tourapi_reports_a_service_error():
    http = FakeHttp({
        (f"{TOURAPI_BASE}/locationBasedList2", "contentTypeId", 12): {
            "response": {"header": {"resultCode": "30", "resultMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}
        },
    })
    with pytest.raises(RuntimeError, match="SERVICE_KEY_IS_NOT_REGISTERED_ERROR"):
        TourApi("BAD", http).near((37.78, 128.88))


def test_tourapi_is_optional(monkeypatch):
    monkeypatch.delenv("TOURAPI_KEY", raising=False)
    assert TourApi.from_environment() is None
    monkeypatch.setenv("TOURAPI_KEY", " abc ")
    assert TourApi.from_environment() is not None
