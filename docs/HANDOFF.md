# ProComic Operational Handoff

**Status:** CURRENT

**Repository:** [LoneVertex/mihon-extension-ar-procomic](https://github.com/LoneVertex/mihon-extension-ar-procomic)

**Authoritative implementation branch:** [`fix/runtime-eof-search-feeds`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/runtime-eof-search-feeds)

**Implementation baseline HEAD:** [`8f88ec9fe839cbbca9076cd0c866f287a7b684dd`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/8f88ec9fe839cbbca9076cd0c866f287a7b684dd)

**Current branch HEAD:** [`89a2859e261c1e48dbc2ddd36a410d8b90fade76`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/89a2859e261c1e48dbc2ddd36a410d8b90fade76)

**Focused Reader source commit:** [`334888c`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/334888c)

**Review path:** [PR #11](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/11) into `fix/full-remediation`, stacked above [PR #10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) into `main`

**Software status:** PASS. All 12 deterministic suites, protected-path checks, `git diff --check`, clean debug/release builds, and corrected audit-remediation CI builds pass. Direct Android-device rendering remains not verified in this sandbox.

**Release status:** No tag or GitHub Release exists. PR merges and release publication remain approval-gated and were not performed by this documentation synchronization.

## Roles

**The repository maintainer** owns the extension implementation, deterministic tests, software builds, evidence-backed documentation, and GitHub hygiene. The maintainer must preserve the stacked PR structure, must not modify `main`, and must not merge PR #11 or PR #10 without explicit approval.

**The manual validator** reports Android/Mihon behavior using the exact release APK and records reproducible evidence. Reported testing already exposed the Search false-positive issue, the three-page Reader symptom, chapter-131 protected-tile failure, trust-transition/native-loading failure, and `Unknown` publication status; those defects are addressed in the current implementation. Any new extension-side defect becomes a separately approved remediation task.

**The release owner** decides whether to merge the stacked PRs, accept Dependabot updates, create a version tag, and publish a GitHub Release. These are not automatic consequences of a passing software gate.

## Exact Build and APK Identity

```bash
ANDROID_HOME=/home/ubuntu/android-sdk \
ANDROID_SDK_ROOT=/home/ubuntu/android-sdk \
./gradlew clean :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
```

| Item | Value |
|---|---|
| Package | `eu.kanade.tachiyomi.extension.ar.procomic` |
| `versionCode` / `versionName` | `2` / `1.1` |
| Compile/target SDK | `36` / `35` |
| Minimum SDK | `26` |
| AVIF dependency | `io.github.awxkee:avif-coder:2.2.1` |
| Jsoup compile-only dependency | `org.jsoup:jsoup:1.23.1` |
| Native packaging | `useLegacyPackaging=true` |
| Release local APK | `app/build/outputs/apk/release/app-release.apk`, 22,830,491 bytes |
| Release local SHA-256 | `7dc72b668bd958275cd3f3bf47c3b9d59218f6984941ae5b61e6a3f0000d2bb0` |
| Debug local APK | `app/build/outputs/apk/debug/app-debug.apk`, 25,631,098 bytes |
| Debug local SHA-256 | `2427a1a1c516b8fb2b067fbb16a9a4e26d5fc972ad6a201061c199d915cb5d8e` |
| Size rationale | Universal `avif-coder` native libraries across four ABIs; no ABI split applied without Mihon distribution evidence |
| Reproducibility note | Debug hash was stable across repeated clean builds; release hash varied while size/metadata remained identical, so the recorded release hash identifies this exact local artifact only |
| Release signing | Debug keystore for sideload/testing; production publication requires maintainer-owned signing credentials |

The current workflow explicitly installs Android API 36 before building because the stable AVIF decoder declares `minCompileSdk=36`. The first suite-enabled workflow runs failed because the GitHub runner lacked Pillow: [32500306071](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500306071) and [32500309639](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500309639). After adding pinned `Pillow==12.3.0` in `requirements-test.txt`, corrected push/PR runs [32500561810](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500561810) and [32500566137](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500566137) passed. The workflow also sets `permissions: contents: read`. Source-remediation runs [32497667085](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497667085) and [32497669824](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497669824) and earlier implementation runs [32451903381](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451903381) and [32451899341](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451899341) remain historical evidence. The current artifact copies and checksum file are retained in the external synchronization evidence bundle.

## Completed Implementation Fixes

The current branch includes the following completed and tested work:

1. RSC bracket scanning now skips escaped quotes and remains bounded.
2. Shared Search, Popular, and Latest response bodies use the corrected at-most bounded lifecycle, preventing EOF failures.
3. Search uses `limit=50`, bounded six-page in-parse aggregation, repeated-page detection, and explicit exhaustion.
4. Search filters by title-like identity fields rather than narrative descriptions, ranks visible-title matches above original-title/alias and slug-only matches, and collapses duplicate series identities.
5. The official ProComic favicon is rasterized into all required Android density resources.
6. Reader parsing extracts live sibling `deferredMedia` with a nested legacy fallback.
7. Reader assembly combines public images, direct deferred images, and protected-page placeholders through the site’s own deferred-media contract.
8. Protected pages request a fresh proxy plan, validate geometry and bounds, download signed tiles, reconstruct a page, and return a JPEG response to Mihon.
9. Chapter-131 tile decoding has an `ImageDecoder` fallback, explicit `PreferredColorConfig.RGBA_8888`, and a per-tile byte bound in addition to tile/composite bounds.
10. AVIF native loading is lazy to avoid extension disappearance during Mihon trust/discovery transitions.
11. Gradle uses `useLegacyPackaging=true` for install-time native-library extraction.
12. Lifecycle status maps top-level `progress` values such as `مستمر` and `مكتمل`; approval/access values are not used as publication status.
13. Protected map responses and tile bodies use explicit byte bounds; the native decoder retries with its default color configuration after an explicit RGBA failure.
14. CI action versions were updated to `actions/checkout@v7`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`, and `actions/upload-artifact@v7`; workflow permissions are limited to `contents: read`; deterministic suites run after installing pinned `Pillow==12.3.0`; corrected post-remediation CI runs passed.

No authentication, login, session/cookie bypass, payment bypass, WebView, browser automation, or fabricated premium page behavior was added.

## Required Validation Workflow

Run the complete deterministic gate:

```bash
python3 -m pip install --disable-pip-version-check --no-input -r requirements-test.txt
for test in $(find testdata -type f -name 'test_*.py' | sort); do
  python3 "$test" || exit 1
done
git diff --check
```

The 12 suites cover diagnostics, Details, chapters, Popular, Latest, gates/preferences, parser hardening, runtime EOF/body lifecycle, Search, Reader/protected pages, the official icon, and lifecycle status mapping. Confirm the package/version identity, APK sizes, and APK hashes before any future release decision.

## Manual Android/Mihon Evidence Boundary

The extension’s reported manual testing informed the fixes above. A future release owner may request an additional device matrix, but the repository must not inflate the current evidence into universal Android validation. If performing a manual regression, use the exact release APK and verify installation, source startup, Search relevance/ranking, Popular and Latest semantics, Details, Chapters and gate states, preference persistence, and Reader cases including a chapter with protected pages.

For Reader evidence, distinguish the chapter route, Reader UI visibility, page-list count, raw image URL discovery, actual image request, response status/content type, visible rendering, and exact chapter-to-image relationship. A chapter route returning HTTP 200 alone is not a Reader pass. Live public probing confirms the observed three-public-page/deferred-protected flow and valid AVIF tile responses, but the user’s exact Android rendering remains not verified without a connected device.

## No-Code-Change and Safety Rules

The manual validator must not modify source, tests, fixtures, Gradle files, dependencies, or branch history. No login/authentication mechanism is to be introduced. No new defect discovered during validation is to be fixed in place without an approved remediation task. The extension must not bypass restricted access, payment, safe-browsing, or server-side controls.

## Current Limitations

Authentication and full paid access are not implemented. `RESTRICTED_AUTH_REQUIRED` remains a separate visible state. Server-side public-image rules may limit particular chapters. Novel content is excluded. WebView is not used as a parser or fallback. These limitations are separate from the PASS software gate.

## Approval-Gated Follow-ups

1. Review PR #11 and, if approved, merge it into `fix/full-remediation`.
2. Review PR #10 and, if approved, merge it into `main`.
3. Review Dependabot PRs #1–#9 for compatibility; none is merged by this handoff.
4. Decide whether to create a version tag and GitHub Release.

The documentation synchronization performs none of these operations and pushes only to `fix/runtime-eof-search-feeds`.
