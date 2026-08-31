#!/usr/bin/env python3
"""Deterministic contract tests for the verified Latest JSON feed."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEEDS = ROOT / "testdata" / "feeds"
SOURCE = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt"


def load(name: str):
    return json.loads((FEEDS / name).read_text())


def ids(payload: dict) -> list[int]:
    return [item["mangaId"] for item in payload["data"]]


def unique_non_novel_ids(payload: dict) -> list[int]:
    seen: set[int] = set()
    result: list[int] = []
    for item in payload["data"]:
        if item["type"] == "novel" or item["mangaId"] in seen:
            continue
        seen.add(item["mangaId"])
        result.append(item["mangaId"])
    return result


def test_flat_schema_and_chapter_summaries() -> None:
    page1 = load("latest_page1.json")
    assert page1["success"] is True
    assert len(page1["data"]) == 18
    item = page1["data"][0]
    assert {"mangaId", "mangaSlug", "mangaTitle", "coverImage", "type", "chapters"}.issubset(item)
    chapter = item["chapters"][0]
    assert {"id", "slug", "number", "language", "publishedAt", "supportMode", "coinsRequired", "hasShortlink", "lockedForever", "lockedByCoins", "lockedByExclusive"}.issubset(chapter)


def test_page_parameter_is_authoritative_and_disjoint() -> None:
    page1 = set(ids(load("latest_page1.json")))
    page2 = set(ids(load("latest_page2.json")))
    page3 = set(ids(load("latest_page3.json")))
    assert page1.isdisjoint(page2)
    assert page1.isdisjoint(page3)
    assert page2.isdisjoint(page3)


def test_server_order_crosses_page_boundaries() -> None:
    page1 = load("latest_page1.json")["data"]
    page2 = load("latest_page2.json")["data"]
    first_page_date = page1[-1]["chapters"][0]["publishedAt"]
    second_page_date = page2[0]["chapters"][0]["publishedAt"]
    assert second_page_date < first_page_date


def test_short_page_is_not_termination() -> None:
    assert len(load("latest_page6.json")["data"]) == 17
    edge = load("latest_edge_fixtures.json")
    assert len(edge["short_page"]["data"]) == 1


def test_empty_page_is_termination() -> None:
    edge = load("latest_edge_fixtures.json")
    assert edge["empty"] == {"success": True, "data": []}


def test_duplicate_and_novel_policy() -> None:
    edge = load("latest_edge_fixtures.json")["duplicate_and_novel"]
    assert unique_non_novel_ids(edge) == [1]


def test_missing_optional_fields() -> None:
    edge = load("latest_edge_fixtures.json")["missing_optional"]["data"][0]
    assert edge["mangaId"] == 4 and edge["mangaTitle"] == "Minimal"
    assert edge["chapters"] == []


def test_malformed_payload_is_not_successful_data() -> None:
    try:
        json.loads(load("latest_edge_fixtures.json")["malformed"])
    except json.JSONDecodeError:
        pass
    else:
        raise AssertionError("malformed Latest payload unexpectedly decoded")


def test_source_uses_dedicated_latest_contract() -> None:
    source = SOURCE.read_text()
    latest_start = source.index("override fun latestUpdatesRequest")
    search_start = source.index("// ---- Search ----")
    latest_block = source[latest_start:search_start]
    assert "page=${page.coerceAtLeast(1)}" in latest_block
    assert "ProComicLatestResponse" in latest_block
    assert "extractSeriesList(body, \"LATEST\"" not in latest_block
    assert "hasNextPage = hasNextPage" in latest_block


def main() -> None:
    test_flat_schema_and_chapter_summaries()
    test_page_parameter_is_authoritative_and_disjoint()
    test_server_order_crosses_page_boundaries()
    test_short_page_is_not_termination()
    test_empty_page_is_termination()
    test_duplicate_and_novel_policy()
    test_missing_optional_fields()
    test_malformed_payload_is_not_successful_data()
    test_source_uses_dedicated_latest_contract()
    print("latest contract tests: PASS (flat schema, pages, ordering, short page, termination, chapters, malformed)")


if __name__ == "__main__":
    main()
