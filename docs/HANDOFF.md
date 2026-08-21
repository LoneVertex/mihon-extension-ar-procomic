# ProComic Operational Handoff

**Status:** CURRENT

**Repository:** [LoneVertex/mihon-extension-ar-procomic](https://github.com/LoneVertex/mihon-extension-ar-procomic)

**Authoritative implementation branch:** [`fix/runtime-eof-search-feeds`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/runtime-eof-search-feeds)

**Implementation HEAD:** [`8f88ec9fe839cbbca9076cd0c866f287a7b684dd`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/8f88ec9fe839cbbca9076cd0c866f287a7b684dd)

**Review path:** [PR #11](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/11) into `fix/full-remediation`, stacked above [PR #10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) into `main`

**Software status:** PASS. All 11 deterministic suites, protected-path checks, `git diff --check`, and implementation CI builds pass.

**Release status:** No tag or GitHub Release exists. PR merges and release publication remain approval-gated and were not performed by this documentation synchronization.

## Roles

**The repository maintainer** owns the extension implementation, deterministic tests, software builds, evidence-backed documentation, and GitHub hygiene. The maintainer must preserve the stacked PR structure, must not modify `main`, and must not merge PR #11 or PR #10 without explicit approval.

**The manual validator** reports Android/Mihon behavior using the exact release APK and records reproducible evidence. Reported testing already exposed the Search false-positive issue, the three-page Reader symptom, chapter-131 protected-tile failure, and trust-transition/native-loading failure; those defects were addressed in the current implementation. Any new extension-side defect becomes a separately approved remediation task.

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
| Compile/target SDK | `35` / `35` |
| Minimum SDK | `26` |
| AVIF dependency | `com.github.awxkee:avif-coder:2.1.3@aar` |
| Native packaging | `useLegacyPackaging=true` |
| Release CI artifact | `ProComic-v1.1-release-8f88ec9-ci.apk` |
| Release SHA-256 | `db0e3e7d33b5d2b252bcc66d300dc55fc1fbf7f8109dd2309b8cbde16789923f` |
| Debug CI artifact | `ProComic-v1.1-debug-8f88ec9-ci.apk` |
| Debug SHA-256 | `bf2276e7152637ac9020f617eabf78e181f7c606b75a0fe4c0f2b17be9fdb83e` |

Successful implementation CI runs are [32451903381](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451903381) and [32451899341](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451899341). The current artifact copies and checksum file are retained in the external synchronization evidence bundle.

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

No authentication, login, session/cookie bypass, payment bypass, WebView, browser automation, or fabricated premium page behavior was added.

## Required Validation Workflow

Run the complete deterministic gate:

```bash
for test in $(find testdata -type f -name 'test_*.py' | sort); do
  python3 "$test" || exit 1
done
git diff --check
```

The 11 suites cover diagnostics, Details, chapters, Popular, Latest, gates/preferences, parser hardening, runtime EOF/body lifecycle, Search, Reader/protected pages, and the official icon. Confirm the package/version identity and APK hashes before any future release decision.

## Manual Android/Mihon Evidence Boundary

The extension’s reported manual testing informed the fixes above. A future release owner may request an additional device matrix, but the repository must not inflate the current evidence into universal Android validation. If performing a manual regression, use the exact release APK and verify installation, source startup, Search relevance/ranking, Popular and Latest semantics, Details, Chapters and gate states, preference persistence, and Reader cases including a chapter with protected pages.

For Reader evidence, distinguish the chapter route, Reader UI visibility, page-list count, raw image URL discovery, actual image request, response status/content type, visible rendering, and exact chapter-to-image relationship. A chapter route returning HTTP 200 alone is not a Reader pass. Record the observed three-public-page/deferred-protected flow without treating it as a fixed universal page count.

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
