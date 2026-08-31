#!/usr/bin/env python3
"""Adversarial regression tests for hostile Reader and parser inputs.

These tests intentionally model the Kotlin boundary contracts and pair them with source assertions
for behavior that cannot be instantiated without the Android/Mihon runtime.
"""

from __future__ import annotations

import base64
import json
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).parents[2]
UTILS = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicUtils.kt"
SOURCE = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt"

ALLOWED_PAGE_HOSTS = {"app.procomic.pro"}
ALLOWED_TILE_HOSTS = {"img1.procomic.pro", "img2.procomic.pro", "img3.procomic.pro", "img4.procomic.pro"}
ALLOWED_CDN_PATHS = {"cdn1", "cdn2", "cdn3", "cdn4"}
MAX_TOKEN = 8192
MAX_PAGE_INDEX = 10_000


def allowed_page_url(value: str) -> bool:
    parsed = urlsplit(value)
    return (
        parsed.scheme.lower() == "https"
        and parsed.hostname is not None
        and parsed.hostname.lower() in ALLOWED_PAGE_HOSTS
        and parsed.username is None
        and parsed.password is None
        and parsed.query == ""
        and parsed.fragment == ""
        and parsed.path.startswith("/chapters/")
        and parsed.path.rsplit(".", 1)[-1].lower() in {"avif", "webp", "jpg", "jpeg", "png"}
    )


def allowed_reader_url(value: str) -> bool:
    parsed = urlsplit(value)
    return (
        parsed.scheme.lower() == "https"
        and parsed.hostname == "procomic.pro"
        and parsed.username is None
        and parsed.password is None
        and parsed.query == ""
        and parsed.fragment == ""
        and (parsed.path.startswith("/en/chapter/") or parsed.path.startswith("/ar/chapter/"))
    )


def allowed_tile_url(value: str) -> bool:
    parsed = urlsplit(value)
    return (
        parsed.scheme.lower() == "https"
        and parsed.hostname in ALLOWED_TILE_HOSTS
        and parsed.username is None
        and parsed.password is None
        and parsed.query == ""
        and parsed.fragment == ""
        and parsed.path.startswith("/i/")
        and len(parsed.path) > 3
        and all(segment not in {".", ".."} for segment in parsed.path.split("/"))
    )


def valid_payload(fragment: str) -> bool:
    prefix = "procomic-protected-page-v1:"
    if not fragment.startswith(prefix):
        return False
    encoded = fragment[len(prefix) :]
    if not encoded or len(encoded) > MAX_TOKEN * 2:
        return False
    try:
        raw = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4))
        payload = json.loads(raw)
    except (ValueError, UnicodeDecodeError, json.JSONDecodeError):
        return False
    return (
        payload.get("kind") == prefix[:-1]
        and isinstance(payload.get("chapterId"), int)
        and payload["chapterId"] > 0
        and isinstance(payload.get("token"), str)
        and 0 < len(payload["token"]) <= MAX_TOKEN
        and payload.get("method") == "browser_session"
        and payload.get("cdnPath") in ALLOWED_CDN_PATHS
        and isinstance(payload.get("pageIndex"), int)
        and 0 <= payload["pageIndex"] <= MAX_PAGE_INDEX
    )


def encoded_payload(**overrides: object) -> str:
    payload = {
        "kind": "procomic-protected-page-v1",
        "chapterId": 53081,
        "token": "short-lived-token",
        "method": "browser_session",
        "cdnPath": "cdn2",
        "pageIndex": 3,
    }
    payload.update(overrides)
    encoded = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    return "procomic-protected-page-v1:" + encoded


def test_page_url_boundaries() -> None:
    assert allowed_page_url("https://app.procomic.pro/chapters/690/53081/p1.avif")
    for value in (
        "http://app.procomic.pro/chapters/690/p1.avif",
        "https://evil.example/chapters/690/p1.avif",
        "https://app.procomic.pro/chapters/690/p1.avif?token=secret",
        "https://user:pass@app.procomic.pro/chapters/690/p1.avif",
        "https://app.procomic.pro/other/p1.avif",
        "https://app.procomic.pro/chapters/690/p1.svg",
    ):
        assert not allowed_page_url(value), value


def test_reader_and_tile_url_boundaries() -> None:
    assert allowed_reader_url("https://procomic.pro/en/chapter/title-1-53081")
    assert allowed_tile_url("https://img2.procomic.pro/i/signed-piece.avif")
    for value in (
        "https://evil.example/en/chapter/title-1-53081",
        "http://procomic.pro/en/chapter/title-1-53081",
        "https://procomic.pro/en/chapter/title-1-53081?token=secret",
        "https://user:pass@procomic.pro/en/chapter/title-1-53081",
        "https://img5.procomic.pro/i/piece.avif",
        "https://img2.procomic.pro/i/piece.avif?sig=secret",
        "https://img2.procomic.pro/other/piece.avif",
        "https://img2.procomic.pro/i/../secret.avif",
    ):
        assert not (allowed_reader_url(value) or allowed_tile_url(value)), value


def test_thumbnail_policy_is_source_enforced() -> None:
    source = SOURCE.read_text()
    utils = UTILS.read_text()
    assert "fun isAllowedThumbnailUrl" in utils
    assert source.count("isAllowedThumbnailUrl") >= 6
    assert "isAllowedCdnPath" in source
    assert "cdn\\\\d+" not in source


def test_protected_payload_chaos() -> None:
    assert valid_payload(encoded_payload())
    invalid = (
        {"kind": "other"},
        {"chapterId": 0},
        {"token": ""},
        {"token": "x" * (MAX_TOKEN + 1)},
        {"method": "webview"},
        {"cdnPath": "cdn999"},
        {"pageIndex": -1},
        {"pageIndex": MAX_PAGE_INDEX + 1},
    )
    for overrides in invalid:
        assert not valid_payload(encoded_payload(**overrides)), overrides
    assert not valid_payload("procomic-protected-page-v1:" + "A" * (MAX_TOKEN * 2 + 1))
    assert not valid_payload("procomic-protected-page-v1:not-base64!!!")


def test_json_string_array_with_embedded_bracket() -> None:
    body = '{"originalSources":["title ] with bracket", "second"]}'
    start = body.index("[")
    end = body.rfind("]") + 1
    assert json.loads(body[start:end]) == ["title ] with bracket", "second"]
    source = UTILS.read_text()
    assert "extractJsonArray(body, valueStart)" in source
    assert "body.indexOf(']', valueStart)" not in source


def test_request_entrypoints_bound_untrusted_state() -> None:
    source = SOURCE.read_text()
    assert "page.coerceAtLeast(1)" in source
    assert "toIntOrNull()" in source
    assert 'throw Exception("ProComic: malformed manga URL")' in source
    assert "@Volatile" in source


def test_deferred_failures_are_nonfatal_and_identity_checked() -> None:
    source = SOURCE.read_text()
    assert 'if (parsed.success == false)' in source
    assert 'deferredData.chapterId != chapterId' in source
    assert 'catch (error: Exception)' in source
    assert 'return pages' in source
    assert 'MAX_READER_PAGE_INDEX' in source


def test_tile_geometry_is_overflow_safe() -> None:
    interceptor = (ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicImageInterceptor.kt").read_text()
    assert "MAX_COMPOSITE_PIXELS" in interceptor
    assert "MAX_BITMAP_DIMENSION" in interceptor
    assert "MAX_OUTPUT_BYTES" in interceptor
    assert "width.toLong() * height.toLong()" in interceptor
    assert "validateTileDimensions" in interceptor
    assert "if (parsed.success == false)" in interceptor
    assert "rect.left.toLong() + rect.width.toLong()" in interceptor
    assert "rect.top.toLong() + rect.height.toLong()" in interceptor
    assert "width.toLong() * (column + 1).toLong()" in interceptor
    assert "height.toLong() * (row + 1).toLong()" in interceptor
    assert "tileResponse.newBuilder" not in interceptor
    assert ".protocol(Protocol.HTTP_1_1)" in interceptor
    assert "isProtectedPageImageUrl(request.url.toString())" in interceptor
    utils = UTILS.read_text()
    assert "it.isFinite() && it >= 0f" in utils


def test_source_boundaries_and_diagnostic_policy() -> None:
    source = SOURCE.read_text()
    utils = UTILS.read_text()
    diag = (ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicDiag.kt").read_text()
    assert "MAX_RESPONSE_BYTES" in source
    assert "body.contentLength()" in source
    assert "takeIf(ProComicUtils::isAllowedReaderUrl)" in source
    assert "json first 200" not in utils
    assert "seriesJsonSha256" in utils
    assert "takeIf(::isAllowedPageImageUrl)" in utils
    assert "decodeProtectedPagePayload(parsed.fragment.orEmpty())" in utils
    assert "raw response body content" in diag
    assert "bodySha256=" in diag


def main() -> None:
    for test in sorted(globals()):
        if test.startswith("test_"):
            globals()[test]()
    print("adversarial chaos boundary tests: PASS")


if __name__ == "__main__":
    main()
