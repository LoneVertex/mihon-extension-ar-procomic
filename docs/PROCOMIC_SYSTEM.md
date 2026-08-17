# ProComic System Reference

**Status:** CURRENT
**Authoritative branch:** [`fix/full-remediation`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/full-remediation)
**HEAD:** [`e5ef4d0175dab33433767582c7900e20061b1ab3`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/e5ef4d0175dab33433767582c7900e20061b1ab3)
**Review PR:** [#10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) into `main`
**Runtime status:** Software validation passed; one external Android/Mihon session remains pending.

## Purpose and Scope

This document is the concise current engineering reference for the Arabic ProComic Mihon extension. It describes the implementation that exists on the authoritative final branch. Deep probes, raw captures, and investigation history remain in the retained audit artifacts and historical documents; they are evidence, not alternate current instructions.

The extension targets `https://procomic.net` and uses Mihon’s legacy `HttpSource`/Rx-compatible API surface. It is packaged as `eu.kanade.tachiyomi.extension.ar.procomic`, with `versionCode=2` and `versionName=1.1`.

## System Architecture

The extension uses normal OkHttp requests with a small RSC boundary parser for responses that require Next.js React Server Component extraction. Search, Popular, Latest, and Chapters use verified public JSON contracts. Details uses the canonical slug-ID RSC route. Reader page-list extraction uses the raw chapter response available to OkHttp; no WebView, login, authentication, or browser automation is embedded in the source.

| Layer | Responsibility | Current implementation |
|---|---|---|
| `ProComic.kt` | Mihon source lifecycle, requests, parsing, mappers, preference filtering, and image requests | `HttpSource` plus `ConfigurableSource` |
| `ProComicDto.kt` | JSON DTOs and gate metadata model | Kotlin serialization DTOs with optional server fields |
| `ProComicUtils.kt` | RSC boundary extraction, Details fallback extraction, chapter normalization, gate classification, and page-image parsing | String-aware bounded JSON boundary scans |
| `ProComicDiag.kt` | Safe runtime diagnostics | Metadata-only logging with redacted URLs and no raw body/header values |
| `testdata/` | Deterministic contract fixtures and tests | Dependency-free Python suites |

## Website Contract Map

| Feature | Request | Response contract | Termination/selection rule |
|---|---|---|---|
| Search | `GET /api/public/series/search?status=approved&limit=18&page={page}&sort=latest&search={query}` with optional `type` | `ProComicSearchResponse` with `data` and `meta` | `meta.page < meta.pages`; novel rows are excluded |
| Popular | `GET /api/public/content/popular-new?limit=20` | `ProComicPopularResponse` with nested `content` rows | No continuation signal is exposed; `hasNextPage=false` |
| Latest | `GET /api/public/content/latest-updates?limit=18&category=all&page={page}` | `ProComicLatestResponse` with flat series rows and chapter summaries | Non-empty data continues; empty data terminates; server order is preserved |
| Details | Canonical `/ar/series/{slug}-{id}?_rsc=det` with RSC headers | Complete `series` object or restricted metadata shape | Expected ID/slug identity is checked; malformed candidates are rejected |
| Chapters | `GET /api/chapters?contentId={seriesId}&_u={encodedMangaUrl}`, then `page=N` | `ProComicChapterListResponse` | Server `hasMore`, empty-page protection, repeated-page protection, and a 50-page ceiling |
| Reader route | Canonical public `.pro` chapter URL | Raw HTML/RSC body with `appImages` manifest | `pageListParse` extracts the actual OkHttp response; browser-only hydration is not used |
| Page image | URL emitted by page extraction | Image response | The image request accepts only verified HTTPS `app.procomic.pro/chapters/` URLs with known image extensions |

## Details and Restricted Content

The source retains the internal manga URL `/ar/series/{type}/{id}/{slug}` for chapter REST identity, but converts it to `/ar/series/{slug}-{id}` for the canonical Details request. The parser first validates a complete canonical `series` object against expected identity. It separately recognizes the restricted-content response shape. Access denial is not treated as a paid chapter signal and does not generate a fabricated payment field.

## Chapter Normalization

Chapter records are first restricted to approved records. The normalization pipeline then computes normalized language, numeric or special chapter identity, gate metadata, and deterministic ordering. For a shared chapter identity, Arabic records win when present; English is selected only when Arabic is absent. Same-language duplicates are resolved by latest publication/creation timestamp and then highest ID. Numeric chapters sort descending before special labels, with deterministic timestamp and ID tie-breakers.

Only after this normalization and deduplication does the persistent `show_paid_chapters` preference filter the result. The default is `true`, preserving existing behavior.

## Gate and Preference Model

The classifier uses verified fields only: `lockedByCoins`, `lockedByExclusive`, `lockedForever`, `hasShortlink`, `coinsRequired`, `coinsUnlocks`, and `shortlinkUnlocks`. It produces the following conservative states:

| State | Meaning | Hidden when preference is Hide? |
|---|---|---|
| `FREE` | All lock flags are explicitly false and no cost signal exists | No |
| `COIN_LOCKED` | Coin lock is asserted with a positive required coin amount | Yes |
| `EXCLUSIVE` | Exclusive lock is asserted | Yes |
| `SHORTLINK_UNLOCK` | Shortlink unlock is asserted without a stronger lock | Yes |
| `PERMANENTLY_LOCKED` | Permanent lock is asserted | Yes |
| `RESTRICTED_AUTH_REQUIRED` | Separate content-access restriction state | No; never a paid state |
| `UNKNOWN` | Missing, conflicting, incomplete, or unrecognized gate data | No |

The preference is exposed through `ConfigurableSource.setupPreferenceScreen` as `show_paid_chapters`, stored in the source-scoped Android `SharedPreferences` namespace `source_${id}`, and defaults to Show. There is no `isPaid` boolean.

## Pagination and Error Handling

Search uses the server’s explicit page metadata. Popular does not fabricate page continuation because its public endpoint exposes no authoritative continuation signal. Latest continues across short non-empty pages and stops only on an empty data array. Chapters follows `hasMore` but stops on an empty or repeated chapter-ID page and enforces a 50-page ceiling.

Non-Reader response bodies are bounded to two million bytes before decoding. RSC candidate extraction is limited to eight key candidates and one million bytes per candidate, with bounded string-aware bracket scans. Diagnostic logging records request method, redacted URL path, status, content type, lengths, hashes, and safe header names without raw bodies or sensitive header values.

## Reader and Image Access

The Reader implementation remains raw-response based. `pageListRequest`, `pageListParse`, and `extractPageImages` are preserved. The actual image request rejects unrecognized schemes, hosts, paths, extensions, query strings, and fragments. Audited successful public downloads used `https://app.procomic.pro/chapters/...` and returned `200` with `image/avif`. Other observed CDN references, including `cdn2.procomic.pro`, are not accepted as current image requests when they lack successful public-image evidence.

Runtime validation must distinguish route loading, Reader UI visibility, image URL discovery, actual image response success, and visible rendering. A chapter route returning HTTP 200 alone is not a Reader pass.

## Validation Strategy

The deterministic gate runs every `testdata/test_*.py` suite and `git diff --check`. Current suites cover diagnostics redaction, Details contracts, chapter normalization, Popular, Latest, gate states and preference filtering, and parser hardening. The Android software build uses:

```bash
ANDROID_HOME=/home/ubuntu/android-sdk \
ANDROID_SDK_ROOT=/home/ubuntu/android-sdk \
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
```

The later one-time Android/Mihon session must use the final release APK and cover source startup, Search, Popular, Latest, Details for series `678`, `690`, `691`, `693`, and `695`, Chapters and gate behavior, Show/Hide preference persistence, Reader cases `690/50821`, `693/51606`, one additional chapter, and a full regression after toggling the preference.

## Known Limitations

Authentication and full paid access are outside the implementation scope. Restricted/auth-required content has not been validated with an authenticated account. Server-side public-image limitations may remain. Novel content is excluded. WebView is not used as a parser or fallback. Final runtime validation remains pending and must not be represented as passed until the external session produces the required evidence.
