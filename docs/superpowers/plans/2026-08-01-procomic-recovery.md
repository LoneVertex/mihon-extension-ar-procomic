# ProComic Extension Recovery — Revised Roadmap
# Evidence gathered: 2026-08-01T21:48–21:55 UTC+3
# All claims classified per Autonomous Engineering Execution Contract

---

## Evidence Classification System

- VERIFIED FACT: Confirmed by direct measurement, command output, or reproducible live probe
- HIGH-CONFIDENCE INFERENCE: Follows necessarily from verified facts; single logical step
- LOW-CONFIDENCE INFERENCE: Plausible explanation; requires further verification
- HYPOTHESIS: Untested theory; must not drive implementation decisions

---

## Probe Manifest

| Probe | Method | Result |
|-------|--------|--------|
| P01 | curl listing on .pro and .net | .pro 200 154748B; .net 200 154705B |
| P02 | curl detail on .pro and .net | .pro 410 895B; .net 200 116018B |
| P03 | curl reader on .pro and .net | .pro 410; .net 200 58516B |
| P04 | curl page=1,2,5 on .net popular | All return 138585B, ids [691,690,689] |
| P05 | RSC:1 only / NRST only / neither | RSC:1 alone sufficient; NRST optional |
| P06 | Extract "series":{ from detail | Position 6433, unique, extracts id:48 Breakers |
| P07 | Extract "images" from reader | 3 avif URLs on cdn3.procomic.net |
| P08 | curl cdn3 image with Referer | HTTP 403 |
| P09 | sort=popular vs sort=latest | Same first 5 IDs; body sizes differ by 3B |
| P10 | .pro listing vs .net listing | Same first 10 IDs; NOT byte-identical |
| P11 | _rsc param without value | RSC:1 header alone returns initialSeries |
| P12 | URL fragment ordering analysis | _rsc after # = in fragment = not sent to server |
| P13 | Thumbnail URL domains | Mix of app.procomic.pro and app.procomic.net |
| P14 | .pro listing URL domain content | 77 .pro URLs, 13 .net URLs embedded |
| P15 | .pro listing redirect check | Direct 200 response, no Location header |
| P16 | "chapters" key count in detail | "chapters":0, "initialChapters":1, "series":3 |
| P17 | Full parse + field validation | 18 series, all 18 have slug, listing parseable |

---

## Issue 1: Domain — baseUrl points to procomic.pro

VERIFIED FACT (P02): procomic.pro/ar/series/manhwa/48/breakers?_rsc=det → HTTP 410, 895B HTML
VERIFIED FACT (P02): procomic.net/ar/series/manhwa/48/breakers?_rsc=det → HTTP 200, 116018B RSC
VERIFIED FACT (P03): procomic.pro reader endpoint → HTTP 410
VERIFIED FACT (P03): procomic.net reader endpoint → HTTP 200, 58516B
VERIFIED FACT (P01): procomic.pro listing → HTTP 200, 154748B RSC (WORKS)
VERIFIED FACT (P01): procomic.net listing → HTTP 200, 154705B RSC (WORKS)
VERIFIED FACT (P10): .pro and .net listing return same series IDs (not byte-identical, differ by 51B)
VERIFIED FACT (P15): .pro listing is NOT a redirect — it's a real direct response
HIGH-CONFIDENCE INFERENCE: .pro and .net are independent deployments at different migration states
HIGH-CONFIDENCE INFERENCE: All detail/chapter/reader requests must go to .net
HIGH-CONFIDENCE INFERENCE: Listing could stay on .pro, but .net works identically — no mixed-domain needed
HYPOTHESIS: The 51-byte difference in listing bodies is RSC metadata, not content data

IMPACT: Any manga opened for detail (SManga.url resolution) returns 410 on .pro → empty parse
REGRESSION RISK OF FIX: LOW. .net listing returns identical series IDs. No path diverges.
FIX: Change baseUrl to "https://procomic.net" (one-character domain change)

---

## Issue 2: RSC Headers — Global headersBuilder() adds RSC:1 to all requests

VERIFIED FACT (P05): Without RSC:1, server returns text/html (282651B) — HTML, no initialSeries
VERIFIED FACT (P05): With RSC:1 only (no NRST), server returns text/x-component (169177B) — RSC with initialSeries
VERIFIED FACT (P05): With NRST only (no RSC:1), server returns text/html (282667B)
VERIFIED FACT: User screenshot shows "1:\"$Sreact.fragment\"" in WebView → RSC wire format
HIGH-CONFIDENCE INFERENCE: headersBuilder() is used for WebView navigation (Tachiyomi source contract)
HIGH-CONFIDENCE INFERENCE: RSC:1 in headersBuilder() → WebView GET includes RSC:1 → server returns RSC → WebView displays wire format
VERIFIED FACT: RSC:1 is the only header needed to trigger RSC response
VERIFIED FACT: Next-Router-State-Tree is optional (RSC:1 alone is sufficient)

IMPACT: WebView shows raw RSC format for any manga opened in browser
REGRESSION RISK OF FIX: LOW. RSC data requests continue to work via rscHeaders().
FIX: Remove RSC:1 and Next-Router-State-Tree from headersBuilder(); add private rscHeaders() used only in data request methods

IMPLEMENTATION NOTE: imageRequest already had .removeAll("RSC") and .removeAll("Next-Router-State-Tree"). After the fix, these calls become unnecessary. Remove them for cleanliness.

---

## Issue 3: Search URL — _rsc query param placed after fragment

VERIFIED FACT (P11): Server returns RSC even without _rsc param in URL (RSC:1 header is sufficient)
VERIFIED FACT (P12): Current code: "?page=N#q=hunter&_rsc=srcN" → OkHttp strips fragment → server receives "?page=N"
VERIFIED FACT (P12): Fixed code: "?page=N&_rsc=srcN#q=hunter" → server receives "?page=N&_rsc=srcN"
HIGH-CONFIDENCE INFERENCE: _rsc param is never received by server in current code
HIGH-CONFIDENCE INFERENCE: This does NOT break RSC triggering (header alone is sufficient)

CLASSIFICATION OF IMPACT: COSMETIC / ARCHITECTURAL CORRECTNESS
The search actually works in functional terms (RSC:1 header triggers RSC response). The query is stored in the fragment and retrieved client-side in searchMangaParse. The _rsc param being in the fragment is architecturally wrong but functionally harmless.

REGRESSION RISK OF FIX: VERY LOW. Two-line swap.
FIX: Swap lines 127-128 — append _rsc before the fragment.

---

## Issue 4: Pagination — server ignores page parameter

VERIFIED FACT (P04): page=1, page=2, page=5 all return identical bodies (138585 bytes each)
VERIFIED FACT (P04): First 3 IDs for all pages: [691, 690, 689]
VERIFIED FACT (P17): 18 total series, 14 non-novel → mangas.size=14 < 20 threshold
HIGH-CONFIDENCE INFERENCE: hasNextPage currently evaluates to false (14 < 20) with current data
HIGH-CONFIDENCE INFERENCE: If series count grows to 21+, hasNextPage would become true → infinite scroll
VERIFIED FACT (P04): "total" field NOT FOUND in RSC listing response

CLASSIFICATION: CURRENT STATE — not causing issues with 14 series; FUTURE RISK — infinite scroll if >20 series added
FIX: Set hasNextPage = false. Remove dependency on series count.
REGRESSION RISK: LOW.

---

## Issue 5: Parser — extractSeriesDetail uses unreliable "id": fallback

VERIFIED FACT (P02/P16): Detail RSC has zero "initialSeries" occurrences
VERIFIED FACT (P16): "series":{" occurs at exactly 1 position in detail RSC (pos 6433)
VERIFIED FACT (P06): extractJsonObjectAfterKey-style extraction at pos 6433 → 4574B object → successfully parses to id:48 Breakers
VERIFIED FACT (P02): Detail RSC body has 276 "id": occurrences → old fallback unreliable
VERIFIED FACT: Current extractSeriesDetail: tries extractSeriesList() first (fails: no initialSeries) → falls back to finding "id": (unreliable)
HIGH-CONFIDENCE INFERENCE: extractSeriesDetail currently returns wrong object or null for any series detail page

IMPACT: CRITICAL. Series detail view shows wrong or empty metadata for all series.
FIX: Add extractJsonObjectAfterKey helper; use "series" as primary key in extractSeriesDetail
REGRESSION RISK: LOW. "series":{" is unique; bracket-counting parser is proven correct.

---

## Issue 6: Parser — extractChapterList tries wrong key first

VERIFIED FACT (P16): "chapters" key occurs 0 times in detail RSC
VERIFIED FACT (P16): "initialChapters" key occurs 1 time in detail RSC
VERIFIED FACT (P06): "initialChapters":[" at pos 69582 → 30 AR chapters extracted correctly
HIGH-CONFIDENCE INFERENCE: Current code always falls through to the fallback ("initialChapters") — adds unnecessary scan time
VERIFIED FACT: Chapter filter (language == "AR") required: all 30 chapters ARE AR (confirmed by P06)

IMPACT: MINOR (falls through to correct key). Performance cost of scanning 69KB for nonexistent "chapters".
FIX: Swap key priority: "initialChapters" first, "chapters" second.

---

## Issue 7: Unused class ProComicSeriesListResponse

VERIFIED FACT (P04): "total" NOT FOUND in listing RSC response
VERIFIED FACT: ProComicSeriesListResponse is declared but never instantiated or called in any parser function
VERIFIED FACT: extractSeriesList() deserializes List<ProComicSeriesDto> directly from bracket-extracted array

IMPACT: Dead code. Misleading. "total: Int = 0" is a lie (field doesn't exist in response).
FIX: Remove the class.

---

## Issue 8: CDN Image 403

VERIFIED FACT (P08): cdn3.procomic.net image URL → HTTP 403 with Referer: https://procomic.net/
VERIFIED FACT (P07): Reader RSC contains exactly 3 image URLs in "images" array (publicImageCount limit)
HYPOTHESIS: Authentication required for full image access
HYPOTHESIS: Cookies from a login session might bypass 403
UNKNOWN: Whether "app.procomic.net/chapters/" URLs (different from cdn3) are guest-accessible

IMPACT: Reader shows 3 images max, all 403. Reader is effectively broken for guests.
STATUS: KNOWN LIMITATION. Out of scope for this recovery (requires auth flow).

---

## Scraplng Re-evaluation

Scrapling (https://github.com/D4Vinci/Scrapling) is a Python adaptive web scraping framework.

VERIFIED FACT: It is a Python library.
VERIFIED FACT: This extension is Kotlin/JVM targeting Android.
VERIFIED FACT: Python libraries cannot be bundled in Android APKs.
VERIFIED FACT: ProComic serves text/x-component RSC wire format, not rendered HTML DOM.
VERIFIED FACT: Scrapling is designed for HTML DOM extraction (CSS/XPath selectors).
VERIFIED FACT: curl + Python json are sufficient for all investigation needs (proven by 17 probes above).

VERDICT: NOT USING IT.
- Cannot run in production extension (wrong language/platform)
- Not applicable to RSC wire format (wrong target format)
- Adds no value over curl for investigation

---

## Revised Commit Plan

Commits ordered by: evidence strength × user-visible impact × regression safety

| # | Commit | Evidence | Impact | Risk |
|---|--------|----------|--------|------|
| 1 | fix(headers): RSC headers per-request only | VERIFIED FACT | HIGH | LOW |
| 2 | fix(domain): baseUrl → procomic.net | VERIFIED FACT | CRITICAL | LOW |
| 3 | fix(parser): "series" key + "initialChapters" first | VERIFIED FACT | HIGH | LOW |
| 4 | fix(pagination): hasNextPage = false | VERIFIED FACT | MEDIUM | LOW |
| 5 | fix(search): _rsc before URL fragment | VERIFIED FACT | LOW | VERY LOW |
| 6 | chore(dto): remove ProComicSeriesListResponse | VERIFIED FACT | COSMETIC | NONE |

NOTE: Commit 1 (RSC headers) is first per user spec ("If confidence is high, this should become the FIRST implementation task") and per evidence strength.

---

## Regression Checklist (per commit)

After EVERY commit:
1. ./gradlew :app:assembleDebug → MUST succeed
2. Verify changed code path produces expected behavior
3. Verify other request paths are unaffected

Full regression after ALL commits:
- [ ] Popular tab: 14 series appear (no novel types)
- [ ] Latest tab: 14 series appear (same data as popular with current dataset)
- [ ] Series detail: description, genres, author populated (Breakers test: id:48)
- [ ] Chapter list: 30 AR chapters sorted descending
- [ ] Reader: images array found (3 items, CDN 403 is known limitation)
- [ ] Search: text filter applies client-side correctly
- [ ] WebView: HTML rendered (not RSC wire format)
- [ ] No infinite scroll triggered on first load (14 < 20 threshold)

