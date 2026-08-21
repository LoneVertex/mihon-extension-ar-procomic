"""Deterministic tests for ProComic publication lifecycle status mapping."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "testdata" / "status" / "status_fixtures.json"
SOURCE = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt"
DTO = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicDto.kt"


def map_publication_status(progress: str | None, lifecycle_status: str | None) -> str:
    known = {
        "ongoing": "ONGOING",
        "مستمر": "ONGOING",
        "completed": "COMPLETED",
        "complete": "COMPLETED",
        "finished": "COMPLETED",
        "مكتمل": "COMPLETED",
        "hiatus": "ON_HIATUS",
        "on hiatus": "ON_HIATUS",
        "متوقف مؤقتا": "ON_HIATUS",
        "متوقف مؤقتًا": "ON_HIATUS",
        "dropped": "CANCELLED",
        "cancelled": "CANCELLED",
        "canceled": "CANCELLED",
        "متوقف": "CANCELLED",
    }
    for value in (progress, lifecycle_status):
        if value is not None:
            mapped = known.get(value.strip().lower())
            if mapped is not None:
                return mapped
    return "UNKNOWN"


def test_fixture_expectations() -> None:
    fixtures = json.loads(FIXTURES.read_text(encoding="utf-8"))
    for name, row in fixtures.items():
        actual = map_publication_status(row.get("progress"), row.get("status"))
        assert actual == row["expected"], f"{name}: {actual} != {row['expected']}"


def test_source_uses_lifecycle_mapper_and_not_view_status() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    assert "private fun mapPublicationStatus" in source
    assert "progress: String? = null" in DTO.read_text(encoding="utf-8")
    assert "progress = this@toPopularSManga.progress" in source
    assert "progress = this@toSManga.progress" in source
    assert "metadata?.viewStatus?.lowercase()" not in source
    assert '"public"' not in source[source.index("private fun mapPublicationStatus"):source.index("// Mobile Chrome UA")]
    for value in ("مستمر", "مكتمل", "ongoing", "completed", "hiatus", "dropped"):
        assert value in source


def test_latest_uses_same_mapper() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    latest_start = source.index("private fun ProComicLatestSeries.toLatestSManga")
    popular_start = source.index("private fun ProComicPopularContent.toPopularSManga")
    latest_block = source[latest_start:popular_start]
    assert "mapPublicationStatus" in latest_block
    assert "this@toLatestSManga.status" in latest_block


def main() -> None:
    test_fixture_expectations()
    test_source_uses_lifecycle_mapper_and_not_view_status()
    test_latest_uses_same_mapper()
    print("status mapping tests: PASS (Arabic/English lifecycle, access separation, all mappers)")


if __name__ == "__main__":
    main()
