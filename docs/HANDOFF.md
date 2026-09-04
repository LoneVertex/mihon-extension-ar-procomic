# ProComic Operational Handoff

**Status:** CURRENT

**Repository:** [LoneVertex/mihon-extension-ar-procomic](https://github.com/LoneVertex/mihon-extension-ar-procomic)

**Authoritative implementation branch:** `main` (all four fix branches merged)

**Implementation baseline HEAD:** [`81485ee15f88b292842e03cc548474de044056f1`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/81485ee15f88b292842e03cc548474de044056f1)

**Documentation snapshot parent HEAD:** [`81485ee`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/81485ee15f88b292842e03cc548474de044056f1)

**Focused Reader source commit:** [`81485ee`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/81485ee15f88b292842e03cc548474de044056f1)

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
| `versionCode` / `versionName` | `3` / `1.2` |
| Compile/target SDK | `35` / `35` |
| Minimum SDK | `26` |
| AVIF dependency | `org.aomedia.avif.android:avif:1.3.0.841110fd` |
| Jsoup compile-only dependency | `org.jsoup:jsoup:1.23.1` |
| Native packaging | `useLegacyPackaging=true` |
| Release local APK | `app/build/outputs/apk/release/app-release.apk`, ~2.1 MB (signed) |
| Release local SHA-256 | `3b686227464774ff29cbf56234566d4e8e5c218c698d06c1154b9ee5691d3b63` |
| Debug local APK | `app/build/outputs/apk/debug/app-debug.apk`, ~2.1 MB |
| Debug local SHA-256 | `1aa6f094686301c9ce19c9e53b26dabd89d63d5a78cbcc153677d4d58f8d7121` |
| Size rationale | Official AOMedia AVIF native library across four ABIs; no ABI split applied without Mihon distribution evidence |
| Reproducibility note | Debug hash was stable across repeated clean builds; release hash varied while size/metadata remained identical, so the recorded release hash identifies this exact local artifact only |
| Release signing | Debug keystore for sideload/testing; production publication requires maintainer-owned signing credentials |

The current workflow explicitly installs Android API 35 before building because the final extension compileSdk is 35. The first suite-enabled workflow runs failed because the GitHub runner lacked Pillow:  and . After adding pinned `Pillow==12.3.0` in `requirements-test.txt`, corrected push/PR runs [32500561810](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500561810) and [32500566137](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500566137) passed. The workflow also sets `permissions: contents: read`. Source-remediation runs [32497667085](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497667085) and [32497669824](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497669824) and earlier implementation runs [32451903381](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451903381) and [32451899341](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451899341) remain historical evidence. The current artifact copies and checksum file are retained in the external synchronization evidence bundle. The new APK version is intentionally 3/1.2 so Mihon and Android cannot retain the previously installed failing version.

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
9. Chapter-131 and generic protected-tile decoding use platform fallbacks plus the official AOMedia AVIF decoder through a direct bounded buffer; 8-bit tiles use ARGB_8888 and deeper tiles use RGBA_F16.
10. AOMedia native loading and decoding failures are contained and emitted only as redacted stage metadata, so extension discovery remains nonfatal.
11. Gradle uses `useLegacyPackaging=true` for install-time native-library extraction.
12. Lifecycle status maps top-level `progress` values such as `مستمر` and `مكتمل`; approval/access values are not used as publication status.
13. Protected map responses and tile bodies use explicit byte bounds; the AOMedia decoder validates tile metadata and dimensions before allocating a bounded bitmap.
14. CI action versions were updated to `actions/checkout@v7`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`, and `actions/upload-artifact@v7`; workflow permissions are limited to `contents: read`; deterministic suites run after installing pinned `Pillow==12.3.0`; corrected post-remediation CI runs passed.

No authentication, login, session/cookie bypass, payment bypass, WebView, browser automation, or fabricated premium page behavior was added.

## Global Black-Line Finding

The reported thin black line is not present in the exact chapter-10 page bytes or protected composite geometry. The live contract contains three public manifest images, two direct deferred images, and one protected map with complete rectangle coverage; all protected tiles are valid HTTP 200 AVIF responses. Mihon’s Reader displays these as separate Page objects while WebView presents them continuously. This matches [Mihon issue #696](https://github.com/mihonapp/mihon/issues/696), which documents an intermittent black stripe between Long Strip pages. Treat this as a Mihon viewer inter-page gap, not a missing ProComic page. The extension must not collapse all logical pages into one image because that would break page navigation, progress, memory bounds, and source semantics.

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

## Status as of 2026-09-05

All four fix branches merged into `main`. PR #13 (`fix/site-contract-sync`) was the final merge — live site audit confirmed: CDN deferred image pages fixed, legacy thumbnail hosts fixed, hide-paid-chapters preference lazy init fixed. CI ✅ all 13 test suites pass. Signed APK at `~/Downloads/procomic-release-v1.3-final.apk` (versionCode=4, versionName=1.3, v2+v3, RSA 4096).

Pending: physical Android device smoke test; GitHub Release tag.

