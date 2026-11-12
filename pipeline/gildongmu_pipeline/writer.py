"""Has Claude write a story and quiz for one place, from that place's source text only."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Any, Callable

from . import check
from .sources import Candidate

DEFAULT_MODEL = os.environ.get("GILDONGMU_MODEL", "claude-opus-5-5")

SYSTEM_PROMPT = """\
당신은 '길동무'입니다. 가족이 탄 차 안에서, 지금 지나가는 곳의 이야기를 들려주는 진행자입니다. 듣는 사람은 운전하는 어른과 뒷자리의 아이들이고, 당신이 쓴 글은 음성으로만 전달됩니다.

한 장소의 근거 문서가 주어집니다. 그 문서에 있는 사실만으로 이야기 두 가지와 퀴즈를 만드세요.

사실에 관한 규칙
- 근거 문서에 없는 사실, 숫자, 연도, 이름은 쓰지 않습니다. 확실하지 않으면 뺍니다.
- 전설이나 설화는 "이런 이야기가 전해 와요"처럼, 전해 오는 이야기임을 밝힙니다.
- 방향이나 보이는 풍경(오른쪽에, 저 멀리 보이는)은 쓰지 않습니다. 차에서 실제로 보이는지 알 수 없기 때문입니다.
- 계절, 날씨, 시각에 따라 달라지는 말은 쓰지 않습니다.
- 지금 지나는, 여기는, 이곳은, 이제처럼 차가 그 자리에 있어야 맞는 말로 시작하지 않습니다. 이야기는 그 장소에 닿기 전이나 휴게소에서 쉬는 동안에도 들려주기 때문입니다. 장소나 지역의 이름으로 시작합니다.

말투와 형식
- 해요체로, 초등학생도 알아들을 쉬운 말로 씁니다. 한 문장에는 한 가지 내용만 담습니다.
- 괄호, 한자, 영어 약어, 기호를 쓰지 않습니다. 숫자는 아라비아 숫자로, 단위는 한글로 씁니다. 예: 832미터.
- short: 소리 내어 읽으면 10초에서 20초. 그 장소가 무엇인지와 가장 재미있는 사실 하나를 담습니다.
- long: 소리 내어 읽으면 30초에서 55초. short의 내용을 포함하고, 아이가 궁금해할 이야기를 더합니다. 마지막 문장은 차 안에서 서로 이야기 나눌 거리를 주는 질문이나 제안으로 끝냅니다.
- title: 이야기의 제목. 15자 이내.
- name: 문장 속에서 부를 장소 이름. "곧 지나갈 무엇 이야기"의 무엇에 들어갑니다.
- area: 장소가 있는 시나 군의 이름. 근거 문서에 없으면 빈 문자열로 둡니다.

퀴즈 규칙
- 두 문제나 세 문제. 이야기를 들은 아이가 대답할 수 있는 문제만 냅니다.
- answers: 정답으로 인정할 말들. 첫 번째가 대표 정답입니다. 한두 단어로 답할 수 있어야 합니다. 숫자가 정답이면 숫자만 적은 항목을 포함합니다. 예: 832.
- choices: 보기가 필요한 문제에만 두세 개를 씁니다. 보기를 쓰면 그 가운데 정확히 하나가 answers에 있어야 합니다. 보기가 없으면 빈 배열로 둡니다.
- explanation: 정답을 알려 준 뒤 덧붙일 한 문장.
- evidence: 정답의 근거가 되는 문장을 근거 문서에서 그대로 옮겨 적습니다. 한 글자도 바꾸지 않습니다.

장소가 이야깃거리가 되지 않으면 usable을 false로 하고 reason에 이유를 적습니다. 행정구역이나 도로, 회사처럼 가족이 흥미를 느끼기 어려운 대상이거나, 문서가 너무 짧아 사실을 확인할 수 없는 경우입니다. 그때 나머지 항목은 빈 값으로 둡니다.
"""

SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "usable": {"type": "boolean"},
        "reason": {"type": "string"},
        "name": {"type": "string"},
        "area": {"type": "string"},
        "title": {"type": "string"},
        "short": {"type": "string"},
        "long": {"type": "string"},
        "quiz": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "prompt": {"type": "string"},
                    "answers": {"type": "array", "items": {"type": "string"}},
                    "choices": {"type": "array", "items": {"type": "string"}},
                    "explanation": {"type": "string"},
                    "evidence": {"type": "string"},
                },
                "required": ["prompt", "answers", "choices", "explanation", "evidence"],
                "additionalProperties": False,
            },
        },
    },
    "required": ["usable", "reason", "name", "area", "title", "short", "long", "quiz"],
    "additionalProperties": False,
}


@dataclass
class Draft:
    """A story and quiz that passed every check."""

    name: str
    area: str
    title: str
    short: str
    long: str
    quiz: list[dict[str, Any]]


class WriterError(Exception):
    pass


class Writer:
    """Writes one place at a time. `client` is an `anthropic.Anthropic` (or anything with the same
    `messages.create`), so tests can pass a stand-in and no network is needed to test the logic."""

    def __init__(self, client: Any, model: str = DEFAULT_MODEL, log: Callable[[str], None] = lambda message: None):
        self._client = client
        self._model = model
        self._log = log

    def write(self, candidate: Candidate, route_title: str) -> Draft | None:
        """A checked draft for `candidate`, or None if the place is unusable or the model could not
        produce one that passes the checks in two attempts."""
        messages: list[dict[str, Any]] = [{
            "role": "user",
            "content": (
                f"경로: {route_title}\n장소: {candidate.title}\n\n"
                f"<근거_문서 제목=\"{candidate.source_title}\">\n{candidate.text}\n</근거_문서>"
            ),
        }]
        for attempt in (1, 2):
            raw, data = self._ask(messages)
            if not data.get("usable"):
                self._log(f"  {candidate.title}: 사용하지 않음 ({data.get('reason', '')})")
                return None

            story = check.story_problems(data["short"], data["long"], candidate.text)
            good, bad = [], []
            for question in data["quiz"]:
                problems = check.question_problems(question, candidate.text)
                (bad if problems else good).append((question, problems))

            # A story that passes with at least one sound question is used; unsound questions are dropped.
            if not story and good:
                for question, problems in bad:
                    self._log(f"  {candidate.title}: 문제 제외 \"{question.get('prompt', '')}\" ({'; '.join(problems)})")
                return Draft(
                    name=data["name"].strip() or candidate.title,
                    area=data["area"].strip(),
                    title=data["title"].strip() or candidate.title,
                    short=data["short"].strip(),
                    long=data["long"].strip(),
                    quiz=[question for question, _ in good],
                )

            feedback = story + [f"퀴즈 \"{q.get('prompt', '')}\": {p}" for q, problems in bad for p in problems]
            if not good:
                feedback.append("검사를 통과한 퀴즈가 하나도 없습니다.")
            self._log(f"  {candidate.title}: {attempt}번째 시도 반려 ({len(feedback)}건)")
            messages.append({"role": "assistant", "content": raw})
            messages.append({
                "role": "user",
                "content": "다음 문제를 고쳐서 전체를 다시 작성하세요.\n" + "\n".join(f"- {item}" for item in feedback),
            })
        return None

    def _ask(self, messages: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
        response = self._client.messages.create(
            model=self._model,
            max_tokens=3000,
            system=SYSTEM_PROMPT,
            messages=messages,
            output_config={"format": {"type": "json_schema", "schema": SCHEMA}},
        )
        if response.stop_reason == "refusal":
            return "{}", {"usable": False, "reason": "모델이 작성을 거절함"}
        if response.stop_reason == "max_tokens":
            raise WriterError("The model ran out of output tokens before finishing the JSON")
        raw = next((block.text for block in response.content if block.type == "text"), "")
        try:
            return raw, json.loads(raw)
        except json.JSONDecodeError as e:
            raise WriterError(f"The model did not return JSON: {raw[:200]!r}") from e
