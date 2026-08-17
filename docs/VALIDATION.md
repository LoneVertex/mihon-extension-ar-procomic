# ProComic Validation and Release Status

**Status:** CURRENT
**Software branch:** [`fix/full-remediation`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/full-remediation)
**Final software HEAD:** [`e5ef4d0175dab33433767582c7900e20061b1ab3`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/e5ef4d0175dab33433767582c7900e20061b1ab3)
**Review PR:** [#10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) → `main`
**Release status:** Software-gate PASS; Android/Mihon runtime validation PENDING.

## Software Gate

The final software gate passed all deterministic suites and `git diff --check`. The gate was run on the six-commit remediation branch before this documentation synchronization commit.

| Gate | Result |
|---|---|
| Diagnostics redaction tests | PASS |
| Details contract tests | PASS |
| Chapter normalization tests | PASS |
| Popular contract tests | PASS |
| Latest contract tests | PASS |
| Gate-state and paid-preference tests | PASS |
| Parser-hardening tests | PASS |
| `git diff --check` | PASS |
| Debug APK build | PASS |
| Release APK build | PASS |
| Final PR CI: `Build & Validate APK` | PASS |

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
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
```

## APK Identity

| Variant | Path | Package | Version | SHA-256 |
|---|---|---|---|---|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | `eu.kanade.tachiyomi.extension.ar.procomic` | `versionCode=2`, `versionName=1.1` | `7f086d56b42377268c670221392c5ebf11230fdccf4355a43c315e0cd868fdf7` |
| Release | `app/build/outputs/apk/release/app-release.apk` | `eu.kanade.tachiyomi.extension.ar.procomic` | `versionCode=2`, `versionName=1.1` | `bc8f153a8209598c3d5bed13555c0b703f3ea395ee404e50d08218ccdcfeb8b1` |

## Runtime Status

Android/Mihon validation is **PENDING**, not PASS. No Android device, emulator, AVD, or `adb` runtime validation was performed during the six remediation commits or repository cleanup. The next action after this cleanup is exactly one external validation session against the final branch and release APK.

## Required One-Time Android/Mihon Session

Install the exact release APK and record installation success, package name, version code/name, and APK hash. Launch Mihon and record ProComic source startup. Validate Search using dragon, a known title, a multi-word query, an Arabic query, a mixed Arabic/Latin query, and a no-result query. Validate Popular page behavior and semantics, Latest page ordering and termination, Details for series `678`, `690`, `691`, `693`, and `695`, and Chapters for AR/EN selection, fallback, deduplication, ordering, special labels, and gate states.

Open source settings and verify the persistent paid-chapter preference. Show is the default. With Show enabled, all normalized chapters remain. With Hide enabled, only positively identified coin-locked, exclusive, shortlink-unlock, and permanently locked chapters may be hidden. `UNKNOWN` and `RESTRICTED_AUTH_REQUIRED` must remain visible. Toggle the setting, leave and re-enter the source, and perform a full regression after toggling.

Reader validation must cover series/chapter `690/50821`, `693/51606`, and one additional chapter. For each case, record the chapter route, Reader UI, raw image URL discovery, actual image request, image response status and content type, visible image rendering, and exact route-to-image relationship. A chapter route returning HTTP 200 alone is not sufficient. Compare raw HTTP response content with browser-rendered DOM and do not introduce WebView or browser automation into the extension.

Capture installation evidence, source-launch evidence, screenshots or recordings for chapter opening and actual image rendering, request evidence for at least one image per required chapter, and logcat covering startup, feeds, Details, Chapters, gate filtering, Reader extraction, image requests, and rejected-host diagnostics.

## Current Blockers and Limitations

Authenticated restricted-content behavior is not validated. Full paid access is not implemented. Server-side public-image limits may remain. The extension has no authentication mechanism and no WebView fallback. Final runtime status must remain pending until the external evidence requirements are satisfied.
