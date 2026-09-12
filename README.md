# ProComic Mihon Extension

[![CI Build](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/workflows/ci.yml/badge.svg)](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/workflows/ci.yml) [![Version](https://img.shields.io/badge/version-1.5%20(6)-blue)](https://github.com/LoneVertex/mihon-extension-ar-procomic/releases) ![Platform](https://img.shields.io/badge/Platform-Mihon%20%2F%20Android-green)

ProComic is an Arabic Mihon extension for manga, manhwa, and manhua available from [procomic.net](https://procomic.net). It provides server-side Search, verified Popular and Latest feeds, canonical Details parsing, REST chapter listing, Arabic/English chapter normalization, conservative paid-chapter visibility, and a high-resilience raw-HTTP Reader with automatic dual-domain failover (`procomic.pro` <-> `procomic.net`) that reconstructs protected pages through the site’s documented public media contracts.

## Current Repository State


| Property | Value |
|---|---|
| Package | `eu.kanade.tachiyomi.extension.ar.procomic` |
| Source class | `eu.kanade.tachiyomi.extension.ar.procomic.ProComic` |
| Catalog language | Arabic (`ar`) with Arabic and English releases |
| Base domain | `https://procomic.net` |
| Version | `versionCode=6`, `versionName=1.5` |
| Implementation branch | `main` |
| Implementation baseline | Dual-domain hybrid reader engine (`procomic.pro` & `procomic.net`), Cloudflare clearance compatibility, coin-locked chapter gating |
| Latest fix | Resolved HTTP 403 / "Check website in WebView" loop by delegating User-Agent to device WebView profile, filtered broken cdn chapter images, classified coin-locked chapters with null cost, added dynamic image referers |
| Runtime status | v1.5 released: Cloudflare clearance verified, coin-locked chapter paywall detection and hiding implemented, unroutable cdn chapter URLs filtered out. All 13 test suites pass. |

## Current Architecture

The extension uses normal OkHttp requests through Mihon’s `HttpSource` API. Public JSON endpoints are used where the site exposes a verified contract, while bounded RSC parsing is retained for responses that require it. The extension does not contain a WebView, login flow, authentication mechanism, cookie/session bypass, payment bypass, or browser automation.

| Feature | Current contract and behavior |
|---|---|
| Search | `GET /api/public/series/search?status=approved&limit=50&page=N&sort=latest&search=...`, with optional type filtering. The parser consumes a bounded batch of up to six pages, applies title-like relevance filtering, ranks visible-title matches above original-title/alias matches and slug-only matches, collapses duplicate series identities, and returns the collected batch without an unbounded continuation. |
| Popular | `GET /api/public/content/popular-new?limit=20`; novel rows are filtered, duplicate series IDs are removed, `coverImage`/thumbnail URLs are normalized, and no fabricated continuation is reported. |
| Latest | `GET /api/public/content/latest-updates?limit=18&category=comics&page=N`; strictly queries comic entries to bypass server novel flooding, server order is preserved, short non-empty pages continue, and an empty data array terminates pagination. |
| Details | The source’s internal manga URL is converted to the live canonical `/ar/series/{slug}-{id}` RSC route. Complete and restricted response shapes are handled separately. |
| Chapters | `GET /api/chapters?contentId={seriesId}&_u=...`, followed by authoritative `hasMore` pagination, approval filtering, Arabic preference, English fallback, deduplication, and deterministic descending ordering. |
| Reader | The canonical chapter route is requested as raw HTTP with automatic bidirectional domain fallback (`procomic.pro` / `procomic.net`). Public manifests expose initial pages from `app.procomic.pro` or `app.procomic.net`; sibling `deferredMedia` is fetched from the chapter-deferred-media contract, direct deferred images are appended, and protected-page placeholders are resolved through the chapter-map proxy-plan contract, tile reconstruction, and JPEG synthesis. |
| Lifecycle status | The site’s top-level `progress` field is mapped to Mihon `ONGOING`, `COMPLETED`, `ON_HIATUS`, or `CANCELLED`; `status=approved` and `metadata.viewStatus=public/exclusive` are not treated as publication lifecycle values. |
| Icon | The launcher resources use the official ProComic favicon from `https://procomic.net/favicon.svg`, rasterized across the required Android density resources. |

## Reader and Protected Pages

A protected Reader page is not fabricated. Mihon receives a page placeholder containing the site’s own short-lived map capability. At image-request time, the interceptor requests a fresh proxy plan for that chapter, validates the returned geometry and evidence-derived tile URLs, downloads bounded AVIF pieces, reconstructs the page into a normal bitmap, and returns a JPEG response to Mihon. Map responses and tile bodies are read with explicit byte bounds. The decoder chain is `BitmapFactory`, then Android `ImageDecoder` where available, then the official AOMedia AVIF decoder through a direct bounded `ByteBuffer` path. The AOMedia fallback selects `ARGB_8888` for ordinary 8-bit tiles and `RGBA_F16` for deeper images after validating decoded dimensions and pixel limits.

The chapter-131 regression hardened tile decoding with an `ImageDecoder` fallback, explicit output configuration, per-tile and map-response byte limits, tile-count and composite-pixel bounds, and diagnostic metadata that never records raw response bodies or sensitive headers. The generic Reader remediation uses official AOMedia `org.aomedia.avif.android:avif:1.3.0.841110fd`, whose live-verified API and four-ABI package cover the YUV444 AVIF tiles observed in series 387 / chapter 19273 as well as earlier protected layouts. Android/device rendering still requires physical acceptance testing; the chapter-specific public contract is covered by a redacted fixture. Gradle uses `useLegacyPackaging=true` so bundled native libraries are extracted at install time.

## Chapter and Gate Rules

Arabic chapters are preferred when Arabic and English records share the same chapter identity. English is retained only as a fallback when no Arabic record exists. Deduplication and ordering occur before the paid-chapter visibility preference is applied, so hiding paid chapters cannot cause an English mirror to reappear unexpectedly.

The persistent preference `show_paid_chapters` defaults to `true`. When disabled, only chapters classified positively as `COIN_LOCKED`, `EXCLUSIVE`, `SHORTLINK_UNLOCK`, or `PERMANENTLY_LOCKED` are hidden. Incomplete or conflicting gate data becomes `UNKNOWN` and remains visible. `RESTRICTED_AUTH_REQUIRED` is a separate access state, is never inferred from denial alone, and remains visible when paid chapters are hidden. No synthetic `isPaid` field is used.

## Validation Status

The deterministic software gate passes all 13 repository test suites, `git diff --check`, protected-path checks, and clean debug/release APK builds on every push and PR to `main`. CI ✅ — latest passing run on `main` at commit `6905482`. The exact test inventory and APK identities are recorded in [`docs/VALIDATION.md`](docs/VALIDATION.md).

Reported Android testing identified the earlier Search false-positive behavior, the three-page Reader symptom, chapter-131 tile decoding failure, trust-transition/native-loading failure, and `Unknown` publication status. The exact series-387/chapter-19273 failure is now covered by a redacted fixture proving two protected maps, nine valid AVIF tiles, YUV444 characteristics, and the AOMedia decode path. Live public probing confirmed the exact deferred-media/proxy-plan contract; direct Android-device rendering of the new APK is still **NOT VERIFIED** here.

The version 1.4 release APK is approximately 2.1 MB (signed v2+v3) because the official AOMedia decoder ships one compact native AVIF library per ABI. No ABI split was applied without Mihon distribution evidence; the measured footprint and trade-off are recorded in [`docs/VALIDATION.md`](docs/VALIDATION.md). The release APK is signed with the project keystore (RSA 4096, alias `procomic`, valid to 2051) using v2+v3 signature schemes.

## Known Limitations

Authentication and full paid access are not implemented. Restricted/auth-required content remains visible as a distinct access state, but authenticated behavior is not provided or claimed as validated. Public-image availability can still be constrained by server-side access rules. The extension does not provide a WebView fallback and does not bypass login, session, payment, or safe-browsing controls. Novel-type content is excluded because Mihon is a comic reader.

A thin black line between otherwise complete images is a Mihon Reader layout symptom, not a ProComic image boundary, when the page bytes and map rectangles are complete. Mihon documents a configurable Reader background color, and [Mihon issue #696](https://github.com/mihonapp/mihon/issues/696) records the same intermittent black stripe between Long Strip pages. The extension cannot control Mihon’s inter-page gutter without collapsing all chapter pages into one image, which would break page navigation, progress, memory limits, and source semantics. For the supplied symptom, compare with Mihon’s Background color setting and the latest Mihon build; do not treat this viewer-owned stripe as a missing ProComic page.

## Build and Test

Use JDK 21 and the repository’s Gradle wrapper. The wrapper currently resolves Gradle 8.14.4. Install Android API 35 and the test-only dependency before running the deterministic suite, then use the documented software-gate command:

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
./gradlew :app:lint --no-daemon --stacktrace
```

## Documentation and Handoff

[`docs/PROCOMIC_SYSTEM.md`](docs/PROCOMIC_SYSTEM.md) is the current engineering reference. [`docs/VALIDATION.md`](docs/VALIDATION.md) contains the complete software gate and evidence record. [`docs/HANDOFF.md`](docs/HANDOFF.md) is the current operational handoff. [`docs/BRANCH_TOPOLOGY.md`](docs/BRANCH_TOPOLOGY.md) records the live 36-commit stack and PR topology. [`docs/HANDOFF_FINAL.md`](docs/HANDOFF_FINAL.md) and [`docs/research/procomic-recon.md`](docs/research/procomic-recon.md) are retained historical artifacts and are not current implementation instructions.

## Contribution and Release Workflow

All remediation branches (PR #10, #11, #12, #13) and subsequent fixes are merged into `main`. The codebase is consolidated on `main` with all 13 contract test suites passing in CI. Releases are built and signed with the project keystore (`~/.android/procomic.keystore`, RSA 4096, v2+v3 schemes). GitHub Releases and tags are published following physical Android device smoke testing.

> For detailed architecture, validation evidence, branch state, and remaining approval-gated operations, use the current documents under [`docs/`](docs/).
