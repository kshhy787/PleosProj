"""A minimal JSON-over-HTTP GET, on the standard library only."""

from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Callable

# Wikimedia asks automated clients to identify themselves. Set GILDONGMU_CONTACT to an address or
# URL where the operator of this pipeline can be reached.
_CONTACT = os.environ.get("GILDONGMU_CONTACT", "contact not set")
USER_AGENT = f"gildongmu-pipeline/0.1 ({_CONTACT})"

GetJson = Callable[[str, dict[str, Any]], Any]


class HttpError(Exception):
    pass


def get_json(url: str, params: dict[str, Any], *, timeout: float = 20.0, attempts: int = 3) -> Any:
    """GETs `url` with `params` and returns the decoded JSON body. Retries on transient failures."""
    full = url + "?" + urllib.parse.urlencode(params)
    last: Exception | None = None
    for attempt in range(attempts):
        request = urllib.request.Request(full, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                body = response.read().decode("utf-8")
            try:
                return json.loads(body)
            except json.JSONDecodeError as e:
                # data.go.kr answers some errors (a bad key, for example) in XML even when JSON was asked for.
                raise HttpError(f"{url} did not return JSON: {body[:200]!r}") from e
        except urllib.error.HTTPError as e:
            if e.code < 500 and e.code != 429:
                raise HttpError(f"{url} answered HTTP {e.code}") from e
            last = e
        except (urllib.error.URLError, TimeoutError) as e:
            last = e
        time.sleep(1.5 * (attempt + 1))
    raise HttpError(f"{url} failed after {attempts} attempts: {last}")
