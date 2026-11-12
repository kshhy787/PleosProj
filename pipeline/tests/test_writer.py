import json
from types import SimpleNamespace

import pytest

from conftest import SOURCE, good_draft
from gildongmu_pipeline.sources import Candidate
from gildongmu_pipeline.writer import SCHEMA, Writer, WriterError

PLACE = Candidate(key="wikipedia:1", title="대관령", location=(37.688, 128.757), text=SOURCE, source_title="위키백과: 대관령", source_url="https://ko.wikipedia.org/wiki/대관령", kind="wikipedia")


class FakeClient:
    """Stands in for anthropic.Anthropic: replies with the queued drafts and records each request."""

    def __init__(self, *replies):
        self.replies = list(replies)
        self.requests = []
        self.messages = self

    def create(self, **request):
        self.requests.append(request)
        reply = self.replies.pop(0)
        if isinstance(reply, SimpleNamespace):
            return reply
        text = reply if isinstance(reply, str) else json.dumps(reply, ensure_ascii=False)
        return SimpleNamespace(stop_reason="end_turn", content=[SimpleNamespace(type="text", text=text)])


def test_a_sound_draft_is_accepted_first_time():
    client = FakeClient(good_draft())
    draft = Writer(client, model="some-model").write(PLACE, "서울 → 강릉")
    assert draft is not None and draft.name == "대관령" and len(draft.quiz) == 1
    request = client.requests[0]
    assert request["model"] == "some-model"
    assert request["output_config"] == {"format": {"type": "json_schema", "schema": SCHEMA}}
    assert SOURCE in request["messages"][0]["content"], "the source text is what the model writes from"
    assert "서울 → 강릉" in request["messages"][0]["content"]


def test_an_unusable_place_is_skipped_without_a_second_request():
    client = FakeClient(good_draft(usable=False, reason="행정구역"))
    assert Writer(client).write(PLACE, "서울 → 강릉") is None
    assert len(client.requests) == 1


def test_an_invented_number_is_sent_back_with_the_reason():
    bad = good_draft(short=good_draft()["short"].replace("832", "1832"))
    client = FakeClient(bad, good_draft())
    draft = Writer(client).write(PLACE, "서울 → 강릉")
    assert draft is not None and "832미터" in draft.short
    retry = client.requests[1]["messages"]
    assert [m["role"] for m in retry] == ["user", "assistant", "user"]
    assert "1832" in retry[2]["content"] and "근거 문서에 없는 숫자" in retry[2]["content"]


def test_gives_up_after_two_failed_attempts():
    bad = good_draft(short="대관령(大關嶺)")
    client = FakeClient(bad, bad)
    assert Writer(client).write(PLACE, "서울 → 강릉") is None
    assert len(client.requests) == 2


def test_an_ungrounded_question_is_dropped_but_the_rest_is_kept():
    invented = {"prompt": "대관령에 사는 동물은?", "answers": ["산양"], "choices": [], "explanation": "산양이 살아요.", "evidence": "대관령에는 산양이 산다."}
    client = FakeClient(good_draft(quiz=[good_draft()["quiz"][0], invented]))
    notes = []
    draft = Writer(client, log=notes.append).write(PLACE, "서울 → 강릉")
    assert draft is not None and [q["prompt"] for q in draft.quiz] == ["대관령의 높이는 몇 미터일까요?"]
    assert len(client.requests) == 1
    assert any("문제 제외" in note for note in notes)


def test_a_draft_with_no_sound_question_is_sent_back():
    invented = {"prompt": "대관령에 사는 동물은?", "answers": ["산양"], "choices": [], "explanation": "", "evidence": "대관령에는 산양이 산다."}
    client = FakeClient(good_draft(quiz=[invented]), good_draft())
    assert Writer(client).write(PLACE, "서울 → 강릉") is not None
    assert "검사를 통과한 퀴즈가 하나도 없습니다" in client.requests[1]["messages"][2]["content"]


def test_a_refusal_counts_as_unusable():
    client = FakeClient(SimpleNamespace(stop_reason="refusal", content=[]))
    assert Writer(client).write(PLACE, "서울 → 강릉") is None


def test_truncated_or_malformed_output_is_an_error():
    with pytest.raises(WriterError, match="ran out of output tokens"):
        Writer(FakeClient(SimpleNamespace(stop_reason="max_tokens", content=[]))).write(PLACE, "서울 → 강릉")
    with pytest.raises(WriterError, match="did not return JSON"):
        Writer(FakeClient("이야기입니다")).write(PLACE, "서울 → 강릉")
