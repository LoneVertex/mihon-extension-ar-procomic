"""Deterministic contract tests for Commit 6 parser and image-host hardening."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "testdata" / "hardening" / "hardening_fixtures.json"
SOURCE_DIR = ROOT / "app" / "src" / "main" / "kotlin" / "eu" / "kanade" / "tachiyomi" / "extension" / "ar" / "procomic"
UTILS = SOURCE_DIR / "ProComicUtils.kt"
SOURCE = SOURCE_DIR / "ProComic.kt"


def allowed_image_url(value: str) -> bool:
    parsed = urlparse(value)
    return (
        parsed.scheme.lower() == "https"
        and parsed.hostname == "app.procomic.pro"
        and parsed.query == ""
        and parsed.fragment == ""
        and parsed.path.startswith("/chapters/")
        and any(parsed.path.lower().endswith(f".{extension}") for extension in ("avif", "webp", "jpg", "jpeg", "png"))
    )


def page_fingerprint(page: dict[str, object]) -> str:
    return ",".join(str(value) for value in page["ids"])


def paginate(pages: list[dict[str, object]]) -> list[int]:
    seen: set[str] = set()
    output: list[int] = []
    for page in pages:
        fingerprint = page_fingerprint(page)
        if not page["ids"] or fingerprint in seen:
            break
        seen.add(fingerprint)
        output.extend(int(value) for value in page["ids"])
        if not page["hasMore"]:
            break
    return output


def main() -> None:
    data = json.loads(FIXTURE.read_text())
    assert all(allowed_image_url(url) for url in data["allowed_image_urls"])
    assert not any(allowed_image_url(url) for url in data["rejected_image_urls"])

    assert paginate(data["chapter_pages"]["normal"]) == [1001, 1002, 1003]
    assert paginate(data["chapter_pages"]["repeated"]) == [1001, 1002]
    assert paginate(data["chapter_pages"]["empty_continuation"]) == [1001]

    utils_text = UTILS.read_text()
    source_text = SOURCE.read_text()
    assert 'private const val MAX_RSC_CANDIDATES = 8' in utils_text
    assert 'private const val MAX_RSC_CANDIDATE_BYTES = 1_000_000' in utils_text
    # allowedPageImageHosts now contains multiple hosts (cdn1-4.procomic.pro added in audit 2026-09-05)
    assert '"app.procomic.pro"' in utils_text
    assert '"cdn1.procomic.pro"' in utils_text
    assert '"cdn2.procomic.pro"' in utils_text
    assert 'fun isAllowedPageImageUrl(url: String)' in utils_text
    assert 'parsed.scheme.equals("https", ignoreCase = true)' in utils_text or \
           'parsed.scheme.equals("https", ignoreCase = true)' in utils_text
    assert 'parsed.query' in utils_text
    assert 'parsed.fragment' in utils_text
    assert 'repeat(MAX_RSC_CANDIDATES)' in utils_text
    assert 'pos - startPos <= MAX_RSC_CANDIDATE_BYTES' in utils_text
    # Path validation is now host-scoped; the fallback regex still covers the .net domain
    assert 'https://app\\.procomic\\.(pro|net)/chapters/' in utils_text

    assert 'const val MAX_RESPONSE_BYTES = 2_000_000' in source_text
    assert 'const val MAX_CHAPTER_PAGES = 50' in source_text
    assert 'body.source().readByteArray(MAX_RESPONSE_BYTES.toLong() + 1L)' not in source_text
    assert 'val buffer = Buffer()' in source_text
    assert 'source.read(buffer' in source_text
    assert 'seenPageFingerprints' in source_text
    assert 'empty page terminates pagination' in source_text
    assert 'repeated page terminates pagination' in source_text
    assert 'ProComicUtils.isAllowedPageImageUrl(imageUrl)' in source_text
    assert 'rejected unrecognized image host' in source_text

    # Reader extraction and request construction remain the same contract; hardening is layered
    # around the existing methods rather than replacing them with WebView/browser automation.
    assert 'override fun pageListRequest(chapter: SChapter): Request' in source_text
    assert 'override fun pageListParse(response: Response): List<Page>' in source_text
    assert 'ProComicUtils.extractPageImages(body, "PAGES", url)' in source_text
    assert 'override fun imageRequest(page: Page): Request' in source_text
    assert 'import android.webkit.WebView' not in source_text
    print("parser hardening tests: PASS (bounded scans, size limits, repeated pages, image-host allowlist, Reader contract preserved)")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as exc:
        print(f"parser hardening tests: FAIL: {exc}", file=sys.stderr)
        raise
