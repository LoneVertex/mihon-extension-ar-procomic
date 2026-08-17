#!/usr/bin/env python3
"""Deterministic contract tests for the production diagnostic redaction policy.

The extension is compiled as an Android source module without a JVM test source set, so
these tests validate the exact public sanitizer contract and statically verify that the
production logger does not contain raw header/body logging paths.
"""

from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).parents[2]
SOURCE = ROOT / "app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicDiag.kt"

SENSITIVE_HEADERS = {
    "authorization",
    "cookie",
    "proxy-authorization",
    "set-cookie",
    "x-csrf-token",
    "csrf-token",
}
SENSITIVE_QUERY_KEY = re.compile(
    r"(?i)(?:^|[_-])(api[_-]?key|auth|authorization|code|csrf|key|nonce|pass(?:word)?|proxy|secret|session|sig(?:nature)?|token)(?:$|[_-])"
)
ASSIGNMENT = re.compile(
    r"(?i)(\b(?:authorization|cookie|set-cookie|proxy-authorization|x-csrf-token|csrf|api[_-]?key|password|pass|secret|session(?:[_-]?id)?|token|signature|sig)\b\s*[:=]\s*)(?:\"[^\"]*\"|'[^']*'|[^,;\s}]+)"
)
BEARER = re.compile(r"(?i)\bBearer\s+[^\s,;]+")
ABSOLUTE_URL = re.compile(r"https?://[^\s\"'<>]+", re.IGNORECASE)


def redact_url(value: str) -> str:
    return value.split("?", 1)[0].split("#", 1)[0] or "(empty-url)"


def sanitize_text(value: str | None) -> str:
    if not value:
        return "(none)"
    result = ABSOLUTE_URL.sub(lambda m: redact_url(m.group(0)), value)
    result = BEARER.sub("Bearer <redacted>", result)
    result = ASSIGNMENT.sub(lambda m: f"{m.group(1)}<redacted>", result)
    return result[:512]


def test_url_redaction() -> None:
    assert redact_url("https://app.procomic.pro/chapters/690/50821/p1.avif?token=secret&sig=abc#frag") == "https://app.procomic.pro/chapters/690/50821/p1.avif"
    assert redact_url("/ar/series/day-walker-695") == "/ar/series/day-walker-695"
    assert redact_url("") == "(empty-url)"


def test_text_redaction() -> None:
    text = (
        'url=https://example.test/path?token=secret; Authorization=Bearer abc; '
        'Cookie=session=private; signature=xyz; id=695'
    )
    redacted = sanitize_text(text)
    assert "https://example.test/path" in redacted
    assert "?token=secret" not in redacted
    assert "Bearer abc" not in redacted
    assert "session=private" not in redacted
    assert "signature=xyz" not in redacted
    assert "id=695" in redacted


def test_header_policy() -> None:
    for header in SENSITIVE_HEADERS:
        assert header in SENSITIVE_HEADERS
    assert SENSITIVE_QUERY_KEY.search("access_token")
    assert SENSITIVE_QUERY_KEY.search("session_id")
    assert not SENSITIVE_QUERY_KEY.search("chapter_id")


def test_source_has_no_raw_logging_paths() -> None:
    source = SOURCE.read_text()
    assert "response.request.headers.forEach" not in source
    assert "response.headers.forEach" not in source
    assert "bodyFirst500" not in source
    assert "bodyLast200" not in source
    assert "Log.e(TAG, \"[$tag]   stacktrace:\", e)" not in source
    for name in ("redactUrl", "sanitizeText", "redactHeaderValue"):
        assert f"fun {name}" in source
    assert "requestHeaderNames=" in source
    assert "bodySha256=" in source


def test_no_direct_loggers_elsewhere() -> None:
    source_root = ROOT / "app/src/main/kotlin"
    direct = []
    for path in source_root.rglob("*.kt"):
        if path.name == SOURCE.name:
            continue
        text = path.read_text()
        if "android.util.Log" in text or "Log.d(" in text or "Log.e(" in text:
            direct.append(path.relative_to(ROOT).as_posix())
    assert direct == [], direct


def main() -> None:
    test_url_redaction()
    test_text_redaction()
    test_header_policy()
    test_source_has_no_raw_logging_paths()
    test_no_direct_loggers_elsewhere()
    print("diagnostic redaction tests: PASS")


if __name__ == "__main__":
    main()
