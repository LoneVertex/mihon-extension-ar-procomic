# ProComic System Reference

**Status:** CURRENT

**Authoritative implementation branch:** [`fix/runtime-eof-search-feeds`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/runtime-eof-search-feeds)

**Implementation baseline HEAD:** [`81485ee15f88b292842e03cc548474de044056f1`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/81485ee15f88b292842e03cc548474de044056f1)

**Documentation snapshot parent HEAD:** [`81485ee`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/81485ee15f88b292842e03cc548474de044056f1)

**Focused Reader source commit:** [`81485ee`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/81485ee15f88b292842e03cc548474de044056f1)

**Review path:** [PR #11](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/11) → `fix/full-remediation` → [PR #10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10) → `main`

**Runtime and release status:** The lifecycle-status and protected-Reader fixes pass the 12-suite local gate, clean builds, and corrected audit-remediation CI. The live public contract probe returns valid deferred maps and AVIF tiles. Direct Android-device rendering, authenticated account validation, paid access, merge, tag, and GitHub Release are not claimed or performed here. The release build remains debug-keystore signed for sideload/testing.

## Purpose and Scope

This document is the current engineering reference for the Arabic ProComic Mihon extension. It describes the implementation present at the current branch HEAD. Deep probes, raw captures, and investigation history remain retained audit artifacts; they are evidence, not alternate implementation instructions.

The extension targets `https://procomic.net` and uses Mihon’s `HttpSource`/Rx-compatible API surface. It is packaged as `eu.kanade.tachiyomi.extension.ar.procomic`, with `versionCode=3` and `versionName=1.2`. The Android build uses compileSdk 35, targetSdk 35, min SDK 26, and the official AOMedia `org.aomedia.avif.android:avif:1.3.0.841110fd` decoder for protected AVIF tiles. The compact universal native decoder is the reason the release APK is approximately 2.09 MB rather than pure-Kotlin size.

## System Architecture

The extension uses normal OkHttp requests with a bounded RSC boundary parser for responses that require Next.js React Server Component extraction. Search, Popular, Latest, and Chapters use verified public JSON contracts. Details uses the canonical slug-ID RSC route. Reader page-list extraction uses the raw chapter response available to Mihon’s HTTP client and the site’s own deferred-media/protected-map contracts. No WebView, login, authentication, cookie/session bypass, payment bypass, or browser automation is embedded in the source.

| Layer | Responsibility | Current implementation |
|---|---|---|
| `ProComic.kt` | Mihon source lifecycle, requests, parsing, lifecycle-status mappers, preference filtering, Search batching, Reader page-list assembly, and image requests | `HttpSource` plus `ConfigurableSource` |
| `ProComicDto.kt` | JSON DTOs and gate/media metadata models | Kotlin serialization DTOs with optional server fields |
| `ProComicUtils.kt` | RSC/JSON boundary extraction, Reader manifest extraction, chapter normalization, gate classification, protected-page payload parsing, and page-image parsing | Bounded string-aware scans with sibling `deferredMedia` support |
| `ProComicImageInterceptor.kt` | Protected-page proxy-plan retrieval, tile downloading, decoding, geometry validation, reconstruction, and JPEG response synthesis | OkHttp interceptor with bounded tile/composite resources and platform/AOMedia decoder fallback |
| `ProComicDiag.kt` | Safe runtime diagnostics | Metadata-only logging with redacted URLs and no raw body/header values |
| `testdata/` | Deterministic contract fixtures and regression tests | Python suites; CI installs pinned `Pillow==12.3.0` for the icon contract |

## Website Contract Map

| Feature | Request | Response contract | Termination/selection rule |
|---|---|---|---|
| Search | `GET /api/public/series/search?status=approved&limit=50&page={page}&sort=latest&search={query}` with optional `type` | `ProComicSearchResponse` with `data` and `meta` | A bounded batch consumes at most six pages; short/empty/repeated pages exhaust the batch and Mihon receives `hasNextPage=false` |
| Popular | `GET /api/public/content/popular-new?limit=20` | `ProComicPopularResponse` with nested `content` rows | Novel rows and duplicate identities are removed; no continuation is fabricated |
| Latest | `GET /api/public/content/latest-updates?limit=18&category=all&page={page}` | `ProComicLatestResponse` with flat series rows and chapter summaries | Non-empty data continues, including short pages; an empty data array terminates |
| Details | Canonical `/ar/series/{slug}-{id}?_rsc=det` with RSC headers | Complete `series` object or restricted metadata shape | Expected ID/slug identity is checked; malformed candidates are rejected |
| Chapters | `GET /api/chapters?contentId={seriesId}&_u={encodedMangaUrl}`, then `page=N` | `ProComicChapterListResponse` | Server `hasMore`, empty-page protection, repeated-page protection, and a 50-page ceiling |
| Reader route | Canonical public `.pro` chapter URL | Raw HTML/RSC body containing public images, sibling `deferredMedia`, and protection metadata | The actual OkHttp response is parsed; browser-only hydration is not used |
| Deferred media | Site chapter deferred-media contract for the chapter ID | Direct deferred image URLs and protected-page metadata | Direct deferred images are appended; protected entries become bounded Mihon page placeholders |
| Protected map | `chapter-map-proxy-plan/{chapterId}` with the page capability payload | Map dimensions, order, rectangles, and signed tile URLs | Tile URLs, order, rectangles, tile bytes, tile count, and composite pixels are validated before reconstruction |
| Page image | Direct page URL or protected placeholder URL | Image response | Evidence-derived HTTPS hosts/paths are accepted; protected placeholders are reconstructed as JPEG responses |

## Search

Search requests use `limit=50` and server-side URL encoding. The parser reads a bounded response body, filters out novels, and applies title-like token relevance against the visible title, original title, aliases, and slug. Narrative descriptions are deliberately excluded from relevance matching because they create unrelated false positives.

Search ranking gives the highest score to matches in the visible title, the next score to original-title or alias matches, and the lowest score to slug-only matches. Results are collapsed by a stable title/slug search identity. A bounded in-parse batch consumes up to six server pages so Mihon does not receive an empty continuation after valid results; repeated page fingerprints and short/empty pages terminate the batch, and the returned page reports no unbounded continuation.

## Details and Restricted Content

The source retains the internal manga URL `/ar/series/{type}/{id}/{slug}` for chapter REST identity, but converts it to `/ar/series/{slug}-{id}` for the canonical Details request. The parser first validates a complete canonical `series` object against expected identity. It separately recognizes the restricted-content response shape. Access denial is not treated as a paid chapter signal and does not generate a fabricated payment field.

## Chapter Normalization

Chapter records are first restricted to approved records. The normalization pipeline then computes normalized language, numeric or special chapter identity, gate metadata, and deterministic ordering. For a shared chapter identity, Arabic records win when present; English is selected only when Arabic is absent. Same-language duplicates are resolved by latest publication/creation timestamp and then highest ID. Numeric chapters sort descending before special labels, with deterministic timestamp and ID tie-breakers.

Only after normalization and deduplication does the persistent `show_paid_chapters` preference filter the result. The default is `true`, preserving existing behavior.

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

## Pagination, Body Lifecycle, and Error Handling

Search uses bounded in-parse batching rather than an unbounded cursor. Popular does not fabricate page continuation because its public endpoint exposes no authoritative continuation signal. Latest continues across short non-empty pages and stops only on an empty data array. Chapters follows `hasMore` but stops on an empty or repeated chapter-ID page and enforces a 50-page ceiling.

Non-Reader response bodies are bounded to two million bytes before decoding. Shared response readers use at-most bounded reads, distinguishing empty, malformed, truncated, over-limit, unauthorized, server-error, and valid responses without converting parser failures into successful empty results. RSC candidate extraction is limited to eight key candidates and one million bytes per candidate, with bounded string-aware bracket scans that skip escaped quotes.

Diagnostic logging records request method, redacted URL path, status, content type, lengths, hashes, and safe header names without raw bodies or sensitive header values.

## Reader and Image Access

Reader page-list assembly preserves the normal Mihon `pageListRequest`/`pageListParse` boundary. It extracts public images, reads sibling `deferredMedia` from the live payload, fetches the chapter’s deferred-media response, appends direct deferred images, and emits protected-page placeholders carrying the site-provided capability payload. A legacy nested protection shape remains supported as a compatibility fallback, but sibling `deferredMedia` is the current live contract.

The protected-page interceptor requests `chapter-map-proxy-plan/{chapterId}` at image-request time. It validates map dimensions, order, rectangles, allowed tile URLs, tile count, individual tile bytes, and total composite pixels. It downloads the signed AVIF pieces, draws them into a bounded bitmap, compresses the reconstructed page as JPEG, and returns that image response to Mihon. This is reconstruction of the site’s own public media contract; it does not fabricate pages or bypass authentication, payment, or safe-browsing controls.

The decoder chain is:

1. `BitmapFactory.decodeByteArray`;
2. Android `ImageDecoder` with software allocation on API 28 and later; and
3. the official AOMedia `AvifDecoder` through a direct bounded `ByteBuffer`, after validating AVIF metadata and decoded dimensions. Eight-bit tiles use `ARGB_8888`; deeper tiles use `RGBA_F16`.

The generic hardening adds an eight-million-byte per-tile bound, an eight-million-pixel per-tile bound, a one-million-byte protected-map response bound, a 32-tile bound, and a 40-million-pixel composite bound. The AOMedia call is contained in a failure boundary and emits only redacted stage metadata, so native loading or decode errors cannot make extension discovery fail.

The Gradle packaging block sets `useLegacyPackaging=true`, extracting bundled native libraries at install time across the shipped ABIs. The manifest does not use the removed `extractNativeLibs` attribute. Audited page and tile requests remain restricted to evidence-derived HTTPS hosts and paths; arbitrary image-host allowlists are not used.

## Icon and Build Packaging

The launcher icon is the official ProComic website favicon, sourced from `https://procomic.net/favicon.svg` and rasterized into the required Android density resources. The deterministic icon contract verifies the provenance, launcher reference, resource set, and density hashes.

The module uses compileSdk 35, targetSdk 35, min SDK 26, `versionCode=3`, and `versionName=1.2`. The AVIF native dependency is `org.aomedia.avif.android:avif:1.3.0.841110fd`, and compile-only Jsoup is `org.jsoup:jsoup:1.23.1`; native libraries are packaged with `useLegacyPackaging=true`. The AOMedia artifact ships one 16KB-aligned `libavif_android.so` per supported ABI and has no additional native NEEDED libraries.

## Validation Strategy

The deterministic gate runs every `testdata/test_*.py` suite and `git diff --check`. The current Reader fixture set includes exact series 387 / chapter 19273, covering two protected maps, nine valid YUV444 AVIF tiles, and the AOMedia fallback path. The current suite inventory is 12 suites:

| Suite | Coverage |
|---|---|
| `testdata/diagnostics/test_diag_redaction.py` | Redacted diagnostic metadata |
| `testdata/details/test_details_contract.py` | Complete and restricted Details payloads |
| `testdata/chapters/test_chapter_normalization.py` | Language preference, deduplication, ordering, and chapter identity |
| `testdata/feeds/test_popular_contract.py` | Popular endpoint and no-fabricated-pagination contract |
| `testdata/feeds/test_latest_contract.py` | Latest short-page and empty-page termination |
| `testdata/gates/test_gate_states.py` | Gate classification and paid-preference behavior |
| `testdata/hardening/test_parser_hardening.py` | Bounded parser and response hardening |
| `testdata/runtime/test_eof_body_lifecycle.py` | At-most body reads and EOF/truncation lifecycle |
| `testdata/search/test_search_contract.py` | Search limit, batching, relevance, ranking, filtering, and duplicates |
| `testdata/reader/test_reader_contract.py` | Deferred media, protected map reconstruction, chapter-131 hardening, decoder fallback, native packaging, and trust transition |
| `testdata/icon/test_icon_contract.py` | Official favicon provenance and Android density resources |
| `testdata/status/test_status_mapping.py` | Arabic/English lifecycle mapping, access-field separation, and conflicts |

The CI workflow sets `permissions: contents: read`, installs test-only `Pillow==12.3.0` from `requirements-test.txt`, and uses `actions/checkout@v7`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`, and `actions/upload-artifact@v7`. These changes were applied as focused remediations after the previous workflow emitted Node 20/action deprecation and Gradle cache warnings and after the first suite-enabled run exposed the missing Pillow dependency. Runs [32500561810](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500561810) and [32500566137](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500566137) passed; the preceding missing-dependency failures are recorded in `docs/VALIDATION.md`.

The Android software build uses:

```bash
ANDROID_HOME=/home/ubuntu/android-sdk \
ANDROID_SDK_ROOT=/home/ubuntu/android-sdk \
./gradlew clean :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
```

Reader-remediation CI runs [32573390967](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32573390967) and [32573394359](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32573394359) passed on final audited head `400556d`; these runs provision Android API 36, execute all deterministic suites, and build both APK variants. The preceding Reader-source validation runs [32561773852](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32561773852) and [32561776865](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32561776865) passed on `89a2859`. Earlier audit-remediation runs [32500561810](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500561810) and [32500566137](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32500566137), source-remediation runs [32497667085](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497667085) and [32497669824](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32497669824), and implementation runs [32451903381](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451903381) and [32451899341](https://github.com/LoneVertex/mihon-extension-ar-procomic/actions/runs/32451899341) remain historical evidence. The external manual evidence that informed the final fixes is distinct from a claim of exhaustive device coverage.

## Known Limitations and Safety Boundaries

Authentication and full paid access are outside the implementation scope. `RESTRICTED_AUTH_REQUIRED` remains a visible access state, but the extension does not log in or bypass it. Server-side public-image limits may remain. Novel content is excluded. WebView is not used as a parser or fallback. The Reader reconstructs only pages represented by the site’s own deferred/protected media contracts; it does not invent missing pages.

> Current implementation instructions live in this document and the synchronized README, validation, handoff, and branch-topology documents. Historical probes and superseded plans are retained for provenance only.
