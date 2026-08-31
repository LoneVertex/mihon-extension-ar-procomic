# ProComic Operational Handoff

**Status:** CURRENT
**Repository:** [LoneVertex/mihon-extension-ar-procomic](https://github.com/LoneVertex/mihon-extension-ar-procomic)
**Authoritative branch:** [`fix/full-remediation`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/full-remediation)
**Final software HEAD before documentation sync:** `e5ef4d0175dab33433767582c7900e20061b1ab3`
**Review PR:** [#10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) into `main`
**Runtime status:** Software validation passed; one external Android/Mihon validation session remains pending.

## Roles

**The repository maintainer** owns the repository remediation, deterministic tests, software builds, evidence-backed documentation, and GitHub hygiene. The maintainer must not merge PR #10 during this phase and must not claim Android runtime validation before the external session passes.

**The external validator** performs the external Android/Mihon validation session when an Android-capable environment is available. The validator must use the exact final release APK and must not change application code, tests, fixtures, dependencies, or Gradle configuration during validation. Any defect found becomes a separately approved remediation task.

**The user or designated manual validator** supplies or authorizes the Android/Mihon environment, records the requested screenshots/logcat/network evidence, and decides whether the runtime evidence is sufficient for release progression.

## Exact Build and APK Identity

```bash
ANDROID_HOME=/path/to/android-sdk \
ANDROID_SDK_ROOT=/path/to/android-sdk \
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
```

| Item | Value |
|---|---|
| Package | `eu.kanade.tachiyomi.extension.ar.procomic` |
| `versionCode` / `versionName` | `2` / `1.1` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |
| Release SHA-256 | `bc8f153a8209598c3d5bed13555c0b703f3ea395ee404e50d08218ccdcfeb8b1` |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Debug SHA-256 | `7f086d56b42377268c670221392c5ebf11230fdccf4355a43c315e0cd868fdf7` |

## Required Validation Workflow

Install the exact release APK and record package/version identity and installation success. Launch Mihon and confirm that ProComic appears and starts. Validate Search with dragon, a known title, a multi-word query, Arabic text, mixed Arabic/Latin text, and a no-result query. Validate Popular and Latest across the pages requested by Mihon, including ordering, deduplication, and termination semantics. Validate Details for series `678`, `690`, `691`, `693`, and `695`, including complete and restricted response behavior.

Validate Chapters for Arabic preference, English fallback, same-identity deduplication, numeric and special-label ordering, gate metadata, and restricted content. In source settings, confirm that `show_paid_chapters` defaults to Show and persists. With Hide enabled, only positively identified paid/locked states may disappear; `UNKNOWN` and `RESTRICTED_AUTH_REQUIRED` must remain visible. Toggle the setting, leave and re-enter the source, and run a full regression after toggling.

Reader validation must cover series/chapter `690/50821`, `693/51606`, and one additional chapter. The normal public flow must open the chapter, display the Reader UI, discover image sources from the raw contract available to Mihon’s HTTP client, download at least one real image successfully, and visibly render that image. The validator must capture the image-producing request, successful HTTP status, valid image content type, and exact chapter-route-to-image relationship. A chapter route returning HTTP 200 alone is not a pass.

Raw HTTP must be compared with browser-rendered DOM. If raw HTTP lacks image sources, document the actual reproducible API/JSON/manifest request and stop; do not add WebView or browser automation to the extension. Capture screenshots or recordings and logcat for source startup, feeds, Details, Chapters, preference filtering, Reader extraction, image requests, and rejected-host diagnostics. Document WebView absence explicitly.

## No-Code-Change Rule

The external validator must not modify source, tests, fixtures, Gradle files, dependencies, or branch history. No login/authentication mechanism is to be introduced. No new issue discovered during validation is to be fixed in place; it must be reported as a separately approved remediation task.

## Current Limitations

Authentication and full paid access are not implemented. Authenticated restricted-content behavior is not validated. Server-side public-image limitations may remain. The extension does not use WebView as a parser or fallback. Android/Mihon runtime validation remains pending until the required external session and evidence are complete.
