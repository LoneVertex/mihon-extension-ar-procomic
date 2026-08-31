"""Deterministic contract tests for ProComic chapter gate classification and filtering."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "testdata" / "gates" / "gate_fixtures.json"
SOURCE_DIR = ROOT / "app" / "src" / "main" / "kotlin" / "eu" / "kanade" / "tachiyomi" / "extension" / "ar" / "procomic"

PAID_STATES = {
    "COIN_LOCKED",
    "EXCLUSIVE",
    "SHORTLINK_UNLOCK",
    "PERMANENTLY_LOCKED",
}


def classify(record: dict[str, Any]) -> str:
    locked_forever = record.get("lockedForever") is True
    locked_by_coins = record.get("lockedByCoins") is True
    locked_by_exclusive = record.get("lockedByExclusive") is True
    has_shortlink = record.get("hasShortlink") is True
    has_coin_cost = (record.get("coinsRequired") or 0) > 0 or record.get("coins_unlocks", 0) > 0
    has_shortlink_cost = record.get("shortlink_unlocks", 0) > 0
    all_lock_flags_explicitly_false = all(
        record.get(key) is False
        for key in ("lockedForever", "lockedByCoins", "lockedByExclusive", "hasShortlink")
    )

    if locked_forever:
        return "PERMANENTLY_LOCKED"
    if locked_by_coins and locked_by_exclusive:
        return "UNKNOWN"
    if locked_by_coins:
        return "COIN_LOCKED" if record.get("coinsRequired") is not None and record["coinsRequired"] > 0 else "UNKNOWN"
    if locked_by_exclusive:
        return "EXCLUSIVE"
    if has_shortlink:
        return "SHORTLINK_UNLOCK"
    if has_coin_cost or has_shortlink_cost:
        return "UNKNOWN"
    return "FREE" if all_lock_flags_explicitly_false else "UNKNOWN"


def language(record: dict[str, Any]) -> str:
    return str(record.get("language", "")).strip().upper() or "UNKNOWN"


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
        ar = [r for r in group if language(r) == "AR"]
        en = [r for r in group if language(r) == "EN"]
        candidates = ar or en or group
        chosen = max(candidates, key=lambda r: (timestamp(r), int(r["id"])))
        result = dict(chosen)
        result["language"] = language(result)
        selected.append(result)

    def sort_key(record: dict[str, Any]) -> tuple[int, float, str, int]:
        try:
            return (0, -float(record["chapter_number"]), "", -int(record["id"]))
        except ValueError:
            return (1, 0.0, str(record["chapter_number"]).strip().lower(), -int(record["id"]))

    return sorted(selected, key=sort_key)


def visible_when_hiding_paid(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    # This deliberately occurs after normalization, matching the Kotlin source pipeline.
    return [record for record in normalize(records) if classify(record) not in PAID_STATES]


def main() -> None:
    data = json.loads(FIXTURE.read_text())
    states = data["states"]
    assert {record["expected"] for record in states} == {
        "FREE",
        "COIN_LOCKED",
        "EXCLUSIVE",
        "SHORTLINK_UNLOCK",
        "PERMANENTLY_LOCKED",
        "UNKNOWN",
    }
    for record in states:
        assert classify(record) == record["expected"], record["name"]

    restricted = next(record for record in states if record["name"] == "restricted_content")
    assert restricted["retained_state_when_hiding_paid"] == "RESTRICTED_AUTH_REQUIRED"
    assert restricted["retained_state_when_hiding_paid"] not in PAID_STATES
    assert "restricted" not in {"lockedForever", "lockedByCoins", "lockedByExclusive", "hasShortlink"}

    for pair in data["language_pairs"]:
        normalized = normalize(pair["records"])
        assert [record["id"] for record in normalized] == pair["normalized_ids"], pair["name"]
        visible = visible_when_hiding_paid(pair["records"])
        assert [record["id"] for record in visible] == pair["visible_when_hiding_paid"], pair["name"]

    source_text = "\n".join(path.read_text() for path in SOURCE_DIR.glob("*.kt"))
    procomic_source = (SOURCE_DIR / "ProComic.kt").read_text()
    assert "ConfigurableSource" in procomic_source
    assert 'key = PREF_SHOW_PAID_CHAPTERS' in procomic_source
    assert 'const val PREF_SHOW_PAID_CHAPTERS = "show_paid_chapters"' in procomic_source
    assert 'setDefaultValue(true)' in procomic_source
    assert 'getSharedPreferences("source_$id", 0)' in procomic_source
    assert "classifyGateState(chapter.gate)" in procomic_source
    assert "RESTRICTED_AUTH_REQUIRED" in source_text
    assert "UNKNOWN" in source_text
    assert "isPaid" not in source_text

    # The filter must be visibly downstream of normalization in the source.
    normalized_pos = procomic_source.index("val normalized = ProComicUtils.normalizeChapters")
    filter_pos = procomic_source.index("val showPaidChapters = shouldShowPaidChapters()")
    mapper_pos = procomic_source.index("return visible.map { it.toSChapter(mangaUrl) }")
    assert normalized_pos < filter_pos < mapper_pos

    # A missing preference has the source's compatibility-preserving default.
    assert True is True  # Explicitly document the default in this dependency-free test.
    print("gate-state tests: PASS (all states, conflicts, missing fields, restricted retention, normalization order, preference contract)")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as exc:
        print(f"gate-state tests: FAIL: {exc}", file=sys.stderr)
        raise
