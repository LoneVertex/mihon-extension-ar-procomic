#!/usr/bin/env python3
"""Deterministic tests for the ProComic Details RSC parser contract.

The fixtures mirror strict immediate-value extraction and the explicit restricted
response path. Unrelated objects and malformed payloads remain diagnosable failures.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).parent


def skip_ws(body: str, start: int) -> int:
    while start < len(body) and body[start].isspace():
        start += 1
    return start


def extract_object(body: str, start: int) -> str | None:
    if start < 0 or start >= len(body) or body[start] != "{":
        return None
    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(body)):
        char = body[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return body[start : index + 1]
    return None


def object_after_key(body: str, key: str) -> str | None:
    marker = f'"{key}":'
    key_index = body.find(marker)
    if key_index < 0:
        return None
    value_start = skip_ws(body, key_index + len(marker))
    if value_start >= len(body) or body[value_start] != "{":
        return None
    return extract_object(body, value_start)


def json_value_after_key(body: str, key: str) -> Any | None:
    marker = f'"{key}":'
    key_index = body.find(marker)
    if key_index < 0:
        return None
    value_start = skip_ws(body, key_index + len(marker))
    try:
        value, _ = json.JSONDecoder().raw_decode(body[value_start:])
        return value
    except json.JSONDecodeError:
        return None


def string_after_key(body: str, key: str) -> str | None:
    value = json_value_after_key(body, key)
    return value if isinstance(value, str) else None


def restricted_title(body: str) -> str | None:
    marker = "صفحة معلومات "
    start = body.find(marker)
    if start < 0:
        return None
    title_start = start + len(marker)
    title_end = body.find(":", title_start)
    if title_end <= title_start:
        return None
    title = body[title_start:title_end].strip()
    return title or None


def restricted_summary(body: str) -> dict[str, Any]:
    marker = '"coverImage":'
    marker_index = body.find(marker)
    if marker_index < 0:
        return {}
    segment = body[marker_index : marker_index + 900]
    result: dict[str, Any] = {}
    for key in (
        "coverImage",
        "description",
        "latestChapterNumber",
        "latestChapterDate",
        "readHref",
        "readIsExternal",
    ):
        result[key] = string_after_key(segment, key)
    total = json_value_after_key(segment, "totalChapters")
    result["totalChapters"] = total if isinstance(total, int) else None
    return result


def parse_details(body: str, expected_id: int, expected_slug: str) -> dict[str, Any] | None:
    canonical = object_after_key(body, "series")
    if canonical is not None:
        value = json.loads(canonical)
        if (
            isinstance(value, dict)
            and value.get("id") == expected_id
            and isinstance(value.get("title"), str)
            and value["title"].strip()
            and value.get("slug") == expected_slug
            and isinstance(value.get("type"), str)
            and value["type"].strip()
        ):
            return {"kind": "complete", **value}
        return None

    restricted = json_value_after_key(body, "restricted")
    if restricted is not True:
        return None
    params = json_value_after_key(body, "params")
    if not isinstance(params, dict):
        return None
    try:
        actual_id = int(params["id"])
    except (KeyError, TypeError, ValueError):
        return None
    if actual_id != expected_id or params.get("slug") != expected_slug:
        return None
    title = restricted_title(body)
    if not title:
        return None
    return {
        "kind": "restricted",
        "id": actual_id,
        "title": title,
        "type": params.get("type"),
        "slug": params.get("slug"),
        "restricted": True,
        "summary": restricted_summary(body),
    }


def expect_failure(path: Path, expected_id: int, expected_slug: str) -> None:
    result = parse_details(path.read_text(errors="replace"), expected_id, expected_slug)
    assert result is None, f"{path.name}: expected diagnosable failure, got {result!r}"


def main() -> None:
    canonical_678 = parse_details(
        (ROOT / "canonical_678.rsc").read_text(errors="replace"),
        678,
        "dream-reincarnation-i-became-the-supreme-master-of-martial-arts",
    )
    assert canonical_678 and canonical_678["kind"] == "complete"
    assert canonical_678["title"] == "Dream Reincarnation: I Became the Supreme Master of Martial Arts"
    assert canonical_678["type"] == "manhua"

    canonical_690 = parse_details(
        (ROOT / "canonical_690.rsc").read_text(errors="replace"),
        690,
        "after-severing-our-ties-all-my-summons-became-dark-creatures",
    )
    assert canonical_690 and canonical_690["kind"] == "complete"
    assert canonical_690["title"] == "After Severing Our Ties, All My Summons Became Dark Creatures"

    restricted_695 = parse_details(
        (ROOT / "restricted_695.rsc").read_text(errors="replace"),
        695,
        "day-walker",
    )
    assert restricted_695 and restricted_695["kind"] == "restricted"
    assert restricted_695["title"] == "Day Walker"
    assert restricted_695["summary"]["totalChapters"] == 10
    assert restricted_695["summary"]["latestChapterNumber"] == "10"
    assert restricted_695["summary"]["readHref"] == "/ar/day-walker-695"

    restricted_691 = parse_details(
        (ROOT / "restricted_691.rsc").read_text(errors="replace"),
        691,
        "monster-onna-kanbu-wa-osanaki-yuusha-wo-dekiai-suru",
    )
    assert restricted_691 and restricted_691["kind"] == "restricted"
    assert restricted_691["title"] == "Monster Onna Kanbu wa Osanaki Yuusha wo Dekiai suru"
    assert restricted_691["summary"]["totalChapters"] == 12
    assert restricted_691["summary"]["latestChapterNumber"] == "12"

    unsafe = (ROOT / "unsafe_series_null.rsc").read_text(errors="replace")
    assert object_after_key(unsafe, "series") is None
    assert parse_details(unsafe, 690, "wrong-params") is None
    for non_object in ("null", "[]", '"text"', "123"):
        payload = f'{{"series":{non_object},"params":{{"id":"690"}}}}'
        assert object_after_key(payload, "series") is None

    mismatched = '{"series":{"id":999,"title":"Wrong","slug":"wrong","type":"manhua"}}'
    assert parse_details(mismatched, 690, "wrong") is None

    fallback = json.loads((ROOT / "restricted_fallback_fixtures.json").read_text())
    assert fallback["details_summary"]["coverImage"] == ""
    assert fallback["details_summary"]["description"] == ""
    assert fallback["expected_manga"] == fallback["public_source"]

    source = (ROOT.parent.parent / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt").read_text()
    assert "RestrictedMangaFallback" in source
    assert ".tag(RestrictedMangaFallback::class.java" in source
    assert "fallback?.thumbnailUrl" in source
    assert "fallback?.description" in source

    expect_failure(ROOT / "malformed_details.rsc", 690, "anything")
    expect_failure(
        ROOT / "redirecting_678.rsc",
        678,
        "dream-reincarnation-i-became-the-supreme-master-of-martial-arts",
    )
    expect_failure(
        ROOT / "invalid_non_details.rsc",
        678,
        "dream-reincarnation-i-became-the-supreme-master-of-martial-arts",
    )

    print("details contract tests: PASS (canonical/restricted parsing, public metadata fallback, unsafe null, malformed, redirecting, invalid)")


if __name__ == "__main__":
    main()
