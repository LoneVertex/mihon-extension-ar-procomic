#!/usr/bin/env python3
"""Deterministic contract tests for Search cover-image mapping."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEARCH = ROOT / "testdata" / "search"
SOURCE = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt"
DTO = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicDto.kt"


def load(name: str) -> dict:
    return json.loads((SEARCH / name).read_text())


def search_thumbnail_url(series: dict) -> str | None:
    cover = series.get("coverImage")
    if isinstance(cover, str) and cover.startswith("http"):
        return cover

    thumbnail = series.get("thumbnail")
    if isinstance(thumbnail, str) and thumbnail.startswith("http"):
        return thumbnail

    if isinstance(thumbnail, str) and thumbnail.startswith("/") and not thumbnail.startswith("//"):
        cdn = series.get("cdn_path")
        if isinstance(cdn, str) and cdn.startswith("cdn") and cdn[3:].isdigit():
            return f"https://{cdn}.procomic.net{thumbnail}"

    return None


def test_absolute_values_have_priority() -> None:
    data = load("search_edge_fixtures.json")["data"]
    assert search_thumbnail_url(data[0]) == data[0]["coverImage"]
    assert search_thumbnail_url(data[2]) == data[2]["thumbnail"]


def test_relative_values_require_validated_cdn_path() -> None:
    data = load("search_edge_fixtures.json")["data"]
    assert search_thumbnail_url(data[1]) == "https://cdn2.procomic.net/287/image_series/1758447487254-valid.avif"
    assert search_thumbnail_url(data[3]) is None
    assert search_thumbnail_url(data[4]) is None


def search_matches_query(series: dict, query: str) -> bool:
    query_tokens = {token.casefold() for token in query.split() if len(token) > 1}
    if not query_tokens:
        return True
    searchable = " ".join(
        str(series.get(key) or "")
        for key in ("title", "slug", "description")
    ).casefold()
    return all(token in searchable.split() or token in searchable for token in query_tokens)


def test_server_false_positives_are_filtered_without_losing_page_metadata() -> None:
    payload = load("search_relevance_fixtures.json")
    page1 = payload["page1"]
    page2 = payload["page2"]
    assert [item["id"] for item in page1["data"] if search_matches_query(item, payload["query"])] == [1, 2]
    assert [item["id"] for item in page2["data"] if search_matches_query(item, payload["query"])] == []
    assert page1["meta"]["page"] == 1 and page1["meta"]["pages"] == 2
    assert page2["meta"]["page"] == 2 and page2["meta"]["pages"] == 2


def test_bounded_lookahead_recovers_later_matches_without_looping() -> None:
    payload = load("search_relevance_fixtures.json")["lookahead"]
    pages = payload["pages"]
    first_matches = [item["id"] for item in pages[0]["data"] if search_matches_query(item, "dragon")]
    empty_matches = [item["id"] for item in pages[1]["data"] if search_matches_query(item, "dragon")]
    later_matches = [item["id"] for item in pages[2]["data"] if search_matches_query(item, "dragon")]
    assert first_matches == [1]
    assert empty_matches == []
    assert later_matches == [5]
    assert payload["repeated_page"]["data"] == pages[1]["data"]


def test_source_and_dto_match_the_safe_contract() -> None:
    source = SOURCE.read_text()
    dto = DTO.read_text()
    mapping_start = source.index("private fun ProComicSeriesDto.toSManga")
    mapping = source[mapping_start:]

    assert 'thumbnail?.takeIf { it.startsWith("/") && !it.startsWith("//") }' in mapping
    assert 'Regex("cdn\\\\d+")' in mapping
    assert 'https://app.procomic.net$it' not in mapping
    assert 'matchesSearchQuery' in source
    assert 'MAX_SEARCH_LOOKAHEAD_PAGES' in source
    assert 'SEARCH_LOOKAHEAD' in source
    assert 'searchPageFingerprint' in source
    assert 'searchTokens' in source
    assert '@SerialName("cdn_path") val cdnPath: String? = null' in dto
    assert '@Serializable(with = IntOrMapSerializer::class)' in dto


def main() -> None:
    test_absolute_values_have_priority()
    test_relative_values_require_validated_cdn_path()
    test_server_false_positives_are_filtered_without_losing_page_metadata()
    test_bounded_lookahead_recovers_later_matches_without_looping()
    test_source_and_dto_match_the_safe_contract()
    print("search contract tests: PASS (validated thumbnails, relevance filtering, bounded lookahead)")


if __name__ == "__main__":
    main()
