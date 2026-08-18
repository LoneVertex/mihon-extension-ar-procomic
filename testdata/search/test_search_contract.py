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


def test_source_and_dto_match_the_safe_contract() -> None:
    source = SOURCE.read_text()
    dto = DTO.read_text()
    mapping_start = source.index("private fun ProComicSeriesDto.toSManga")
    mapping = source[mapping_start:]

    assert 'thumbnail?.takeIf { it.startsWith("/") && !it.startsWith("//") }' in mapping
    assert 'Regex("cdn\\\\d+")' in mapping
    assert 'https://app.procomic.net$it' not in mapping
    assert '@SerialName("cdn_path") val cdnPath: String? = null' in dto


def main() -> None:
    test_absolute_values_have_priority()
    test_relative_values_require_validated_cdn_path()
    test_source_and_dto_match_the_safe_contract()
    print("search contract tests: PASS (absolute priority, validated CDN fallback, unsafe rejection)")


if __name__ == "__main__":
    main()
