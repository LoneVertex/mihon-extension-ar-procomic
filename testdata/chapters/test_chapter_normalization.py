#!/usr/bin/env python3
"""Deterministic contract tests for the ProComic chapter normalization policy.

These tests mirror the explicitly documented policy against captured chapter DTO
fixtures. They are dependency-free and provide a stable contract while Android
instrumentation remains external to this repository environment.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "testdata" / "chapters" / "chapter_normalization_fixtures.json"


def lang_code(value: str) -> str:
    return value.strip().upper() or "UNKNOWN"


def identity(record: dict[str, Any]) -> tuple[str, Any]:
    raw = str(record["chapter_number"]).strip()
    try:
        return ("numeric", float(raw))
    except ValueError:
        return ("special", raw.lower())


def timestamp(record: dict[str, Any]) -> str:
    return str(record.get("published_at") or record.get("created_at") or "")


def normalize(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    groups: dict[tuple[str, Any], list[dict[str, Any]]] = {}
    for record in records:
        groups.setdefault(identity(record), []).append(record)

    selected: list[dict[str, Any]] = []
    for group in groups.values():
        ar = [r for r in group if lang_code(r.get("language", "")) == "AR"]
        en = [r for r in group if lang_code(r.get("language", "")) == "EN"]
        if ar:
            candidates, fallback = ar, False
        elif en:
            candidates, fallback = en, True
        else:
            candidates, fallback = group, False
        chosen = max(candidates, key=lambda r: (timestamp(r), int(r["id"])))
        result = dict(chosen)
        result["language"] = lang_code(result.get("language", ""))
        result["is_english_fallback"] = fallback
        result["language_display"] = "Arabic" if result["language"] == "AR" else (
            "English" if result["language"] == "EN" else result["language"]
        )
        selected.append(result)

    def sort_key(record: dict[str, Any]) -> tuple[int, float, str, int]:
        try:
            return (0, -float(record["chapter_number"]), "", -int(record["id"]))
        except ValueError:
            return (1, 0.0, str(record["chapter_number"]).lower(), -int(record["id"]))

    return sorted(selected, key=sort_key)


def assert_series(name: str, records: list[dict[str, Any]], expected_ids: list[int]) -> None:
    result = normalize(records)
    ids = [int(r["id"]) for r in result]
    assert ids == expected_ids, f"{name}: expected IDs {expected_ids}, got {ids}"
    assert len({identity(r) for r in result}) == len(result), f"{name}: duplicate identity"
    numeric = [float(r["chapter_number"]) for r in result if str(r["chapter_number"]).replace('.', '', 1).isdigit()]
    assert numeric == sorted(numeric, reverse=True), f"{name}: numeric order is not descending"
    assert all(r["language"] == "AR" for r in result), f"{name}: AR should be preferred"


def main() -> None:
    data = json.loads(FIXTURE.read_text())
    assert_series("series-690", data["series_690"], [52265, 51488, 51008, 50825, 50824, 50822, 50821])
    assert_series("series-693", data["series_693"], [51598, 51597, 51596, 51595, 51594, 51593, 51592, 51591])
    assert_series("series-678", data["series_678"], [46606, 46601, 46600, 46599])

    fallback = normalize(data["ar_fallback"])
    assert len(fallback) == 2
    assert [r["id"] for r in fallback] == [7002, 7001]
    assert fallback[0]["language"] == "EN" and fallback[0]["is_english_fallback"]
    assert fallback[0]["language_display"] == "English"

    unknown = normalize(data["unknown_language"])
    assert len(unknown) == 1
    assert unknown[0]["language"] == "JP"
    assert unknown[0]["language_display"] == "JP"

    special = normalize(data["special_number"])
    assert [r["chapter_number"] for r in special] == ["2", "bonus"]

    gated = normalize(data["gate_metadata"])[0]
    assert gated["coins_unlocks"] == 5
    assert gated["shortlink_unlocks"] == 12
    assert gated["supportMode"] == "default"
    assert gated["lockedByCoins"] is True
    assert "isPaid" not in gated

    same_language = normalize(data["same_language_duplicate"])
    assert [r["id"] for r in same_language] == [8303]
    assert same_language[0]["translator"] == "same-time-higher-id"

    timestamp_tie = normalize(data["timestamp_tie"])
    assert [r["id"] for r in timestamp_tie] == [8402]

    decimals = normalize(data["decimal_and_unusual"])
    assert [r["id"] for r in decimals] == [8502, 8501, 8503]
    assert decimals[0]["chapter_number"] == " 2.0 "

    special_labels = normalize(data["special_labels"])
    assert {r["chapter_number"].strip().lower() for r in special_labels} == {"prologue", "s1", "bonus"}
    assert all(identity(r)[0] == "special" for r in special_labels)

    unknown_variants = normalize(data["unknown_variants"])
    assert [r["language"] for r in unknown_variants] == ["XX", "UNKNOWN"]
    assert all(not r["is_english_fallback"] for r in unknown_variants)

    print("chapter normalization tests: PASS (series 690/693/678, fallback, unknown, special, gates, ties, decimals)")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as exc:
        print(f"chapter normalization tests: FAIL: {exc}", file=sys.stderr)
        raise
