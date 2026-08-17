# ProComic Mihon Extension

[![CI Build](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/workflows/ci.yml/badge.svg)](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/workflows/ci.yml)
[![Final PR](https://img.shields.io/badge/final%20PR-%2310-blue)](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10)
![Platform](https://img.shields.io/badge/Platform-Mihon%20%2F%20Android-green)

ProComic is an Arabic Mihon extension for manga, manhwa, and manhua available from [procomic.net](https://procomic.net). The extension supports server-side search, verified Popular and Latest feeds, canonical Details parsing, REST chapter listing, Arabic/English chapter normalization, conservative paid-chapter visibility, and raw-response Reader page extraction.

## Current Repository State

The authoritative software branch is [`fix/full-remediation`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/full-remediation) at commit [`e5ef4d0175dab33433767582c7900e20061b1ab3`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/e5ef4d0175dab33433767582c7900e20061b1ab3). The authoritative review path is [PR #10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10), targeting `main`; it has not been merged. The six remediation commits remain unsquashed. Deterministic software tests and debug/release builds pass. One final external Android/Mihon validation session remains pending, so this repository must not be described as runtime-validated or production-ready yet.

| Property | Value |
|---|---|
| Package | `eu.kanade.tachiyomi.extension.ar.procomic` |
| Source class | `eu.kanade.tachiyomi.extension.ar.procomic.ProComic` |
| Catalog language | Arabic (`ar`) with Arabic and English releases |
| Base domain | `https://procomic.net` |
| Version | `versionCode=2`, `versionName=1.1` |
| Final branch | `fix/full-remediation` |
| Final PR | [#10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) |
| Android validation | Pending; one external session required |

## Current Architecture

The extension uses normal OkHttp requests through Mihon’s `HttpSource` API. Public JSON endpoints are used where the site exposes a verified contract; RSC parsing remains limited to the site responses that require it. No WebView, login, authentication, or browser automation is part of the extension implementation.

| Feature | Current contract and behavior |
|---|---|
| Search | `GET /api/public/series/search?status=approved&limit=18&page=N&sort=latest&search=...`, with optional type filtering and server-reported pagination. |
| Popular | `GET /api/public/content/popular-new?limit=20`; novel rows are filtered, duplicate series IDs are removed, and no fabricated continuation is reported. |
| Latest | `GET /api/public/content/latest-updates?limit=18&category=all&page=N`; server order is preserved, short non-empty pages continue, and an empty data array terminates pagination. |
| Details | The source’s internal manga URL is converted to the live canonical `/ar/series/{slug}-{id}` RSC route. Complete and restricted response shapes are handled separately. |
| Chapters | `GET /api/chapters?contentId={seriesId}&_u=...`, followed by authoritative `hasMore` pagination, approval filtering, Arabic preference, English fallback, deduplication, and deterministic descending ordering. |
| Reader | The canonical `.pro` chapter route is requested as raw HTTP. `appImages` extraction remains based on the response available to Mihon’s OkHttp client; page-image requests are restricted to the evidence-derived `https://app.procomic.pro/chapters/` host/path contract. |

## Chapter and Gate Rules

Arabic chapters are preferred when an Arabic and English record share the same chapter identity. English is retained only as a fallback when no Arabic record exists. Deduplication and ordering occur before the paid-chapter visibility preference is applied, so hiding paid chapters cannot cause an English mirror to reappear unexpectedly.

The preference `show_paid_chapters` is persistent and defaults to `true` to preserve existing behavior. When disabled, only chapters classified positively as `COIN_LOCKED`, `EXCLUSIVE`, `SHORTLINK_UNLOCK`, or `PERMANENTLY_LOCKED` are hidden. Incomplete or conflicting gate data becomes `UNKNOWN` and remains visible. `RESTRICTED_AUTH_REQUIRED` is a separate access state, is never inferred from denial alone, and remains visible when paid chapters are hidden. No synthetic `isPaid` field is used.

## Validation Status

The final software gate passed all deterministic suites, including diagnostics redaction, Details contracts, chapter normalization, Popular, Latest, gate-state preference, and parser hardening. `git diff --check` passed, and both APK variants build successfully. Android validation is intentionally separate and pending.

A chapter route returning HTTP 200 is not sufficient Reader evidence. The later external session must separately confirm chapter-route success, Reader UI visibility, raw image URL discovery, successful image download with an image content type, visible rendering, and the exact route-to-image relationship for series/chapter `690/50821`, `693/51606`, and one additional chapter.

## Known Limitations

Authentication and full paid access are not implemented. Restricted/auth-required behavior has not been validated with an authenticated account. Public-image availability may remain limited by server-side access rules. The extension does not provide a WebView fallback. Novel-type content is excluded because Mihon is a comic reader. These limitations do not change the software-gate result, but they prevent claiming completed runtime or production validation.

## Build and Test

Use JDK 21 and the repository’s Gradle wrapper. The documented software-gate command is:

```bash
ANDROID_HOME=/home/ubuntu/android-sdk \
ANDROID_SDK_ROOT=/home/ubuntu/android-sdk \
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
```

The APKs are written to `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`. Run all deterministic tests with:

```bash
for test in $(find testdata -type f -name 'test_*.py' | sort); do python3 "$test" || exit 1; done
git diff --check
```

## Documentation and Handoff

[`docs/PROCOMIC_SYSTEM.md`](docs/PROCOMIC_SYSTEM.md) is the current engineering reference. [`docs/VALIDATION.md`](docs/VALIDATION.md) contains the software gate and one-time Android checklist. [`docs/HANDOFF.md`](docs/HANDOFF.md) is the operational handoff for the later external validation session. [`docs/BRANCH_TOPOLOGY.md`](docs/BRANCH_TOPOLOGY.md) records the current branch and commit map. Earlier forensic reports and engineering plans are retained as historical or audit artifacts and are not current implementation instructions.

## Contribution Workflow

Focused implementation changes should remain independently committed and reviewed through pull requests. The current six-commit remediation is reviewed through PR #10 into `main`; it must not be merged automatically during the release-preparation phase. Any issue discovered during the later Android session should become a separately approved remediation task rather than an untracked code change.
