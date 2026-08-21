# ProComic Mihon Extension

[![CI Build](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/workflows/ci.yml/badge.svg)](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/workflows/ci.yml) [![Implementation PR](https://img.shields.io/badge/implementation-PR%20%2311-blue)](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/11) ![Platform](https://img.shields.io/badge/Platform-Mihon%20%2F%20Android-green)

ProComic is an Arabic Mihon extension for manga, manhwa, and manhua available from [procomic.net](https://procomic.net). It provides server-side Search, verified Popular and Latest feeds, canonical Details parsing, REST chapter listing, Arabic/English chapter normalization, conservative paid-chapter visibility, and a raw-HTTP Reader that reconstructs protected pages through the site’s documented public media contracts.

## Current Repository State

The implementation baseline on [`fix/runtime-eof-search-feeds`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/runtime-eof-search-feeds) is commit [`8f88ec9fe839cbbca9076cd0c866f287a7b684dd`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/8f88ec9fe839cbbca9076cd0c866f287a7b684dd). The latest audit-remediation HEAD is [`f3f4290d13f1bf204b0278e25a01235a77ba0087`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/f3f4290d13f1bf204b0278e25a01235a77ba0087), adding deterministic CI contract coverage, least-privilege workflow permissions, and a pinned Pillow test dependency after the initial CI reproduction exposed a missing runner dependency. It is reviewed through stacked [PR #11](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/11), targeting [`fix/full-remediation`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/full-remediation), above [PR #10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10), which targets `main`. Both PRs remain open; no merge, tag, or GitHub Release is implied by this documentation.

| Property | Value |
|---|---|
| Package | `eu.kanade.tachiyomi.extension.ar.procomic` |
| Source class | `eu.kanade.tachiyomi.extension.ar.procomic.ProComic` |
| Catalog language | Arabic (`ar`) with Arabic and English releases |
| Base domain | `https://procomic.net` |
| Version | `versionCode=2`, `versionName=1.1` |
| Implementation branch | `fix/runtime-eof-search-feeds` |
| Implementation baseline | `8f88ec9fe839cbbca9076cd0c866f287a7b684dd` |
| Latest audit-remediation HEAD | `f3f4290d13f1bf204b0278e25a01235a77ba0087` |
| Latest review PR | [#11](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/11), stacked above [#10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) |
| Default branch | `main` remains unchanged at `76c8ed49ee81d066d30cebe6e412040db2d43a73` |
| Runtime status | Lifecycle-status, protected-reader, and CI coverage fixes are implemented; corrected push/PR CI and local gates pass. Direct Android-device rendering remains not verified in this sandbox; authenticated/premium behavior remains outside scope. |

## Current Architecture

The extension uses normal OkHttp requests through Mihon’s `HttpSource` API. Public JSON endpoints are used where the site exposes a verified contract, while bounded RSC parsing is retained for responses that require it. The extension does not contain a WebView, login flow, authentication mechanism, cookie/session bypass, payment bypass, or browser automation.

| Feature | Current contract and behavior |
|---|---|
| Search | `GET /api/public/series/search?status=approved&limit=50&page=N&sort=latest&search=...`, with optional type filtering. The parser consumes a bounded batch of up to six pages, applies title-like relevance filtering, ranks visible-title matches above original-title/alias matches and slug-only matches, collapses duplicate series identities, and returns the collected batch without an unbounded continuation. |
| Popular | `GET /api/public/content/popular-new?limit=20`; novel rows are filtered, duplicate series IDs are removed, cover URLs are normalized, and no fabricated continuation is reported. |
| Latest | `GET /api/public/content/latest-updates?limit=18&category=all&page=N`; server order is preserved, short non-empty pages continue, and an empty data array terminates pagination. |
| Details | The source’s internal manga URL is converted to the live canonical `/ar/series/{slug}-{id}` RSC route. Complete and restricted response shapes are handled separately. |
| Chapters | `GET /api/chapters?contentId={seriesId}&_u=...`, followed by authoritative `hasMore` pagination, approval filtering, Arabic preference, English fallback, deduplication, and deterministic descending ordering. |
| Reader | The canonical chapter route is requested as raw HTTP. Public manifests commonly expose three direct pages; sibling `deferredMedia` is fetched from the chapter-deferred-media contract, direct deferred images are appended, and protected-page placeholders are resolved through the chapter-map proxy-plan contract, tile reconstruction, and JPEG synthesis. Observed chapters can therefore expose the remaining protected pages rather than stopping at three. |
| Lifecycle status | The site’s top-level `progress` field is mapped to Mihon `ONGOING`, `COMPLETED`, `ON_HIATUS`, or `CANCELLED`; `status=approved` and `metadata.viewStatus=public/exclusive` are not treated as publication lifecycle values. |
| Icon | The launcher resources use the official ProComic favicon from `https://procomic.net/favicon.svg`, rasterized across the required Android density resources. |

## Reader and Protected Pages

A protected Reader page is not fabricated. Mihon receives a page placeholder containing the site’s own short-lived map capability. At image-request time, the interceptor requests a fresh proxy plan for that chapter, validates the returned geometry and evidence-derived tile URLs, downloads bounded AVIF pieces, reconstructs the page into a normal bitmap, and returns a JPEG response to Mihon. Map responses and tile bodies are read with explicit byte bounds. The decoder chain is `BitmapFactory`, then Android `ImageDecoder` where available, then the lazily initialized AVIF decoder with `PreferredColorConfig.RGBA_8888` followed by the library’s default-color decode as a compatibility fallback.

The chapter-131 regression hardened tile decoding with an `ImageDecoder` fallback, explicit RGBA output, per-tile and map-response byte limits, tile-count and composite-pixel bounds, and diagnostic metadata that never records raw response bodies or sensitive headers. Native AVIF loading is lazy so Mihon can complete extension discovery and trust transitions without eagerly loading the native library. Gradle uses `useLegacyPackaging=true` so the bundled native libraries are extracted at install time.

## Chapter and Gate Rules

Arabic chapters are preferred when Arabic and English records share the same chapter identity. English is retained only as a fallback when no Arabic record exists. Deduplication and ordering occur before the paid-chapter visibility preference is applied, so hiding paid chapters cannot cause an English mirror to reappear unexpectedly.

The persistent preference `show_paid_chapters` defaults to `true`. When disabled, only chapters classified positively as `COIN_LOCKED`, `EXCLUSIVE`, `SHORTLINK_UNLOCK`, or `PERMANENTLY_LOCKED` are hidden. Incomplete or conflicting gate data becomes `UNKNOWN` and remains visible. `RESTRICTED_AUTH_REQUIRED` is a separate access state, is never inferred from denial alone, and remains visible when paid chapters are hidden. No synthetic `isPaid` field is used.

## Validation Status

The deterministic software gate passes all 12 repository test suites, `git diff --check`, protected-path checks, and debug/release CI builds. The workflow now installs `Pillow==12.3.0` from `requirements-test.txt`, sets `permissions: contents: read`, and uses `actions/checkout@v7`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`, and `actions/upload-artifact@v7`. The first suite-enabled runs failed because Pillow was absent on the runner ([32500306071](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500306071), [32500309639](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500309639)); the corrected push and PR runs passed ([32500561810](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500561810), [32500566137](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500566137)). The exact test inventory and APK identities are recorded in [`docs/VALIDATION.md`](docs/VALIDATION.md).

Reported Android testing identified the earlier Search false-positive behavior, the three-page Reader symptom, chapter-131 tile decoding failure, trust-transition/native-loading failure, and `Unknown` publication status. The corresponding fixes are covered by deterministic fixtures and CI assertions. Live public probing confirmed the deferred-media/proxy-plan contract returns seven protected maps and valid AVIF tiles for the captured chapter; direct Android-device rendering is still not verified here.

The release APK is approximately 21.46 MB and the debug APK approximately 23.28 MB because `avif-coder` bundles native AVIF/HEIF libraries for four ABIs. This is materially larger than pure-Kotlin extensions, but it is expected for a universal native-decoder APK. No ABI split was applied without Mihon distribution evidence; the measured footprint and trade-off are recorded in [`docs/VALIDATION.md`](docs/VALIDATION.md). The release build is debug-keystore signed for sideload/testing; a production release requires maintainer-owned signing credentials.

## Known Limitations

Authentication and full paid access are not implemented. Restricted/auth-required content remains visible as a distinct access state, but authenticated behavior is not provided or claimed as validated. Public-image availability can still be constrained by server-side access rules. The extension does not provide a WebView fallback and does not bypass login, session, payment, or safe-browsing controls. Novel-type content is excluded because Mihon is a comic reader.

## Build and Test

Use JDK 21 and the repository’s Gradle wrapper. The wrapper currently resolves Gradle 8.14.4. Install the test-only dependency before running the deterministic suite, then use the documented software-gate command:

```bash
ANDROID_HOME=/home/ubuntu/android-sdk \
ANDROID_SDK_ROOT=/home/ubuntu/android-sdk \
./gradlew clean :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
```

The APKs are written to `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`. Run all deterministic tests with:

```bash
python3 -m pip install --disable-pip-version-check --no-input -r requirements-test.txt
for test in $(find testdata -type f -name 'test_*.py' | sort); do
  python3 "$test" || exit 1
done
git diff --check
```

## Documentation and Handoff

[`docs/PROCOMIC_SYSTEM.md`](docs/PROCOMIC_SYSTEM.md) is the current engineering reference. [`docs/VALIDATION.md`](docs/VALIDATION.md) contains the complete software gate and evidence record. [`docs/HANDOFF.md`](docs/HANDOFF.md) is the current operational handoff. [`docs/BRANCH_TOPOLOGY.md`](docs/BRANCH_TOPOLOGY.md) records the 26-commit stack and PR topology. [`docs/HANDOFF_FINAL.md`](docs/HANDOFF_FINAL.md), [`autonomous-extension-fix-prompt.md`](autonomous-extension-fix-prompt.md), and [`docs/research/procomic-recon.md`](docs/research/procomic-recon.md) are retained historical artifacts and are not current implementation instructions.

## Contribution and Release Workflow

Focused implementation, CI, and documentation changes remain independently committed and reviewed through pull requests. PR #11 must not be merged into its stacked base, and PR #10 must not be merged into `main`, without explicit approval for those operations. Dependabot PRs #1–#9 remain open for compatibility review. No tag or GitHub Release currently exists; creating one is a separate release decision.

This audit changed only the approved `fix/runtime-eof-search-feeds` branch; it did not modify `main`, merge or close PRs, create tags/releases, or alter authentication/payment behavior.

> For detailed architecture, validation evidence, branch state, and remaining approval-gated operations, use the current documents under [`docs/`](docs/).
