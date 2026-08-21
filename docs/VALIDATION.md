# ProComic Validation and Release Status

**Status:** CURRENT

**Implementation branch:** [`fix/runtime-eof-search-feeds`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/runtime-eof-search-feeds)

**Implementation baseline HEAD:** [`8f88ec9fe839cbbca9076cd0c866f287a7b684dd`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/8f88ec9fe839cbbca9076cd0c866f287a7b684dd)

**Latest audited branch HEAD:** [`1285213d3d162471756109187e61a79627cd5708`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/1285213d3d162471756109187e61a79627cd5708)

**Review path:** [PR #11](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/11) → `fix/full-remediation` → [PR #10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) → `main`

**Software-gate status:** PASS for the current implementation and CI evidence.

**Release status:** No tag or GitHub Release exists. Publishing and merging remain separate approval-gated operations.

## Software Gate

The final deterministic gate passed all 11 suites, `git diff --check`, protected-path checks, and clean debug/release builds. The current implementation branch is 26 commits ahead of unchanged `main`; the commits remain unsquashed and the stacked PRs remain open.

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
| `git diff --check` | PASS | Final software gate |
| Protected-path checks | PASS | Final software gate |
| Debug APK build | PASS | CI and local gate evidence |
| Release APK build | PASS | CI and local gate evidence |
| CI action modernization | PASS | Action-only remediation commit `1285213d`; both current workflow runs passed |

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

## CI Evidence

The successful CI runs for the current audited branch are:

| Run | Purpose | Commit | Result |
|---:|---|---|---|
| [32465464645](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32465464645) | Branch push validation | `1285213d` | PASS |
| [32465468659](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32465468659) | Pull-request validation | `1285213d` | PASS |

Earlier implementation runs [32451903381](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451903381) and [32451899341](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451899341) remain historical evidence. The current CI workflow uses `actions/checkout@v7`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`, and `actions/upload-artifact@v7`; the action-only remediation was validated by both current runs.

## APK Identity

The implementation package is `eu.kanade.tachiyomi.extension.ar.procomic`, with `versionCode=2` and `versionName=1.1`. The build uses compile/target SDK 35, min SDK 26, AVIF Coder 2.1.3, and `useLegacyPackaging=true` for install-time native-library extraction.

| Variant | CI artifact filename | Package | Version | SHA-256 |
|---|---|---|---|---|
| Debug | `ProComic-v1.1-debug-8f88ec9-ci.apk` | `eu.kanade.tachiyomi.extension.ar.procomic` | `versionCode=2`, `versionName=1.1` | `bf2276e7152637ac9020f617eabf78e181f7c606b75a0fe4c0f2b17be9fdb83e` |
| Release | `ProComic-v1.1-release-8f88ec9-ci.apk` | `eu.kanade.tachiyomi.extension.ar.procomic` | `versionCode=2`, `versionName=1.1` | `db0e3e7d33b5d2b252bcc66d300dc55fc1fbf7f8109dd2309b8cbde16789923f` |

The standard local output paths are `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`. The CI artifact copies and checksum file are retained in the external synchronization evidence bundle, not committed into this source repository.

## Regression Coverage Added by the Final Fixes

The current deterministic fixtures cover the final reported failure sequence:

| Reported or discovered issue | Current coverage and implementation result |
|---|---|
| Search returned unrelated titles | Title-like token filtering excludes description-only false positives |
| Search results were insufficient or incorrectly continued | `limit=50`, bounded six-page in-parse aggregation, repeated-page detection, and explicit exhaustion |
| Search ranking was weak | Visible-title matches rank above original-title/alias and slug-only matches |
| Novel/manhua duplicate rows appeared | Stable search identity collapse removes duplicates |
| Reader stopped at three public pages | Sibling `deferredMedia` retrieval and protected-page placeholders extend the page list through the site’s own media contracts |
| Chapter 131 protected tiles failed | `ImageDecoder` fallback, RGBA output, bounded tile decode, and protected-map geometry checks |
| Trusting the extension caused it to disappear | Native AVIF decoder initialization is lazy; native libraries use `useLegacyPackaging=true` |
| Extension icon was incorrect | Official `procomic.net/favicon.svg` is rasterized across the Android density resources |
| Shared response reads could fail at EOF | Bounded at-most body reads distinguish truncated/empty/oversize responses |

Reported manual Android testing informed these fixes. The repository does not claim that every Android version, device, authenticated session, premium chapter, or server-side access state has been exhaustively tested.

## Runtime and Security Boundaries

The extension does not implement login, authentication, cookie/session bypass, payment bypass, or WebView/browser automation. `RESTRICTED_AUTH_REQUIRED` remains a distinct visible state and is never converted into a paid state. Protected Reader pages are reconstructed only from the site’s own deferred-media and proxy-plan responses; missing pages are not fabricated.

The Reader validation evidence must distinguish the chapter route, Mihon Reader UI, image URL discovery, actual image response, content type, visible rendering, and the exact chapter-to-image relationship. A chapter route returning HTTP 200 alone is not sufficient proof of successful reading.

## Current Limitations

Authenticated restricted-content behavior is not provided or validated. Full paid access is outside the implementation scope. Server-side public-image rules can still limit availability for particular chapters. Novel content is excluded because Mihon is a comic reader. No WebView fallback is present. These limitations are separate from the PASS software gate and remain relevant to any future release decision.

## Approval-Gated Follow-ups

The following operations remain intentionally unperformed:

1. Merge PR #11 into `fix/full-remediation`.
2. Merge PR #10 into `main`.
3. Review and decide on Dependabot PRs #1–#9.
4. Create a version tag and GitHub Release, if approved.

No documentation update changes `main`, merges a PR, closes a PR, creates a tag, or publishes a release.
