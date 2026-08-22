#!/usr/bin/env python3
"""Deterministic tests for the protected Reader manifest and access-state contract."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "testdata" / "reader"
UTILS = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicUtils.kt"
PROCOMIC = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt"
BUILD = ROOT / "app/build.gradle.kts"
CI = ROOT / ".github/workflows/ci.yml"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


def load() -> dict:
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


def extract_escaped_rsc_array(body: str) -> list[dict[str, str]]:
    marker = '\\\"appImages\\\":['
    marker_start = body.index(marker)
    start = marker_start + len(marker) - 1
    depth = 0
    in_string = False
    escaped = False
    pos = start
    while pos < len(body):
        char = body[pos]
        if not in_string and char == '\\' and pos + 1 < len(body) and body[pos + 1] == '"':
            pos += 2
            continue
        if escaped:
            escaped = False
        elif char == '\\' and in_string:
            escaped = True
        elif char == '"':
            in_string = not in_string
        elif not in_string and char == '[':
            depth += 1
        elif not in_string and char == ']':
            depth -= 1
            if depth == 0:
                candidate = body[start:pos + 1].replace('\\"', '"')
                return json.loads(candidate)
        pos += 1
    raise AssertionError('escaped RSC array terminator not found')


def test_live_escaped_rsc_array_stops_before_trailing_protection_object() -> None:
    fixture = load()["live_escaped_rsc_with_trailing_object"]
    images = extract_escaped_rsc_array(fixture)
    assert len(images) == 3
    assert [item["desktop"] for item in images] == [
        "https://app.procomic.pro/chapters/690/50821/p1/1785188000210-bp3wk0x3m06-4086df2c7bf6-desktop.avif",
        "https://app.procomic.pro/chapters/690/50821/p2/1785188081002-2mzaao9xdsh-37fd56b92665-desktop.avif",
        "https://app.procomic.pro/chapters/690/50821/p3/1785188091276-8luu5gddfhl-a55b69f3cd25-desktop.avif",
    ]
    assert "protectionV2" not in json.dumps(images)


def extract_escaped_rsc_object(body: str, key: str) -> dict:
    marker = f'\\\"{key}\\\":{{'
    marker_start = body.index(marker)
    start = marker_start + len(marker) - 1
    depth = 0
    in_string = False
    escaped = False
    pos = start
    while pos < len(body):
        char = body[pos]
        if not in_string and char == '\\' and pos + 1 < len(body) and body[pos + 1] == '"':
            pos += 2
            continue
        if escaped:
            escaped = False
        elif char == '\\' and in_string:
            escaped = True
        elif char == '"':
            in_string = not in_string
        elif not in_string and char == '{':
            depth += 1
        elif not in_string and char == '}':
            depth -= 1
            if depth == 0:
                candidate = body[start:pos + 1].replace('\\"', '"')
                return json.loads(candidate)
        pos += 1
    raise AssertionError(f'escaped RSC object terminator not found for {key}')


def test_live_guest_limit_is_proven_server_side() -> None:
    case = load()["live_guest_limit_case"]
    assert case["appImages_count"] == 3
    assert case["publicImageCount"] == 3
    assert case["page_4_probe_status"] == 404
    assert len(case["page_urls"]) == case["publicImageCount"]
    assert all(url.startswith("https://app.procomic.pro/chapters/690/50821/") for url in case["page_urls"])


def test_chapter_131_page_4_tiles_are_valid_avif_and_geometry_is_complete() -> None:
    case = load()["chapter_131_tile_decode_contract"]
    page = case["page_4"]
    assert case["series_id"] == 56
    assert case["chapter_id"] == 50318
    assert case["publicImageCount"] == 3
    assert case["deferred_media_status"] == 200
    assert case["map_count"] == 17
    assert case["page_indices"] == list(range(3, 20))
    assert page["dim"] == [800, 5000]
    assert page["mode"] == "grid_4x2"
    assert page["piece_count"] == page["rect_count"] == 8
    assert page["tile_statuses"] == [200] * 8
    assert page["tile_content_type"] == "image/avif"
    assert page["tile_dimensions"] == [[200, 2500]] * 8
    assert page["tile_decode_probe"] == "pillow_avif_ok"
    assert set(case["all_tile_hosts"]) == {
        "img1.procomic.pro",
        "img2.procomic.pro",
        "img3.procomic.pro",
        "img4.procomic.pro",
    }


def test_exact_chapter_1_contract_has_valid_protected_tiles() -> None:
    case = load()["exact_chapter_1_tile_decode_contract"]
    assert case["series_id"] == 109
    assert case["chapter_id"] == 5650
    assert case["chapter_label"] == "1"
    assert case["deferred_media_status"] == 200
    assert case["map_count"] == 3
    assert [item["dim"] for item in case["maps"]] == [[800, 7500], [800, 7500], [800, 5273]]
    assert [item["mode"] for item in case["maps"]] == ["grid_3x2", "grid_2x3", "vertical_5"]
    assert [item["piece_count"] for item in case["maps"]] == [6, 6, 5]
    assert [item["piece_count"] for item in case["maps"]] == [item["rect_count"] for item in case["maps"]]
    assert all(item["tile_status"] == 200 for item in case["maps"])
    assert case["total_tile_count"] == 17
    assert case["tile_content_types"] == ["image/avif"]
    assert case["tile_body_signatures"] == ["avif/isobmff"]
    assert case["tile_decode_probe"] == "pillow_avif_ok"
    assert set(case["all_tile_hosts"]) == {
        "img1.procomic.pro",
        "img2.procomic.pro",
        "img3.procomic.pro",
        "img4.procomic.pro",
    }
    assert sum(case["tile_host_counts"].values()) == case["total_tile_count"]
    assert case["maps_failed"] == case["tiles_failed"] == 0


def test_exact_chapter_5_contract_covers_yuv444_and_all_protected_tiles() -> None:
    case = load()["exact_chapter_5_tile_decode_contract"]
    assert case["series_id"] == 387
    assert case["chapter_id"] == 19273
    assert case["chapter_label"] == "5"
    assert case["language"] == "AR"
    assert case["deferred_media_status"] == 200
    assert case["public_image_count"] == 2
    assert case["map_count"] == 2
    assert case["page_indices"] == [3, 4]
    assert [item["dim"] for item in case["maps"]] == [[768, 8000], [768, 362]]
    assert [item["mode"] for item in case["maps"]] == ["vertical_5", "grid_2x2"]
    assert [item["piece_count"] for item in case["maps"]] == [5, 4]
    assert [item["piece_count"] for item in case["maps"]] == [item["rect_count"] for item in case["maps"]]
    assert case["total_tile_count"] == 9
    assert case["tile_status"] == 200
    assert case["tile_content_type"] == "image/avif"
    assert case["tile_body_signature"] == "avif/isobmff"
    assert case["tile_decode_probe"] == "pillow_avif_ok"
    assert case["tile_codec"] == "av1"
    assert case["tile_pixel_format"] == "yuv444p"
    assert case["tile_color_primaries"] == "bt709"
    assert case["tile_color_range"] == "pc"
    assert case["tile_transfer"] == "iec61966-2-1"
    assert set(case["tile_hosts"]) == {
        "img1.procomic.pro",
        "img2.procomic.pro",
        "img3.procomic.pro",
        "img4.procomic.pro",
    }
    assert case["tile_bytes_range"] == [302, 54158]
    assert case["aomedia_decoder_required"] is True
    assert case["maps_failed"] == case["tiles_failed"] == 0


def test_global_page_boundary_contract_preserves_all_logical_sources_and_full_frame() -> None:
    case = load()["global_page_boundary_contract"]
    assert case["series_id"] == 387
    assert case["chapter_id"] == 19269
    assert case["chapter_label"] == "10"
    assert case["language"] == "AR"
    assert case["chapter_response_status"] == case["deferred_media_status"] == 200
    assert case["public_manifest_count"] == 3
    assert case["deferred_image_count"] == 2
    assert case["protected_map_count"] == 1
    assert case["logical_page_count"] == 6
    assert case["logical_page_indices"] == list(range(case["logical_page_count"]))
    assert case["page_source_order"] == [
        "public_manifest", "public_manifest", "public_manifest",
        "deferred_image", "deferred_image", "protected_map",
    ]
    assert case["protected_page_index"] == 5
    assert case["proxy_page_index"] == 3
    page = case["map"]
    assert page["dim"] == [1000, 7659]
    assert page["mode"] == "grid_2x2"
    assert page["piece_count"] == page["rect_count"] == 4
    assert page["rectangles"] == [
        [0, 0, 500, 3830],
        [500, 0, 500, 3830],
        [0, 3830, 500, 3829],
        [500, 3830, 500, 3829],
    ]
    assert page["coverage"] == "exact_full_frame_no_uncovered_rows_or_columns"
    assert page["tile_status"] == 200
    assert page["tile_content_type"] == "image/avif"
    assert page["tile_body_signature"] == "avif/isobmff"
    assert case["black_strip_in_page_bytes"] is False
    assert case["viewer_gap_candidate"] is True


def test_deferred_media_contract_recovers_all_protected_pages() -> None:
    case = load()["deferred_media_contract"]
    assert case["publicImageCount"] == 3
    assert case["deferred_images_count"] == 0
    assert case["map_count"] == 7
    assert case["page_indices"] == list(range(3, 10))
    assert case["tile_counts"] == case["rect_counts"]
    assert case["proxy_plan_status"] == 200
    assert case["tile_probe_status"] == 200
    assert case["tile_content_type"] == "image/avif"
    assert set(case["tile_hosts"]) == {
        "img1.procomic.pro",
        "img2.procomic.pro",
        "img3.procomic.pro",
        "img4.procomic.pro",
    }


def test_current_reader_sibling_deferred_media_recovers_ten_logical_pages() -> None:
    fixture = load()["sibling_deferred_media_rsc"]
    normalized = fixture.replace('\\"', '"')
    images = extract_escaped_rsc_array(fixture)
    protection = extract_escaped_rsc_object(fixture, "protectionV2")
    deferred = extract_escaped_rsc_object(fixture, "deferredMedia")
    assert len(images) == 3
    assert protection["publicImageCount"] == 3
    assert "deferredMedia" not in protection
    assert deferred["token"] == "redacted-capability-token"
    assert deferred["splitIndex"] == 3
    assert deferred["requireTurnstile"] is False
    assert len(images) + load()["deferred_media_contract"]["map_count"] == 10


def test_legacy_nested_deferred_media_remains_supported_and_malformed_sibling_is_rejected() -> None:
    legacy_fixture = load()["legacy_nested_deferred_media_rsc"]
    malformed_fixture = load()["malformed_sibling_deferred_media_rsc"]
    legacy = extract_escaped_rsc_object(legacy_fixture, "protectionV2")
    malformed = extract_escaped_rsc_object(malformed_fixture, "deferredMedia")
    assert legacy["deferredMedia"]["token"] == "legacy-token"
    assert malformed.get("token") is None


def test_source_uses_deferred_media_and_protected_tile_reconstruction() -> None:
    procomic = PROCOMIC.read_text()
    interceptor = (PROCOMIC.parent / "ProComicImageInterceptor.kt").read_text()
    dto = (PROCOMIC.parent / "ProComicDto.kt").read_text()
    utils = UTILS.read_text()
    assert "chapter-deferred-media" in procomic
    assert "extractReaderDeferredMedia(body" in procomic
    assert "?: protection?.deferredMedia" in procomic
    assert "MAX_READER_DEFERRED_MAPS" in procomic
    assert "extractReaderDeferredMedia" in utils
    assert "chapter-map-proxy-plan" in interceptor
    assert "ProComicDeferredMediaResponse" in dto
    assert "ProComicProtectedMap" in dto
    assert "ProComicImageInterceptor" in procomic
    assert "procomic-protected-page-v1" in utils
    assert "isAllowedProtectedTileUrl" in utils
    assert "Bitmap.createBitmap" in interceptor
    assert "Canvas" in interceptor
    assert "ImageDecoder" in interceptor
    assert "AvifDecoder" in interceptor
    assert "decodeWithAomedia" in interceptor
    assert "MAX_TILE_PIXELS" in interceptor
    assert "MAX_TILE_BYTES" in interceptor
    assert "MAX_MAP_RESPONSE_BYTES" in interceptor
    assert "readBoundedBytes(tileResponse, MAX_TILE_BYTES)" in interceptor
    assert "readBoundedText(response, MAX_MAP_RESPONSE_BYTES)" in interceptor
    assert "AvifDecoder.decode(source, bytes.size, bitmap)" in interceptor
    assert "logUnexpectedTileMetadata" in interceptor
    assert "protected tile body signature invalid" in interceptor
    assert "AOMedia AVIF decoder returned no bitmap" in interceptor
    build = BUILD.read_text()
    ci = CI.read_text()
    assert "org.aomedia.avif.android:avif:1.3.0.841110fd" in build
    assert "avif-coder" not in build
    assert 'compileOnly("org.jsoup:jsoup:1.23.1")' in build
    assert 'compileOnly("org.jsoup:jsoup:1.16.2")' not in build
    assert "compileSdk = 35" in build
    assert "targetSdk = 35" in build
    assert "versionCode = 3" in build
    assert 'versionName = "1.2"' in build
    assert 'sdkmanager" "platforms;android-35"' in ci
    assert "useLegacyPackaging = true" in build
    assert "extractNativeLibs" not in MANIFEST.read_text()
    assert "img1.procomic.pro" in utils
    assert "android.webkit.WebView" not in procomic
    assert "android.webkit.WebView" not in interceptor
    assert "Cookie" not in procomic
    assert "x-turnstile-token" not in procomic
    assert "chapter-map-session-key" not in procomic


def test_aomedia_avif_decoder_is_bounded_and_nonfatal_at_extension_startup() -> None:
    interceptor = (PROCOMIC.parent / "ProComicImageInterceptor.kt").read_text()
    assert "runCatching { decodeWithAomedia(bytes) }" in interceptor
    assert "AvifDecoder.getInfo" in interceptor
    assert "AvifDecoder.decode" in interceptor
    assert "ByteBuffer.allocateDirect" in interceptor
    assert "MAX_TILE_PIXELS" in interceptor
    assert "AOMedia AVIF tile decode failed" in interceptor
    assert "AOMedia AVIF decoder returned no bitmap" in interceptor


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
    assert "Next.js RSC may serialize JSON property quotes" in utils
    assert "publicImageCount" in utils
    assert "Premium chapter" in utils
    assert "https://procomic.pro" in procomic
    assert "pageListParse" in procomic
    assert "imageRequest" in procomic
    assert "import android.webkit.WebView" not in procomic


def main() -> None:
    test_multi_page_manifest_preserves_order_and_count()
    test_escaped_rsc_manifest_preserves_order_and_count()
    test_live_escaped_rsc_array_stops_before_trailing_protection_object()
    test_live_guest_limit_is_proven_server_side()
    test_current_reader_sibling_deferred_media_recovers_ten_logical_pages()
    test_legacy_nested_deferred_media_remains_supported_and_malformed_sibling_is_rejected()
    test_chapter_131_page_4_tiles_are_valid_avif_and_geometry_is_complete()
    test_exact_chapter_1_contract_has_valid_protected_tiles()
    test_exact_chapter_5_contract_covers_yuv444_and_all_protected_tiles()
    test_global_page_boundary_contract_preserves_all_logical_sources_and_full_frame()
    test_deferred_media_contract_recovers_all_protected_pages()
    test_source_uses_deferred_media_and_protected_tile_reconstruction()
    test_aomedia_avif_decoder_is_bounded_and_nonfatal_at_extension_startup()
    test_premium_response_is_distinguished_from_missing_manifest()
    test_safe_browsing_response_is_distinguished_from_missing_manifest()
    test_source_preserves_reader_bounds_and_explicit_access_diagnostic()
    print("reader contract tests: PASS (public/deferred pages, protected-map geometry, bounded AOMedia AVIF fallback, escaped RSC boundaries, access states, bounds)")


if __name__ == "__main__":
    main()
