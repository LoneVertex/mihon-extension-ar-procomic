#!/usr/bin/env python3
"""Deterministic tests for the protected Reader manifest and access-state contract."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "testdata" / "reader"
UTILS = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicUtils.kt"
PROCOMIC = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt"


def load() -> dict[str, str]:
    return json.loads((FIXTURES / "reader_contract_fixtures.json").read_text())


def extract_fixture_images(body: str) -> list[str]:
    marker = '"appImages":['
    start = body.index(marker) + len(marker)
    end = body.find(']}</script>', start)
    if end < 0:
        end = body.find(']</script>', start)
    if end < 0:
        raise AssertionError('fixture manifest terminator not found')
    manifest = json.loads("[" + body[start:end] + "]")
    return [item.get("desktop") or item.get("mobile") for item in manifest]


def test_multi_page_manifest_preserves_order_and_count() -> None:
    images = extract_fixture_images(load()["multi_page_manifest"])
    assert images == [
        "https://app.procomic.pro/chapters/690/50821/p1/page1.avif",
        "https://app.procomic.pro/chapters/690/50821/p2/page2.avif",
        "https://app.procomic.pro/chapters/690/50821/p3/page3.avif",
    ]


def test_escaped_rsc_manifest_preserves_order_and_count() -> None:
    fixture = load()["escaped_rsc_manifest"]
    normalized = fixture.replace('\\"', '"')
    images = extract_fixture_images(normalized)
    assert len(images) == 3
    assert images[0].endswith('/p1/page1.avif')
    assert images[1].endswith('/p2/page2.avif')
    assert images[2].endswith('/p3/page3.avif')


def test_premium_response_is_distinguished_from_missing_manifest() -> None:
    fixture = load()["premium_locked"]
    assert "Premium chapter" in fixture
    assert "Unlock now for just 5 coins" in fixture
    assert "appImages" not in fixture


def test_safe_browsing_response_is_distinguished_from_missing_manifest() -> None:
    fixture = load()["safe_browsing_required"]
    assert "Safe Browsing Required" in fixture
    assert "Log in and disable Safe Browsing" in fixture
    assert "appImages" not in fixture


def test_source_preserves_reader_bounds_and_explicit_access_diagnostic() -> None:
    utils = UTILS.read_text()
    procomic = PROCOMIC.read_text()
    assert "extractPageImages" in utils
    assert "MAX_RSC_CANDIDATE_BYTES" in utils
    assert "MAX_RSC_CANDIDATES" in utils
    assert "Safe Browsing Required" in utils
    assert "Log in and disable Safe Browsing" in utils
    assert "normalizeRscJson" in utils
    assert "Premium chapter" in utils
    assert "https://procomic.pro" in procomic
    assert "pageListParse" in procomic
    assert "imageRequest" in procomic
    assert "import android.webkit.WebView" not in procomic


def main() -> None:
    test_multi_page_manifest_preserves_order_and_count()
    test_escaped_rsc_manifest_preserves_order_and_count()
    test_premium_response_is_distinguished_from_missing_manifest()
    test_safe_browsing_response_is_distinguished_from_missing_manifest()
    test_source_preserves_reader_bounds_and_explicit_access_diagnostic()
    print("reader contract tests: PASS (multi-page order, escaped RSC, premium/Safe Browsing states, bounds)")


if __name__ == "__main__":
    main()
