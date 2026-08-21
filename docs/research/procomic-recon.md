> **HISTORICAL / EVIDENCE-ONLY:** This report preserves dated reconnaissance evidence from 2026-07-26. Some endpoint, CDN, and architecture claims were later superseded; it is not current implementation guidance. Use [`docs/PROCOMIC_SYSTEM.md`](../PROCOMIC_SYSTEM.md), [`docs/VALIDATION.md`](../VALIDATION.md), and [`README.md`](../../README.md) for the current extension state.

# ProComic.pro Recon Report — Stage 3C Evidence

**Date:** 2026-07-26
**Investigator:** Antigravity Agent
**Target domains:** procomic.pro, procomic.net, app.procomic.net/app

---

## 1. Anti-Bot / WAF Mechanism

**Finding:** The site uses **Cloudflare** (confirmed via `server: cloudflare` response header and `cf-cache-status`), but NOT Cloudflare Turnstile/Interstitial challenge mode.

**Evidence:**
- `curl -L https://procomic.pro/ar -H "User-Agent: Mozilla/5.0 (Android mobile UA)"` → **HTTP 200** with 277KB of HTML
- Response header: `server: cloudflare`, `cf-cache-status: DYNAMIC`
- The prior automated fetch failures in the brief were due to **missing/default User-Agent** or direct bot UA — NOT a JS challenge page

**Conclusion:** Standard `HttpSource` + OkHttp with a mobile `User-Agent` header is sufficient to pass WAF. **No WebView interceptor needed for page fetching.** The Cloudflare presence is passive edge proxy, not an active challenge.

---

## 2. Platform / CMS Identification

**Finding:** Custom **Next.js App Router** (RSC streaming) frontend with a proprietary backend.

**Evidence:**
- `/_next/static/chunks/` in page HTML → Next.js confirmed
- `app/(prochan-app)/layout` in JS bundle references → internal app name is **"ProChan"**
- RSC endpoint `https://procomic.pro/ar?_rsc=1` returns `content-type: text/x-component` → Next.js RSC wire format
- No WordPress-style admin paths, no `/wp-json/` or Madara/MangaThemesia signatures
- `window.__SAFE_BROWSING` custom JS flag in page HTML → proprietary platform code

**Conclusion:** This is a **greenfield custom platform** called "ProChan". NOT Madara/WPManga/MangaThemesia. No multisrc template applies.

---

## 3. Data Access Strategy

### 3.1 RSC Stream (Chosen Approach)

**Finding:** RSC endpoints return **structured JSON-embedded data** without needing a JS runtime.

**Evidence:**
- `GET /ar/series?_rsc=1` with headers `RSC: 1` → 165KB response containing full series listings
- Series data extracted from RSC stream:
  ```
  {"id":688,"title":"Full Time Hunter: I Hunt the World","slug":"full-time-hunter-i-hunt-the-world","type":"manhua","thumbnail":"https://app.procomic.pro/series-cards/688/originals/...avif"}
  ```
- Chapter data embedded in series detail RSC:
  ```json
  {
    "id": 50675,
    "content_id": 688,
    "chapter_number": "4",
    "language": "AR",
    "cdn_path": "cdn2",
    "metadata": {
      "protectionV2": {
        "version": 5,
        "publicImageCount": 3,
        "staticWatermark": {...}
      }
    }
  }
  ```

**Required headers for RSC requests:**
- `User-Agent: Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 ...`
- `RSC: 1`
- `Accept-Language: ar,en;q=0.9`
- `Next-Router-State-Tree: %5B%22%22%2C%7B%7D%5D` (optional, helps for navigation)

### 3.2 Direct JSON API
**Finding:** No `/api/*` routes exist (all returned 404). The backend API is **internal to the Next.js server** and not exposed publicly as a REST API.

### 3.3 Image CDN

**Finding:** Chapter images served from `cdn2.procomic.pro` with direct URL path pattern.

**Evidence:**
```
https://cdn2.procomic.pro/{seriesId}/{chapterId}/{timestamp}-{filename}.avif
```
Example: `https://cdn2.procomic.pro/688/50675/1784912095035-i8xdrvc35x.avif`

**⚠️ Critical issue: CDN returns 403 Forbidden** even for "public" images when accessed directly.
- `GET https://cdn2.procomic.pro/688/50675/1784912095035-i8xdrvc35x.avif` → **HTTP 403**

This means one of:
1. Images require a session cookie from a successful page visit
2. CDN uses signed URLs with authentication token in URL or header
3. The `publicImageCount: 3` refers to UI-level restriction, CDN is fully gated

**→ This is the CRITICAL blocker that must be resolved in Stage 4.**

---

## 4. Data Model (Confirmed Structure)

### Series Listing RSC (`/ar/series?_rsc=1`)
```
Fields: id (int), title (string), slug (string), type (string: "manga"|"manhwa"|"manhua"|"novel"),
        status (string: "approved"), thumbnail (URL from app.procomic.pro)
```

### Series Detail RSC (`/ar/series/{type}/{id}/{slug}?_rsc=1`)
```
Fields: id, title, slug, type, status, cover, description (AR+EN),
        genres [{id, en, ar, descriptionEn, descriptionAr}],
        tags [{id, en, ar}]
```

### Chapter List (embedded in series detail RSC)
```
Fields: id, content_id, chapter_number, title, language ("AR"|"EN"),
        translator, uploader_id, status, cdn_path, metadata
```

### Chapter Reader RSC (`/ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNum}?_rsc=1`)
```
Fields: images (array of CDN URLs, length = publicImageCount = 3 for guests),
        protectionV2 { publicImageCount, version, staticWatermark { pageIndex, pages[] }}
```

---

## 5. URL Patterns (Confirmed)

| Page | URL Pattern |
|---|---|
| Series listing / Latest | `procomic.pro/ar/series?_rsc=1` |
| Popular | Unknown (to be determined) |
| Search | Unknown (to be determined) |
| Series detail | `procomic.pro/ar/series/{type}/{id}/{slug}?_rsc=1` |
| Chapter reader | `procomic.pro/ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNum}?_rsc=1` |

---

## 6. Domain Relationships (Confirmed)

| Domain | Role |
|---|---|
| `procomic.pro` | Primary reader site (Next.js frontend + backend) |
| `procomic.net` | Mirror/redirect to procomic.pro |
| `app.procomic.net/app` | Mobile-optimized version of same Next.js app |
| `app.procomic.pro` | **CDN for series thumbnails** (`/series-cards/{id}/originals/...avif`) |
| `cdn2.procomic.pro` | **CDN for chapter page images** (`/{seriesId}/{chapterId}/{filename}.avif`) |
| `dashboard.procomic.pro` | Translator/admin panel — out of scope |

---

## 7. Content & Safe-Browsing

**Finding:** The site has a `safe_browsing` toggle (cookie + localStorage).

**Evidence:**
- JS code: `var safeBrowsingEnabled = true; // Guests can never disable safe browsing: ignore any stored "off" value until the visitor is logged in`
- This means guests always have safe browsing ON → content visible to guests is SFW filtered
- `nsfw` preference toggle may be needed IF login-aware session is implemented — but for guest access it's always SFW → **do NOT add an NSFW toggle without login support**

---

## 8. Critical Blocker: CDN Image Access (403)

**Status: UNRESOLVED — Must be addressed in Stage 4 before implementation.**

The images in the RSC stream are accessible URLs but return 403 when fetched directly. Possible causes:
1. **Cloudflare hotlink protection** — requires `Referer: https://procomic.pro` header
2. **Session cookie gate** — requires visiting the page first to set a cookie
3. **Signed URL tokens** — URLs embed auth (not visible in the sampled URLs)
4. **IP-based** — Cloudflare may require the request to come from a browser session

**Next required step:** Test CDN access WITH `Referer` header AND the `language=ar` cookie set from a prior RSC call, and without, to isolate the mechanism.

---

## 9. Recon-to-Implementation Translation Table

| Finding | Kotlin implementation |
|---|---|
| Site passes on plain HTTP with mobile UA | Standard `HttpSource` + OkHttp — no stealth needed |
| RSC endpoint returns structured data | Parse RSC stream in OkHttp `Interceptor` or directly in `HttpSource` methods |
| No public REST API | Cannot use direct JSON calls; must parse RSC wire format |
| CDN returns 403 for images | Must investigate: try Referer + cookie propagation in OkHttp `CookieJar` |
| Only 3 public images per chapter for guests | Extension limited to 3 pages per chapter unless login is implemented |
| `publicImageCount: 3` | Flag as critical limitation — guest-mode extension covers only first 3 pages |
| Genres/tags fully bilingual (AR+EN) | Can expose as `Filter.Select` options — use `en` field as display value |
| Series type: manga/manhwa/manhua/novel | Can expose `type` filter; exclude `novel` type (not comic pages) |
