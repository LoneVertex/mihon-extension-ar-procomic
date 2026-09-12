# ProComic Validation and Release Status

**Status:** CURRENT

**Implementation branch:** `main` (all four fix branches merged: #10, #11, #12, #13)

**Implementation baseline HEAD:** [`5d2c0a5b6fef13388ddea669771ef9638d395e93`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/5d2c0a5b6fef13388ddea669771ef9638d395e93)

**Documentation snapshot parent HEAD:** [`5d2c0a5`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/5d2c0a5b6fef13388ddea669771ef9638d395e93)

**Focused Reader source commit:** [`5d2c0a5`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/5d2c0a5b6fef13388ddea669771ef9638d395e93)

**Review path:** All four fix branches (#10, #11, #12, #13) merged into `main`; direct commits [`6905482`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/690548282b85f60dd87f15e63452e1f78e944da0) and [`5d2c0a5`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/5d2c0a5b6fef13388ddea669771ef9638d395e93) on `main`

**Software-gate status:** PASS for the current implementation and CI evidence.

**Release status:** Signed release APK generated at `~/Downloads/procomic-release-v1.5.apk` (`versionCode=6`, `versionName=1.5`, v2+v3 RSA 4096, SHA-256 `5fe6feb1bc0f3094d7847028e96c324b8b483e44d7872750cddbef594e9174ae`). Physical device smoke testing pending before tag and GitHub Release creation.

## Software Gate

The audit-remediation gate passed all 13 suites, `git diff --check`, protected-path checks, full lint, and clean debug/release builds. All fix branches (#10, #11, #12, #13) and direct commits `6905482` and `5d2c0a5` are integrated into `main`.

| Gate | Result | Evidence |
|---|---|---|
| Diagnostics redaction | PASS | `testdata/diagnostics/test_diag_redaction.py` |
| Details contracts | PASS | `testdata/details/test_details_contract.py` |
| Chapter normalization | PASS | `testdata/chapters/test_chapter_normalization.py` |
| Popular contract | PASS | `testdata/feeds/test_popular_contract.py` |
| Latest contract | PASS | `testdata/feeds/test_latest_contract.py` |
| Gate states and paid preference | PASS | `testdata/gates/test_gate_states.py` |
| Parser hardening | PASS | `testdata/hardening/test_parser_hardening.py` |
| Runtime EOF/body lifecycle | PASS | `testdata/runtime/test_eof_body_lifecycle.py` |
| Search contract | PASS | `testdata/search/test_search_contract.py` |
| Reader contract | PASS | `testdata/reader/test_reader_contract.py` |
| Official icon contract | PASS | `testdata/icon/test_icon_contract.py` |
| Lifecycle status mapping | PASS | `testdata/status/test_status_mapping.py` |
| Adversarial chaos boundaries | PASS | `testdata/adversarial/test_chaos_boundaries.py` |
| `git diff --check` | PASS | Final software gate |
| Protected-path checks | PASS | Final software gate |
| Debug APK build | PASS | CI and local gate evidence |
| Release APK build | PASS | CI and local gate evidence; final local artifact is intentionally unsigned |
| Protected Reader fallback and bounded response reads | PASS | Source-remediation commit `affbcf3`; Reader regression and local/CI builds passed |
| CI contract-suite coverage and permissions | PASS | Audit commit `0bda7ea`; corrected by pinned Pillow follow-up `f3f4290` |
| CI action modernization | PASS | Earlier action-only remediation commit `1285213d`; both current workflow runs passed |

The deterministic test command is:

```bash
for test in $(find testdata -type f -name 'test_*.py' | sort); do
  python3 "$test" || exit 1
done
git diff --check
./gradlew :app:lint --no-daemon --stacktrace
```

The build command is:

```bash
ANDROID_HOME=/home/ubuntu/android-sdk \
ANDROID_SDK_ROOT=/home/ubuntu/android-sdk \
./gradlew clean :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
```

CI installs the test-only dependency from `requirements-test.txt` (`Pillow==12.3.0`) before running the deterministic suites. This dependency is not bundled into the Android APK.

## CI Evidence

The current audit-remediation CI history is:

| Run | Purpose | Commit | Result |
|---:|---|---|---|
| [34670639461](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/34670639461) | Push validation on main (v1.5, HTTP 403 / WebView fix, 13 suites) | `5d2c0a5` | ✅ PASS |
| [34592320462](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/34592320462) | Push validation on main (v1.4, dual delivery failover, 13 suites) | `6905482` | ✅ PASS |
| [33924579769](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/33924579769) | Post-merge on main | `dfef381` | ✅ PASS |
| [33924198674](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/33924198674) | PR #13 push run | `dfef381` | ✅ PASS |
| [32573390967](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32573390967) | Final master-audit branch push validation | `400556d` | PASS |
| [32573394359](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32573394359) | Final master-audit pull-request validation | `400556d` | PASS |
| [32561773852](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32561773852) | Reader-remediation branch push validation | `89a2859` | PASS |
| [32561776865](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32561776865) | Reader-remediation pull-request validation | `89a2859` | PASS |
| [32500561810](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500561810) | Corrected branch push validation | `f3f4290` | PASS |
| [32500566137](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500566137) | Corrected pull-request validation | `f3f4290` | PASS |
| [32497667085](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497667085) | Source-remediation branch push | `affbcf3` | PASS |
| [32497669824](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497669824) | Source-remediation pull request | `affbcf3` | PASS |

The CI runs execute the pinned Pillow install, all 13 contract test suites, `git diff --check`, and debug/release APK builds. The workflow uses `permissions: contents: read`, `actions/checkout@v7`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`, and `actions/upload-artifact@v7`. Earlier implementation runs [32451903381](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451903381) and [32451899341](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451899341) remain historical evidence.

## APK Identity

The implementation package is `eu.kanade.tachiyomi.extension.ar.procomic`, with `versionCode=6` and `versionName=1.5`. The build uses compileSdk 35, targetSdk 35, min SDK 26, official AOMedia `org.aomedia.avif.android:avif:1.3.0.841110fd`, compile-only `org.jsoup:jsoup:1.23.1`, and `useLegacyPackaging=true` for install-time native-library extraction. CI explicitly provisions Android API 35. The compact universal native decoder payload is the primary reason the APK remains larger than a pure-Kotlin extension.

| Variant | Current local APK | Package | Version | Size | SHA-256 |
|---|---|---|---|---|---|
| Release (signed) | `~/Downloads/procomic-release-v1.5.apk` | `eu.kanade.tachiyomi.extension.ar.procomic` | `versionCode=6`, `versionName=1.5` | 2.1 MB | `5fe6feb1bc0f3094d7847028e96c324b8b483e44d7872750cddbef594e9174ae` (Signed v2+v3, RSA 4096, alias `procomic`) |

The standard local output paths are `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release-unsigned.apk`. The CI artifact copies and checksum file are retained in the external synchronization evidence bundle, not committed into this source repository.

## Regression Coverage Added by the Final Fixes

The current deterministic fixtures cover the final reported failure sequence:

| Reported or discovered issue | Current coverage and implementation result |
|---|---|
| HTTP 403 / "Check website in WebView" loop when reading chapters | Removed hardcoded static Pixel 7 `User-Agent` so Mihon's `UserAgentInterceptor` matches device WebView profile, enabling Cloudflare `cf_clearance` tokens to validate successfully |
| Unroutable `cdn*.procomic.(pro|net)` chapter images returning HTTP 403 | Filtered out direct `cdn` URLs from `directDeferred` pages in `pageListParse`; all real chapter reader pages are delivered via `app.procomic.pro` and reconstructed protected maps via `img*.procomic.pro/i/...` |
| Coin-locked chapters displayed despite hiding paid chapters | Updated `classifyGateState` to classify `lockedByCoins == true` as `COIN_LOCKED` even when `coinsRequired` is null in the live API; added paywall detection for `"ChapterLocked"` and `"options":{"coins"` in `extractPageImages` |
| Image and tile Referer mismatch | Dynamically set `Referer` to `https://procomic.net/` or `https://procomic.pro/` matching image host, and added explicit Referer to `tileRequest` |
| Split reader domain desync (`procomic.pro` vs `procomic.net`) | Bidirectional domain fallback in `pageListParse` and `fetchDeferredMedia` automatically attempts the alternate domain whenever a response is redirected away or missing `appImages`; allowlists expanded to accept `app.procomic.net` and `img*.procomic.net` |
| Latest updates flooded with light novels | `latestUpdatesRequest` queries `category=comics` rather than `category=all`, returning 100% valid comic entries per page |
| Popular content missing cover images | `ProComicPopularContent` DTO includes `coverImage` field, prioritized during SManga thumbnail resolution |
| Forked workflow trigger missing | `.github/workflows/ci.yml` includes `workflow_dispatch` trigger |
| Mihon showed `Unknown` for ordinary series | Top-level `progress` is mapped conservatively; `approved` and `public/exclusive` access fields are no longer mistaken for lifecycle status; `testdata/status/test_status_mapping.py` covers Arabic/English states and conflicts |
| Search returned unrelated titles | Title-like token filtering excludes description-only false positives |
| Search results were insufficient or incorrectly continued | `limit=50`, bounded six-page in-parse aggregation, repeated-page detection, and explicit exhaustion |
| Search ranking was weak | Visible-title matches rank above original-title/alias and slug-only matches |
| Novel/manhua duplicate rows appeared | Stable search identity collapse removes duplicates |
| Reader stopped at three public pages | Sibling `deferredMedia` retrieval and protected-page placeholders extend the page list through the site’s own media contracts |
| Chapter 131 protected tiles failed | `ImageDecoder` fallback, bounded map/tile reads, protected-map geometry checks, and the official AOMedia AVIF fallback |
| Exact series 387 / chapter 19273 protected tiles failed on Android 16 arm64 | Official AOMedia 1.3.0.841110fd replaces the awxkee decoder path; the generic fallback validates AVIF metadata, supports YUV444 tiles, selects a safe bitmap configuration, bounds tile pixels, and emits redacted decode-stage diagnostics; exact fixture proves 2 maps and 9 valid AVIF tiles |
| Jsoup security advisory affecting the previous compile-only version | `org.jsoup:jsoup` is pinned to 1.23.1, the advisory’s fixed version; it is compile-only and is not bundled into the APK |
| Trusting the extension caused it to disappear | Native AVIF decoder initialization is lazy; native libraries use `useLegacyPackaging=true` |
| Extension icon was incorrect | Official `procomic.net/favicon.svg` is rasterized across the Android density resources |
| Shared response reads could fail at EOF | Bounded at-most body reads distinguish truncated/empty/oversize responses |

Reported manual Android testing informed these fixes. Live public probing confirmed the exact series-387/chapter-19273 deferred-media/proxy-plan route returns two protected maps and nine valid AVIF tiles with YUV444 characteristics, while the sandbox has no connected Android device or emulator. The software remediation and APK build are verified, but direct Mihon rendering on the user’s Android 16 arm64 device remains **NOT VERIFIED** until version 1.5 is installed and tested. The repository does not claim that every Android version, device, authenticated session, premium chapter, or server-side access state has been exhaustively tested.

## Runtime and Security Boundaries

The extension does not implement login, authentication, cookie/session bypass, payment bypass, or WebView/browser automation. `RESTRICTED_AUTH_REQUIRED` remains a distinct visible state and is never converted into a paid state. Protected Reader pages are reconstructed only from the site’s own deferred-media and proxy-plan responses; missing pages are not fabricated.

The global black-line report was audited against the exact screenshot context: series 387, Arabic chapter 19269. The chapter exposes three public manifest images, two direct deferred images, and one protected map. The protected map is `[1000,7659]` with four rectangles that exactly cover the full frame; all four tiles are HTTP 200 `image/avif` with valid AVIF/ISO-BMFF signatures. In-memory boundary measurements found no full-width black strip at the public image edges. Mihon’s official reader documentation identifies a configurable Background color, and [Mihon issue #696](https://github.com/mihonapp/mihon/issues/696) documents the same black stripe between Long Strip pages. This evidence classifies the reported line as a Mihon viewer inter-page gap rather than an extension-generated missing page.

The Reader validation evidence must distinguish the chapter route, Mihon Reader UI, image URL discovery, actual image response, content type, visible rendering, and the exact chapter-to-image relationship. A chapter route returning HTTP 200 alone is not sufficient proof of successful reading.

## Current Limitations

Authenticated restricted-content behavior is not provided or validated. Full paid access is outside the implementation scope. Server-side public-image rules can still limit availability for particular chapters. Novel content is excluded because Mihon is a comic reader. No WebView fallback is present. The audit-remediation software gate is PASS; the global Mihon viewer-gap classification is VERIFIED at the contract/image-boundary level, while exact Android-device rendering remains PARTIAL/NOT VERIFIED until physical-device confirmation, and authenticated/premium behavior remains outside scope. The universal native decoder footprint is measured and explained, but no ABI split was applied without Mihon distribution evidence. The release APK is signed with the project keystore (RSA 4096, alias `procomic`, valid to 2051) using v2+v3 signature schemes. Signed APK: `~/Downloads/procomic-release-v1.5.apk`.

## Milestone Progression — Current State

All four fix branches have been merged into `main` and CI is green:

| PR | Branch | Status |
|---|---|---|
| [#10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) | `fix/full-remediation` | ✅ Merged |
| [#11](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/11) | `fix/runtime-eof-search-feeds` | ✅ Merged |
| [#12](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/12) | `fix/adversarial-hardening` | ✅ Merged |
| [#13](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/13) | `fix/site-contract-sync` | ✅ Merged — live audit: CDN deferred image allowlist, legacy thumbnail hosts, preference lazy init, hardening applied |
| Direct commit | `main` (`6905482`) | ✅ Pushed — dual-domain reader failover (`.pro`/`.net`), comics-only latest updates feed, popular cover images, workflow_dispatch |
| Direct commit | `main` (`5d2c0a5`) | ✅ Pushed — v1.5: HTTP 403 / WebView loop fix, delegate User-Agent, filter broken CDN chapter URLs, coin-locked chapter gating |

Dependabot PRs #1–#9 closed; `open-pull-requests-limit: 0` committed. Only `main` branch remains. Keystore generated at `~/.android/procomic.keystore` (RSA 4096, alias `procomic`, valid to 2051). Signed APK at `~/Downloads/procomic-release-v1.5.apk`.

Pending: physical Android device smoke test; version tag and GitHub Release.
