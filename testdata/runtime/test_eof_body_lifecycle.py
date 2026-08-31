"""Regression tests for the shared Search/Popular/Latest EOF failure."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "testdata" / "runtime" / "runtime_eof_fixtures.json"
SOURCE = ROOT / "app" / "src" / "main" / "kotlin" / "eu" / "kanade" / "tachiyomi" / "extension" / "ar" / "procomic" / "ProComic.kt"


class ExplicitResponseFailure(Exception):
    pass


def legacy_exact_count_read(payload: bytes, requested: int) -> bytes:
    """Model Okio readByteArray(byteCount): exact count or EOFException."""
    if len(payload) < requested:
        raise EOFError("source ended before exact byte count")
    return payload[:requested]


def bounded_at_most_read(payload: bytes, maximum: int, chunk_size: int = 16_384) -> bytes:
    """Model the fixed source: read at most maximum + 1, then reject overflow."""
    result = bytearray()
    offset = 0
    while len(result) <= maximum:
        remaining = maximum + 1 - len(result)
        if offset >= len(payload):
            break
        chunk = payload[offset : offset + min(remaining, chunk_size)]
        result.extend(chunk)
        offset += len(chunk)
        if len(result) > maximum:
            raise ExplicitResponseFailure("response exceeds configured limit")
    return bytes(result)


def parse_non_empty_json(payload: bytes) -> object:
    if not payload:
        raise ExplicitResponseFailure("empty response body")
    try:
        return json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ExplicitResponseFailure("invalid JSON response") from exc


def main() -> None:
    data = json.loads(FIXTURE.read_text())
    maximum = data["max_response_bytes"]

    # The old exact-count call fails on all three valid live response sizes.
    for record in data["valid_payloads"].values():
        payload = b"{" + b"x" * (record["bytes"] - 2) + b"}"
        try:
            legacy_exact_count_read(payload, maximum + 1)
        except EOFError:
            pass
        else:
            raise AssertionError("legacy exact-count read unexpectedly accepted a short body")

    # The fixed at-most reader preserves each valid body and permits independent decoding.
    for name, record in data["valid_payloads"].items():
        if name == "search_dragon":
            payload = json.dumps(record["json"]).encode("utf-8")
        else:
            payload = json.dumps(record["json"]).encode("utf-8")
        assert parse_non_empty_json(bounded_at_most_read(payload, maximum)) == record["json"]

    # Empty and truncated bodies fail explicitly at parsing, not as silent empty feeds.
    try:
        parse_non_empty_json(b"")
    except ExplicitResponseFailure:
        pass
    else:
        raise AssertionError("empty response did not fail explicitly")

    try:
        parse_non_empty_json(data["edge_cases"]["truncated_json"]["text"].encode())
    except ExplicitResponseFailure:
        pass
    else:
        raise AssertionError("truncated response did not fail explicitly")

    # Oversized bodies remain protected.
    try:
        bounded_at_most_read(b"x" * (maximum + 1), maximum)
    except ExplicitResponseFailure:
        pass
    else:
        raise AssertionError("oversized response bypassed the response limit")

    source = SOURCE.read_text()
    assert "body.source().readByteArray(MAX_RESPONSE_BYTES.toLong() + 1L)" not in source
    assert "val buffer = Buffer()" in source
    assert "source.read(buffer" in source
    assert "MAX_RESPONSE_BYTES.toLong() + 1L - buffer.size" in source
    for method in ("searchMangaParse", "popularMangaParse", "latestUpdatesParse"):
        start = source.index(method)
        end = source.find("override fun", start + len(method))
        segment = source[start:] if end < 0 else source[start:end]
        assert "readBoundedBody(response)" in segment, method

    print("runtime EOF tests: PASS (shared exact-read failure reproduced, at-most bounded read validated, feed payloads parse, edge failures explicit)")


if __name__ == "__main__":
    main()
