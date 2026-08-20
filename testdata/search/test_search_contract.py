#!/usr/bin/env python3
"""Deterministic contract tests for Search mapping, relevance, and bounded pagination."""

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


def search_tokens(value: str) -> set[str]:
    import re
    return {token.casefold() for token in re.findall(r"[\w]+", value, flags=re.UNICODE) if len(token) > 1}


def title_like_matches(series: dict, query: str) -> bool:
    metadata = series.get("metadata") or {}
    title_like = " ".join(
        [
            str(series.get("title") or ""),
            str(series.get("slug") or ""),
            str(metadata.get("originalTitle") or ""),
            " ".join(str(item) for item in (metadata.get("altTitles") or [])),
        ]
    )
    available = search_tokens(title_like)
    return search_tokens(query).issubset(available)


def test_server_false_positives_are_filtered_without_losing_page_metadata() -> None:
    payload = load("search_relevance_fixtures.json")
    page1 = payload["page1"]
    page2 = payload["page2"]
    assert [item["id"] for item in page1["data"] if title_like_matches(item, payload["query"])] == [1, 2]
    assert [item["id"] for item in page2["data"] if title_like_matches(item, payload["query"])] == []
    assert page1["meta"]["page"] == 1 and page1["meta"]["pages"] == 2
    assert page2["meta"]["page"] == 2 and page2["meta"]["pages"] == 2


def test_bounded_legacy_fixture_recovers_later_matches_without_looping() -> None:
    payload = load("search_relevance_fixtures.json")["lookahead"]
    pages = payload["pages"]
    first_matches = [item["id"] for item in pages[0]["data"] if title_like_matches(item, "dragon")]
    empty_matches = [item["id"] for item in pages[1]["data"] if title_like_matches(item, "dragon")]
    later_matches = [item["id"] for item in pages[2]["data"] if title_like_matches(item, "dragon")]
    assert first_matches == [1]
    assert empty_matches == []
    assert later_matches == [5]
    assert payload["repeated_page"]["data"] == pages[1]["data"]


def test_live_title_policy_excludes_description_only_false_positives() -> None:
    cases = load("search_relevance_fixtures.json")["live_title_policy_cases"]
    expected = {
        "dragon": [[286, 44, 525, 422, 388, 353, 246, 243, 95], [], [], []],
        "skill": [[686, 637, 533, 511, 496, 469, 464, 399, 395, 383, 323, 215, 195, 153, 146, 118, 489], [], []],
        "exact_title": [[311]],
        "partial_title": [[689, 229, 189, 160, 570, 279, 487, 182, 50]],
        "arabic": [[41]],
        "mixed": [[]],
        "punctuation": [[]],
        "no_result": [[]],
    }
    for query_name, case in cases.items():
        assert [page["title_like_ids"] for page in case["pages"]] == expected[query_name]
    dragon = cases["dragon"]["pages"]
    assert dragon[0]["description_only_ids"] == [518]
    assert dragon[1]["description_only_ids"] == [124, 57]
    assert cases["skill"]["pages"][0]["description_only_ids"] == [465]


def test_live_search_pages_are_bounded_and_terminal_after_aggregation() -> None:
    cases = load("search_relevance_fixtures.json")["live_title_policy_cases"]
    for case in cases.values():
        assert case["pages"]
        assert all(page["meta"]["limit"] == 50 for page in case["pages"])
        assert all(len(page["data_ids"]) <= 50 for page in case["pages"])
        observed_ids = [item_id for page in case["pages"] for item_id in page["data_ids"]]
        assert len(observed_ids) == len(set(observed_ids)) or case["query"] in {"dragon", "skill"}


def test_live_type_filters_are_server_honored() -> None:
    cases = load("search_relevance_fixtures.json")["live_filter_cases"]
    for type_name, case in cases.items():
        all_ids = []
        for page in case["pages"]:
            assert page["meta"]["limit"] == 50
            assert page["types"] == [type_name]
            assert len(page["data_ids"]) <= 50
            all_ids.extend(page["data_ids"])
        assert all_ids
        assert len(all_ids) == len(set(all_ids))


def test_source_and_dto_match_the_safe_contract() -> None:
    source = SOURCE.read_text()
    dto = DTO.read_text()
    mapping_start = source.index("private fun ProComicSeriesDto.toSManga")
    mapping = source[mapping_start:]
    matcher_start = source.index("private fun ProComicSeriesDto.matchesSearchQuery")
    matcher_end = source.index("private fun searchTokens", matcher_start)
    matcher = source[matcher_start:matcher_end]

    assert 'thumbnail?.takeIf { it.startsWith("/") && !it.startsWith("//") }' in mapping
    assert 'Regex("cdn\\\\d+")' in mapping
    assert 'https://app.procomic.net$it' not in mapping
    assert "matchesSearchQuery" in source
    assert "SEARCH_PAGE_LIMIT = 50" in source
    assert "MAX_SEARCH_PAGES_PER_BATCH = 6" in source
    assert "SEARCH_BATCH" in source
    assert "hasNextPage=false" in source
    assert "searchPageFingerprint" in source
    assert "searchTokens" in source
    assert "response.request.newBuilder()" in source
    assert "setQueryParameter(\"page\", nextPage.toString())" in source
    assert "metadata?.originalTitle" in matcher
    assert "metadata?.altTitles" in matcher
    assert "metadata?.descriptions" not in matcher
    assert "description.orEmpty()" not in matcher
    assert '@SerialName("cdn_path") val cdnPath: String? = null' in dto
    assert '@Serializable(with = IntOrMapSerializer::class)' in dto

    filter_start = source.index("override fun getFilterList()")
    filter_block = source[filter_start:source.index("// ---- DTO", filter_start)]
    assert "TypeFilter()" in filter_block
    assert "GenreFilter()" not in filter_block


def main() -> None:
    test_absolute_values_have_priority()
    test_relative_values_require_validated_cdn_path()
    test_server_false_positives_are_filtered_without_losing_page_metadata()
    test_bounded_legacy_fixture_recovers_later_matches_without_looping()
    test_live_title_policy_excludes_description_only_false_positives()
    test_live_search_pages_are_bounded_and_terminal_after_aggregation()
    test_live_type_filters_are_server_honored()
    test_source_and_dto_match_the_safe_contract()
    print("search contract tests: PASS (validated thumbnails, title-like relevance, live false positives, and bounded batch consumption)")


if __name__ == "__main__":
    main()
