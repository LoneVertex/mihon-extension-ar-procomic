# ProComic Operational Handoff

**Status:** CURRENT

**Repository:** [LoneVertex/mihon-extension-ar-procomic](https://github.com/LoneVertex/mihon-extension-ar-procomic)

**Authoritative implementation branch:** `main` (all four fix branches merged: #10, #11, #12, #13, plus v1.5 HTTP 403 / Cloudflare clearance / coin-locked gating fix, official v1.5.0 release)

**Implementation baseline HEAD:** [`774bee4e810b2554be37c32db391494e574a30d7`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/774bee4e810b2554be37c32db391494e574a30d7)

**Documentation snapshot parent HEAD:** [`774bee4`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/774bee4e810b2554be37c32db391494e574a30d7)

**Focused Reader source commit:** [`5d2c0a5`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/5d2c0a5b6fef13388ddea669771ef9638d395e93)

**Review path:** All four fix branches merged into `main`; direct commits [`6905482`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/690548282b85f60dd87f15e63452e1f78e944da0), [`5d2c0a5`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/5d2c0a5b6fef13388ddea669771ef9638d395e93), [`7c3eb49`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/7c3eb492f156d11f95dcfd4a2d8d85f795908587), [`bc7636c`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/bc7636cb83d8976be94b3b33d68dd570e2e941a4), [`f3b7708`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/f3b7708e4d2de88da0132f8c7ecb436f608c9657), and [`774bee4`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/774bee4e810b2554be37c32db391494e574a30d7) on `main`

**Software status:** PASS. All 13 deterministic suites, protected-path checks, `git diff --check`, clean debug/release builds, and CI workflow run `34671593564` pass. Direct Android-device rendering remains not verified in this sandbox.

**Release status:** v1.5.1 release prepared (following v1.5.0 at [`v1.5.0`](https://github.com/LoneVertex/mihon-extension-ar-procomic/releases/tag/v1.5.0)) with signed release APK asset (`versionCode=7`, `versionName=1.5.1`, v2+v3 RSA 4096, SHA-256 `07e6788f2c80ed25392b17a5bd80cf9946da91c84f3b918d9c612b909857b79c`).

## Roles

**The repository maintainer** owns the extension implementation, deterministic tests, software builds, evidence-backed documentation, and GitHub hygiene. All remediation work is consolidated on `main`.

**The manual validator** reports Android/Mihon behavior using the exact release APK and records reproducible evidence. Reported testing already exposed the Search false-positive issue, the three-page Reader symptom, chapter-131 protected-tile failure, trust-transition/native-loading failure, and `Unknown` publication status; those defects are addressed in the current implementation. Any new extension-side defect becomes a separately approved remediation task.

**The release owner** decides whether to create a version tag and publish a GitHub Release after physical device smoke testing. These are not automatic consequences of a passing software gate.

## Exact Build and APK Identity

```bash
ANDROID_HOME=/home/ubuntu/android-sdk \
ANDROID_SDK_ROOT=/home/ubuntu/android-sdk \
./gradlew clean :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
```

| Item | Value |
|---|---|
| Package | `eu.kanade.tachiyomi.extension.ar.procomic` |
| `versionCode` / `versionName` | `7` / `1.5.1` |
| Compile/target SDK | `35` / `35` |
| Minimum SDK | `26` |
| AVIF dependency | `org.aomedia.avif.android:avif:1.3.0.841110fd` |
| Jsoup compile-only dependency | `org.jsoup:jsoup:1.23.1` |
| Native packaging | `useLegacyPackaging=true` |
| Signed release APK | `~/Downloads/procomic-release-v1.5.1.apk`, ~2.1 MB (signed v2+v3 RSA 4096) |
| Release SHA-256 | `07e6788f2c80ed25392b17a5bd80cf9946da91c84f3b918d9c612b909857b79c` |
| Release local APK | `app/build/outputs/apk/release/app-release-unsigned.apk`, ~2.0 MB |
| Debug local APK | `app/build/outputs/apk/debug/app-debug.apk`, ~2.1 MB |
| Size rationale | Official AOMedia AVIF native library across four ABIs; no ABI split applied without Mihon distribution evidence |
| Reproducibility note | Debug hash was stable across repeated clean builds; release hash varies with signing timestamps |
| Release signing | Signed with project keystore `~/.android/procomic.keystore` (RSA 4096, alias `procomic`, valid to 2051) using v2+v3 signature schemes |

The current workflow explicitly installs Android API 35 before building because the final extension compileSdk is 35. The new APK version is intentionally 7/1.5.1 so Mihon and Android cleanly upgrade any previously installed versions.

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
15. Reader domain failover supports bidirectional delivery across `procomic.net` and `procomic.pro`, with expanded CDN host allowlisting (`app.procomic.net`, `img*.procomic.net`), comics-only category filter on Latest Updates feed, popular fallback covers, and `workflow_dispatch` manual CI trigger.
16. Resolved HTTP 403 "Check website in WebView" loop by delegating User-Agent to Mihon's `defaultUserAgentProvider` (preventing Cloudflare `cf_clearance` cryptographic mismatch between OkHttp and Android WebView), filtering broken direct deferred `cdn*.procomic.(pro|net)` chapter image endpoints (nginx 403), aligning origin-based Referer headers across image and proxy requests, and fixing coin-locked chapter classification (`lockedByCoins: true` with null cost) with explicit Arabic/English paywall error messaging.

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

The 13 suites cover diagnostics, Details, chapters, Popular, Latest, gates/preferences, parser hardening, runtime EOF/body lifecycle, Search, Reader/protected pages, the official icon, lifecycle status mapping, and adversarial boundary checks. Confirm the package/version identity, APK sizes, and APK hashes before any future release decision.

## Manual Android/Mihon Evidence Boundary

The extension’s reported manual testing informed the fixes above. A future release owner may request an additional device matrix, but the repository must not inflate the current evidence into universal Android validation. If performing a manual regression, use the exact release APK and verify installation, source startup, Search relevance/ranking, Popular and Latest semantics, Details, Chapters and gate states, preference persistence, and Reader cases including a chapter with protected pages.

For Reader evidence, distinguish the chapter route, Reader UI visibility, page-list count, raw image URL discovery, actual image request, response status/content type, visible rendering, and exact chapter-to-image relationship. A chapter route returning HTTP 200 alone is not a Reader pass. Live public probing confirms the observed three-public-page/deferred-protected flow and valid AVIF tile responses, but the user’s exact Android rendering remains not verified without a connected device.

## No-Code-Change and Safety Rules

The manual validator must not modify source, tests, fixtures, Gradle files, dependencies, or branch history. No login/authentication mechanism is to be introduced. No new defect discovered during validation is to be fixed in place without an approved remediation task. The extension must not bypass restricted access, payment, safe-browsing, or server-side controls.

## Current Limitations

Authentication and full paid access are not implemented. `RESTRICTED_AUTH_REQUIRED` remains a separate visible state. Server-side public-image rules may limit particular chapters. Novel content is excluded. WebView is not used as a parser or fallback. These limitations are separate from the PASS software gate.

## Current Status

v1.5.1 release resolves the runtime "protected tile could not be decoded" error by introducing `AvifNativeLoader` to locate and load `libavif_android.so` in Mihon's `DelegateLastClassLoaderCompat` runtime (where `librarySearchPath` is passed as null), dynamic tile Referer matching, and `ARGB_8888` decode fallback. All 13 test suites pass. Signed APK generated at `~/Downloads/procomic-release-v1.5.1.apk` (`versionCode=7`, `versionName=1.5.1`, v2+v3, RSA 4096, SHA-256 `07e6788f2c80ed25392b17a5bd80cf9946da91c84f3b918d9c612b909857b79c`).

Status: v1.5.1 signed release APK generated and verified.
