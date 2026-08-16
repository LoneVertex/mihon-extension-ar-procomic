#!/usr/bin/env python3
"""Deterministic tests for the raw ProComic Details RSC contract.

The Android module has no existing JVM test source set or test framework dependency,
so this standard-library test validates the exact raw fixtures that the Kotlin parser
must handle. It intentionally treats redirect-only and invalid payloads as failures.
"""

import json
from pathlib import Path

ROOT = Path(__file__).parent


def object_after_key(body: str, key: str) -> str | None:
    marker = f'"{key}":'
    key_index = body.find(marker)
    if key_index < 0:
        return None
    start = body.find("{", key_index + len(marker))
    if start < 0:
        return None

    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(body)):
        char = body[index]
        if escaped:
            escaped = False
            continue
        if in_string and char == "\\":
            escaped = True
            continue
        if char == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return body[start : index + 1]
    return None


def parse_series(body: str) -> dict:
    object_text = object_after_key(body, "series")
    if object_text is None:
        raise ValueError("ProComic: canonical series object not found")
    value = json.loads(object_text)
    if value.get("type") == "novel":
        raise ValueError("novel series is not supported")
    return value


def expect_failure(path: Path) -> None:
    try:
        parse_series(path.read_text(errors="replace"))
    except ValueError:
        return
    raise AssertionError(f"expected diagnosable failure for {path.name}")


def main() -> None:
    canonical = parse_series((ROOT / "canonical_678.rsc").read_text(errors="replace"))
    assert canonical["id"] == 678
    assert canonical["title"] == "Dream Reincarnation: I Became the Supreme Master of Martial Arts"
    assert canonical["slug"] == "dream-reincarnation-i-became-the-supreme-master-of-martial-arts"
    assert canonical["type"] == "manhua"
    assert canonical["status"] == "approved"
    assert canonical["thumbnail"].startswith("https://")
    assert canonical["metadata"]["author"]
    assert canonical["metadata"]["artist"]
    assert canonical["metadata"]["genres"]
    assert canonical["metadata"]["descriptions"]["ar"]

    # The typed-route response is a redirect digest and must not be treated as Details success.
    expect_failure(ROOT / "redirecting_678.rsc")
    expect_failure(ROOT / "invalid_non_details.rsc")
    print("details contract tests: PASS (canonical, redirecting, invalid)")


if __name__ == "__main__":
    main()
