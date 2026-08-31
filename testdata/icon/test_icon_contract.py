#!/usr/bin/env python3
"""Deterministic contract tests for the official ProComic extension icon."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "testdata/icon/official_icon_fixture.json"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
RES = ROOT / "app/src/main/res"


def load() -> dict:
    return json.loads(FIXTURE.read_text())


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def test_manifest_uses_mipmap_launcher() -> None:
    assert 'android:icon="@mipmap/ic_launcher"' in MANIFEST.read_text()


def test_density_resources_match_verified_official_asset_outputs() -> None:
    fixture = load()
    assert fixture["source_url"] == "https://procomic.net/favicon.svg"
    assert fixture["source_content_type"] == "image/svg+xml"
    assert fixture["source_title"] == "ProChan Icon"
    for density, expected in fixture["densities"].items():
        path = RES / density / "ic_launcher.png"
        assert path.is_file(), path
        with Image.open(path) as image:
            assert image.size == (expected["size"], expected["size"])
            assert image.mode == "RGBA"
            image.verify()
        assert sha256(path) == expected["sha256"]


def test_all_required_density_variants_are_present() -> None:
    assert set(load()["densities"]) == {
        "mipmap-mdpi",
        "mipmap-hdpi",
        "mipmap-xhdpi",
        "mipmap-xxhdpi",
        "mipmap-xxxhdpi",
    }


def main() -> None:
    test_manifest_uses_mipmap_launcher()
    test_density_resources_match_verified_official_asset_outputs()
    test_all_required_density_variants_are_present()
    print("icon contract tests: PASS (official favicon provenance and all density resources)")


if __name__ == "__main__":
    main()
