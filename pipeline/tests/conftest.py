import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

REPO = Path(__file__).resolve().parents[2]
BUNDLED = REPO / "app" / "src" / "main" / "assets" / "routes" / "seoul-gangneung.json"

# A source text, and a story and quiz that are faithful to it.
SOURCE = (
    "대관령은 강원특별자치도 평창군 대관령면과 강릉시 성산면 사이에 있는 고개이다. "
    "높이 832m로 영동과 영서를 잇는 관문이다. "
    "예로부터 고개가 험해서 대굴대굴 구르는 고개라는 뜻의 이름이 붙었다는 이야기가 전해 온다."
)
SHORT = "강릉으로 가는 길에는 대관령이 있어요. 대관령은 평창과 강릉 사이에 있는 고개로, 높이가 832미터나 돼요. 영동과 영서를 잇는 관문이지요."
LONG = (
    "강릉으로 가는 길에는 대관령이 있어요. 대관령은 평창군 대관령면과 강릉시 성산면 사이에 있는 고개예요. "
    "높이가 832미터나 돼서, 예로부터 영동과 영서를 잇는 관문이었어요. "
    "고개가 너무 험해서 대굴대굴 구르는 고개라는 뜻으로 이름이 붙었다는 이야기도 전해 와요. "
    "옛날 사람들은 이 높은 고개를 걸어서 넘었대요. 우리라면 걸어서 넘는 데 얼마나 걸릴까요?"
)


def good_draft(**changes):
    draft = {
        "usable": True,
        "reason": "",
        "name": "대관령",
        "area": "평창",
        "title": "구르는 고개, 대관령",
        "short": SHORT,
        "long": LONG,
        "quiz": [
            {
                "prompt": "대관령의 높이는 몇 미터일까요?",
                "answers": ["832"],
                "choices": [],
                "explanation": "대관령은 높이 832미터의 고개예요.",
                "evidence": "높이 832m로 영동과 영서를 잇는 관문이다.",
            }
        ],
    }
    draft.update(changes)
    return draft


@pytest.fixture
def bundled():
    return json.loads(BUNDLED.read_text(encoding="utf-8"))
