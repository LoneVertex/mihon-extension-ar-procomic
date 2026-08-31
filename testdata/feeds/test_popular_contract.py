#!/usr/bin/env python3
"""Deterministic contract tests for the verified Popular JSON feed."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEEDS = ROOT / "testdata" / "feeds"
SOURCE = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt"


def load(name: str):
    return json.loads((FEEDS / name).read_text())


def unique_non_novel_ids(payload: dict) -> list[int]:
    seen: set[int] = set()
    result: list[int] = []
    for item in payload["data"]:
        content = item["content"]
        if content["type"] == "novel" or content["id"] in seen:
            continue
        seen.add(content["id"])
        result.append(content["id"])
    return result


def test_live_nested_shape() -> None:
    default = load("popular_default.json")
    assert default["success"] is True
    assert len(default["data"]) == 37
    assert all(set(item) == {"content", "viewCount"} for item in default["data"])
    assert all({"id", "title", "slug", "type"}.issubset(item["content"]) for item in default["data"])
    assert all(isinstance(item["viewCount"], str) for item in default["data"])


def test_limit_variants_are_mapped_as_complete_returned_sets() -> None:
    assert len(load("popular_limit10.json")["data"]) == 19
    assert len(load("popular_limit100.json")["data"]) == 118
    assert unique_non_novel_ids(load("popular_default.json"))


def test_page_controls_are_not_continuation() -> None:
    page1 = [item["content"]["id"] for item in load("popular_page1.json")["data"]]
    page2 = [item["content"]["id"] for item in load("popular_page2.json")["data"]]
    assert page1 == page2


def test_duplicate_and_novel_policy() -> None:
    edge = load("popular_edge_fixtures.json")["duplicate"]
    assert unique_non_novel_ids(edge) == [1]


def test_empty_and_optional_fields() -> None:
    edge = load("popular_edge_fixtures.json")
    assert edge["empty"] == {"success": True, "data": []}
    minimal = edge["missing_optional"]["data"][0]["content"]
    assert minimal["id"] == 3 and minimal["title"] == "Minimal"
    assert "metadata" not in minimal


def test_malformed_payload_is_not_successful_data() -> None:
    try:
        json.loads(load("popular_edge_fixtures.json")["malformed"])
    except json.JSONDecodeError:
        pass
    else:
        raise AssertionError("malformed Popular payload unexpectedly decoded")


def test_source_uses_dedicated_popular_contract() -> None:
    source = SOURCE.read_text()
    popular_start = source.index("override fun popularMangaRequest")
    latest_start = source.index("// ---- Latest Updates ----")
    popular_block = source[popular_start:latest_start]
    assert "/api/public/content/popular-new?limit=20" in popular_block
    assert "ProComicPopularResponse" in popular_block
    assert "extractSeriesList(body, \"POPULAR\"" not in popular_block
    assert "hasNextPage = false" in popular_block


def main() -> None:
    test_live_nested_shape()
    test_limit_variants_are_mapped_as_complete_returned_sets()
    test_page_controls_are_not_continuation()
    test_duplicate_and_novel_policy()
    test_empty_and_optional_fields()
    test_malformed_payload_is_not_successful_data()
    test_source_uses_dedicated_popular_contract()
    print("popular contract tests: PASS (nested schema, limits, page identity, dedup, novel filter, malformed)")


if __name__ == "__main__":
    main()
