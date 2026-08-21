# ProComic Validation and Release Status

**Status:** CURRENT

**Implementation branch:** [`fix/runtime-eof-search-feeds`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/runtime-eof-search-feeds)

**Implementation baseline HEAD:** [`8f88ec9fe839cbbca9076cd0c866f287a7b684dd`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/8f88ec9fe839cbbca9076cd0c866f287a7b684dd)

**Latest audit-remediation HEAD:** [`f3f4290d13f1bf204b0278e25a01235a77ba0087`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/f3f4290d13f1bf204b0278e25a01235a77ba0087)

**Review path:** [PR #11](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/11) → `fix/full-remediation` → [PR #10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) → `main`

**Software-gate status:** PASS for the current implementation and CI evidence.

**Release status:** No tag or GitHub Release exists. Publishing and merging remain separate approval-gated operations.

## Software Gate

The audit-remediation gate passed all 12 suites, `git diff --check`, protected-path checks, and clean debug/release builds. The historical implementation stack remains 26 commits ahead of unchanged `main`; the current audit commits are focused follow-ups after that stack, and the stacked PRs remain open.

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
| `git diff --check` | PASS | Final software gate |
| Protected-path checks | PASS | Final software gate |
| Debug APK build | PASS | CI and local gate evidence |
| Release APK build | PASS | CI and local gate evidence |
| Protected Reader fallback and bounded response reads | PASS | Source-remediation commit `affbcf3`; Reader regression and local/CI builds passed |
| CI contract-suite coverage and permissions | PASS | Audit commit `0bda7ea`; corrected by pinned Pillow follow-up `f3f4290` |
| CI action modernization | PASS | Earlier action-only remediation commit `1285213d`; both current workflow runs passed |

The deterministic test command is:

```bash
for test in $(find testdata -type f -name 'test_*.py' | sort); do
  python3 "$test" || exit 1
done
git diff --check
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
| [32500306071](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500306071) | First workflow run after adding suite coverage | `0bda7ea` | FAIL — GitHub runner lacked Pillow for the icon contract |
| [32500309639](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500309639) | Pull-request reproduction of the same failure | `0bda7ea` | FAIL — same missing test dependency |
| [32500561810](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500561810) | Corrected branch push validation | `f3f4290` | PASS |
| [32500566137](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500566137) | Corrected pull-request validation | `f3f4290` | PASS |
| [32497667085](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497667085) | Source-remediation branch push | `affbcf3` | PASS |
| [32497669824](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497669824) | Source-remediation pull request | `affbcf3` | PASS |

The corrected runs execute the pinned Pillow install, all 12 deterministic suites, `git diff --check`, and debug/release APK builds. The workflow uses `permissions: contents: read`, `actions/checkout@v7`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`, and `actions/upload-artifact@v7`. Earlier implementation runs [32451903381](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451903381) and [32451899341](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451899341) remain historical evidence.

## APK Identity

The implementation package is `eu.kanade.tachiyomi.extension.ar.procomic`, with `versionCode=2` and `versionName=1.1`. The build uses compile/target SDK 35, min SDK 26, AVIF Coder 2.1.3, and `useLegacyPackaging=true` for install-time native-library extraction. The universal native decoder payload is the primary reason the APK is much larger than pure-Kotlin extensions.

| Variant | Current local APK | Package | Version | Size | SHA-256 |
|---|---|---|---|---|---|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | `eu.kanade.tachiyomi.extension.ar.procomic` | `versionCode=2`, `versionName=1.1` | 23,278,712 bytes | `22072281eeed34b46c913ee9bff68fb48e282e6cfc665c75e671ef2d6647ed76` |
| Release | `app/build/outputs/apk/release/app-release.apk` | `eu.kanade.tachiyomi.extension.ar.procomic` | `versionCode=2`, `versionName=1.1` | 21,464,829 bytes | `01030b6005785f8fffcd51b661c22cdf67e4dd5a7d481585a0f677486927810e` |

The standard local output paths are `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`. The CI artifact copies and checksum file are retained in the external synchronization evidence bundle, not committed into this source repository. The debug hash was stable across the repeated clean gate; the release hash varied between two clean local builds while size and metadata remained identical, so the latest local hash above is evidence for that exact build only, not a reproducibility certificate.

## Regression Coverage Added by the Final Fixes

The current deterministic fixtures cover the final reported failure sequence:

| Reported or discovered issue | Current coverage and implementation result |
|---|---|
| Mihon showed `Unknown` for ordinary series | Top-level `progress` is mapped conservatively; `approved` and `public/exclusive` access fields are no longer mistaken for lifecycle status; `testdata/status/test_status_mapping.py` covers Arabic/English states and conflicts |
| Search returned unrelated titles | Title-like token filtering excludes description-only false positives |
| Search results were insufficient or incorrectly continued | `limit=50`, bounded six-page in-parse aggregation, repeated-page detection, and explicit exhaustion |
| Search ranking was weak | Visible-title matches rank above original-title/alias and slug-only matches |
| Novel/manhua duplicate rows appeared | Stable search identity collapse removes duplicates |
| Reader stopped at three public pages | Sibling `deferredMedia` retrieval and protected-page placeholders extend the page list through the site’s own media contracts |
| Chapter 131 protected tiles failed | `ImageDecoder` fallback, explicit RGBA output, default-color native fallback, bounded map/tile reads, and protected-map geometry checks |
| Trusting the extension caused it to disappear | Native AVIF decoder initialization is lazy; native libraries use `useLegacyPackaging=true` |
| Extension icon was incorrect | Official `procomic.net/favicon.svg` is rasterized across the Android density resources |
| Shared response reads could fail at EOF | Bounded at-most body reads distinguish truncated/empty/oversize responses |

Reported manual Android testing informed these fixes. Live public probing confirmed the captured deferred-media/proxy-plan route returns seven protected maps and valid AVIF tiles; the sandbox has no connected Android device or emulator, so direct Mihon rendering of the user’s exact device remains not verified. The repository does not claim that every Android version, device, authenticated session, premium chapter, or server-side access state has been exhaustively tested.

## Runtime and Security Boundaries

The extension does not implement login, authentication, cookie/session bypass, payment bypass, or WebView/browser automation. `RESTRICTED_AUTH_REQUIRED` remains a distinct visible state and is never converted into a paid state. Protected Reader pages are reconstructed only from the site’s own deferred-media and proxy-plan responses; missing pages are not fabricated.

The Reader validation evidence must distinguish the chapter route, Mihon Reader UI, image URL discovery, actual image response, content type, visible rendering, and the exact chapter-to-image relationship. A chapter route returning HTTP 200 alone is not sufficient proof of successful reading.

## Current Limitations

Authenticated restricted-content behavior is not provided or validated. Full paid access is outside the implementation scope. Server-side public-image rules can still limit availability for particular chapters. Novel content is excluded because Mihon is a comic reader. No WebView fallback is present. The audit-remediation software gate is PASS; direct Android-device rendering and authenticated/premium behavior remain PARTIAL/NOT VERIFIED limitations relevant to any future release decision. The universal native decoder footprint is measured and explained, but no ABI split was applied without Mihon distribution evidence. The release build also uses the debug keystore for sideload/testing; a production release requires maintainer-owned signing credentials and explicit release authorization.

## Approval-Gated Follow-ups

The following operations remain intentionally unperformed:

1. Merge PR #11 into `fix/full-remediation`.
2. Merge PR #10 into `main`.
3. Review and decide on Dependabot PRs #1–#9.
4. Create a version tag and GitHub Release, if approved.

No documentation update changes `main`, merges a PR, closes a PR, creates a tag, or publishes a release.
