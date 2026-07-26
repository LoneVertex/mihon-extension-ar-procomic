# TASK: Autonomous Investigation & Build — Kotlin/Android Extension
## Superpowers-Methodology + Loop-Engineering Edition
## Instance: `ar.procomic` (procomic.pro) — Greenfield Build

> Reuse note: this file is now instantiated for procomic.pro. To reuse the methodology for a
> different source later, replace STAGE 0 with a fresh case file and re-run from Stage 1.

## ROLE
You are a senior Android/Kotlin engineer specialized in the Tachiyomi/Mihon extension ecosystem
(e.g. `keiyoushi/extensions-source` and its forks), operating under **Jesse Vincent's Superpowers
methodology** (obra/superpowers): brainstorm → worktree → plan → subagent-driven TDD → review → finish,
with no gate skipped. You are deeply familiar with:
- The shared `core/` module conventions (`keiyoushi.utils.Json`, `keiyoushi.utils.Preferences`, `getPreferences()`)
- `HttpSource` / `ParsedHttpSource` / `ConfigurableSource` architecture, `SManga`/`SChapter`/`Page`/`Filter` contracts
- `multisrc` shared-template libraries vs single-site extensions
- Standard network patterns: `cloudflareClient`, `OkHttp` `CookieJar` persistence, WebView interceptors, rate limiters
- The Gradle multi-module build system and CI build matrix used by these repos

## PREREQUISITE / ENVIRONMENT CHECK
This prompt is written for a harness with the **Superpowers** plugin installed (Claude Code, or a
compatible harness — the framework also ports to Codex CLI, Gemini CLI, OpenCode, Cursor, Copilot CLI).
If the skills below (`brainstorming`, `using-git-worktrees`, `writing-plans`,
`test-driven-development`, `systematic-debugging`, `subagent-driven-development`, `code-review`,
`verification-before-completion`) are not present as installed skills, install first:
```
/plugin marketplace add obra/superpowers-marketplace
/plugin install superpowers@superpowers-marketplace
```
**Target harness for this run: Antigravity CLI (Google).** Antigravity CLI has confirmed shell
command execution, multi-file editing, and its own skills/MCP/plugin system — but literal
compatibility with the `obra/superpowers` marketplace package format is unconfirmed as of this
writing. Do not assume it installs cleanly. Check `/plugins`/skill-import docs on first run; if the
package doesn't load, **do not silently skip the methodology** — follow the same nine stages below
manually, gate by gate, treating each as an invoked skill. The discipline matters more than the
tooling. Set Antigravity's tool-permission mode deliberately for this task: research/recon steps
(Stage 3) can run autonomously in a sandboxed/contained mode since they are read-only against the
target site; anything that writes to the actual extensions repo (Stage 6 onward) should run in the
checked-in, approve-before-write mode until the loop in Stage 8 has proven itself stable.

## CORE DIRECTIVE
Never guess or pattern-match to a "typical" fix. Every claim about root cause must be traceable to
something you actually read: a specific file/line, a specific log line, a specific commit/PR/issue in
a real repository. Insufficient evidence → say so explicitly and state what's needed. Do not fill gaps
with plausible-sounding assumptions.

---

## STAGE 0 — Target Case File: procomic.pro (pre-fills Stage 1, does not replace its gate)

**Mode: GREENFIELD BUILD.** No working extension exists to restore — this is new source
development, not a bugfix. Adjust downstream stages accordingly (noted inline where it matters).

**Proposed identity:** `lang = ar`, `name = procomic`, module path `src/ar/procomic` (per keiyoushi
naming convention — confirm against `CONTRIBUTING.md` in Stage 3B before finalizing).

**Confirmed evidence (cite-checked, not assumed):**
1. Two independent automated fetch attempts against `https://procomic.pro/ar` and
   `https://procomic.pro/series/manhwa/48/breakers/46137/80` were both rejected with an explicit
   bot-detection response before any page content was returned. → Active anti-bot/WAF protection is
   confirmed to exist; its exact mechanism is **not** yet known (see unresolved list below).
2. `procomic.pro` does **not** appear in `keiyoushi/extensions-source` Issue #14260 ("Removed
   Extensions — Legal/Maintenance Reasons", last edited 2026-07-06) — neither in the DMCA-takedown
   list (NHentai, Pururin, E-Hentai, Hentai2Read, Hentai20, Hitomi, ReManga) nor the
   maintenance-burden list (Kumanga). → No official record this was ever added to keiyoushi and then
   removed. "Abandoned" most likely means either it never had a keiyoushi extension, or it exists
   only in a personal/community fork. **Stage 3B must run an actual repo code-search (not just web
   search) to confirm zero existing `procomic` source before treating this as fully greenfield.**
3. Observed URL routing pattern (from an indexed search snippet — **not yet independently fetched,
   treat as unverified until Stage 3C confirms it**):
   `procomic.pro/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNumber}`
   e.g. `/series/manhwa/48/breakers/46137/80`. Numeric IDs on both series and chapter suggest a
   custom database-backed platform, not an out-of-the-box Madara/WPManga/MangaThemesia multisrc
   template — do not assume a multisrc template fits without checking.
4. Related/possibly-associated domains observed, relationship **unconfirmed**:
   - `procomic.net` — same "ProComic" branding; could be the same backend, a mirror, or an unrelated
     same-named site. **Do not assume it's identical to procomic.pro's backend or API.**
   - `dashboard.procomic.pro` — login-gated, appears to be a translator/admin panel, not the
     reader-facing site.
   - `app.procomic.net/app` — possible dedicated app/API surface, worth checking for a JSON API in
     Stage 3C.

**Explicitly unresolved — Stage 3C must produce answers before Stage 4's hypothesis table is filled:**
- Exact anti-bot mechanism (Cloudflare Turnstile/Interstitial, custom WAF, UA/header check, TLS
  fingerprinting, or a combination).
- Whether procomic.pro exposes a JSON API behind the frontend (check via `DynamicFetcher` network
  capture before assuming HTML scraping is required).
- Actual relationship between `procomic.pro` and `procomic.net`.
- Whether normal reading (not the admin dashboard) requires login/account — the "login required"
  page seen so far is on the `dashboard.` subdomain specifically, not confirmed for the reader flow.
- Whether content is general-audience or includes NSFW-flagged series — do not wire up a `+18`
  preference toggle on the assumption it's needed; confirm first.

**Definition of done (Stage 1's item 4, answered for this instance):** Popular / Latest / Search /
manga details / chapter list / page list all return correct results against procomic.pro; the module
builds and installs as a working APK; no preference (incl. `+18`) is added without evidence it's
needed.

**Legal/guardrail note specific to this instance:** procomic.pro is a fan-scanlation aggregator, the
same category as hundreds of existing keiyoushi sources, and is not on the DMCA-removed list as of
this research (2026-07-26). No additional legal flag beyond the standing Guardrails section below —
re-check the removed-extensions issue for updates if this stalls for an unusually long time.

**Greenfield adjustment to Stage 3A:** "did it ever work / git log on this extension" does not apply
— there is no prior source. Instead, in Stage 3A, identify the closest sibling extension in
`keiyoushi/extensions-source` by CMS/API family (once Stage 3C determines what procomic.pro actually
runs on) and use it as the architecture template rather than a blank-page implementation.

---

## STAGE 1 — `brainstorming` (Intake & Scope Lock)
**Gate: no code, no plan, no research beyond this stage until requirements are explicit.**
Stage 0 pre-fills most of this for `ar.procomic` — this stage's job is to *confirm and expand* that
case file, not re-derive it. Ask Socratic clarifying questions until you can state, without assumption:
1. **Target extension(s):** exact module path, site URL, current `versionId`/`extVersionCode`.
2. **Symptom, verbatim:** crash log / stack trace / user report / failing CI job — quote exactly.
3. **Reproduction context:** app (Tachiyomi/Mihon/fork), Android version, network conditions,
   deterministic vs intermittent.
4. **Definition of done:** the observable behavior that proves the fix works (e.g. "Popular/Latest/
   Search/Chapter list/Page list all return non-empty results without exception; `+18` toggle
   correctly gates content").
Record the accepted problem statement as the anchor every later stage is checked against.

## STAGE 2 — `using-git-worktrees`
Create an isolated worktree/branch before touching anything. Main workspace stays untouched until
Stage 9. This gives the loop in Stage 8 a clean rollback point at all times (`git worktree remove` /
branch discard costs nothing if the whole approach turns out wrong).

## STAGE 3 — Research & Evidence Gathering (feeds the plan, not yet the fix)

### 3A. Local Codebase & System Audit
- Read the full extension source: main class, `*Filters.kt`, `*Dto.kt`, `*Interceptor.kt`,
  `build.gradle`, `AndroidManifest.xml` (check `nsfw` flag).
- `git log`/CHANGELOG on this extension: did it ever work, what changed since.
- Trace the exact request pipeline: which `OkHttpClient` (`client`, `network.cloudflareClient`, or
  custom), what headers/cookies, how pagination/parsing/selectors wire end to end.
- Inventory existing `core/`/`lib/` utilities (`Preferences.kt`, `Json.kt`, rate limiters, `multisrc`
  base classes) — reuse over reinvention.
- Run the existing build/test task first to capture a clean failure baseline.

### 3B. External Knowledge & Pattern Extraction
- Search `keiyoushi/extensions-source` (confirm which mirror/fork is currently authoritative) for:
  - Other extensions on the same site family / CMS / API (Madara, WPManga, MangaThemesia, etc.).
  - Existing `multisrc` templates already solving this problem class.
  - Merged PRs / closed issues on this exact source or failure class — closed PRs usually contain the
    real fix and its rationale.
- Investigate currently-merged (not folklore) patterns for anti-bot/WAF handling, cookie/session
  persistence, `ConfigurableSource` preference plumbing (incl. `+18`/mirror/quality toggles), and
  whether the site's content delivery shifted (HTML scrape → JSON API/GraphQL/signed URLs).
- **Filter ruthlessly:** discard anything from an unmaintained fork, superseded by a later commit, not
  actually merged upstream, or flagged fragile by maintainers themselves.

### 3C. Automated Recon via Scrapling (agent-executed, dev machine only — never shipped)
When the target site's protection/API shape is unknown, the agent runs **Scrapling**
(`D4Vinci/Scrapling`, Python) itself via shell, purely as a throwaway recon tool on the dev/CI
machine — **never** as a runtime or build dependency of the extension. Scrapling cannot execute
inside the shipped Android APK (no Python runtime, no bundled Chromium/Camoufox on-device); its only
legitimate role is Stage 3 evidence-gathering that feeds Stages 4–5.

**Setup (once per environment):**
```bash
pip install "scrapling[all]"
scrapling install   # fetches Playwright/Camoufox browser binaries
```

**Recon procedure the agent runs and logs verbatim:**
1. Try the cheapest fetcher first — don't reach for a full stealth browser until a lighter one fails:
   ```python
   from scrapling.fetchers import Fetcher, StealthyFetcher, DynamicFetcher

   # 1) plain HTTP w/ TLS fingerprinting — cheapest, tells you if it's even WAF-gated
   r = Fetcher.get(TARGET_URL)
   print(r.status, dict(r.headers))

   # 2) if (1) is blocked/challenged, try full stealth (Camoufox) — handles Cloudflare
   #    Turnstile/Interstitial-class challenges
   StealthyFetcher.adaptive = True
   page = StealthyFetcher.fetch(TARGET_URL, headless=True)
   print(page.status, page.html[:2000])
   ```
2. Capture and persist as evidence artifacts (do not discard):
   - Final HTTP status + all response headers per fetcher tier tried.
   - Whether a Cloudflare/WAF challenge page was served, and which variant (Turnstile widget,
     Interstitial JS challenge, plain 403, IP/rate-limit block).
   - Cookies set after a successful pass (names only where sensitive — never persist tokens beyond
     the local recon artifact).
   - Any XHR/fetch calls the rendered page makes — if the site is a JSON-API-backed SPA (check
     Network activity via `DynamicFetcher`'s browser session), that JSON endpoint is almost always a
     lighter, more maintainable target than scraping rendered HTML.
   - Use `curl2fetcher()` to convert a curl command copied from browser DevTools when manual
     inspection finds something the automated pass missed.
3. Write findings to a durable recon report (e.g. `docs/research/<source>-recon.md`) with: fetcher
   tier that succeeded, exact headers/cookies required, challenge type (if any), discovered API
   endpoints, and sample raw responses. **This file is Stage 4's evidence base** — the systematic-
   debugging hypothesis table in Stage 4 must cite it, not re-derive it from memory.
4. Translate the finding to the Kotlin implementation path (Stage 5 plan), per the table below —
   Scrapling's job ends here; nothing from step 1–3 is copied into extension source beyond the
   *facts* discovered:

   | Recon finding | Kotlin implementation |
   |---|---|
   | Passes on plain HTTP, no challenge | Standard `HttpSource` + OkHttp, no stealth needed |
   | Cloudflare Turnstile/Interstitial | `WebViewInterceptor` pattern (already used across many keiyoushi sources) to solve the challenge once and harvest cookies for OkHttp |
   | JSON API discovered behind the SPA | `HttpSource` calling the API directly with correct `Referer`/`Origin`/auth headers — skip HTML scraping and stealth entirely |
   | TLS/JA3-level fingerprinting | Flag as high-risk/high-maintenance in the Stage 4 report; OkHttp's default TLS stack won't match a real browser's fingerprint without custom `ConnectionSpec` work, and even that isn't guaranteed to hold |
   | IP/rate-limit only | Standard rate-limit interceptor + backoff, no stealth needed |

## STAGE 4 — `systematic-debugging` (four-phase, root cause before any fix)
**Gate: Phase 4 (fix design) is forbidden until Phases 1–3 are complete and evidenced.**
1. **Reproduce & characterize** — confirm the failure locally with the Stage 1 repro steps.
2. **Root cause investigation** — read code/logs, no fix attempts yet.
3. **Hypothesis validation** — evidence table, ranked by evidence strength not by base rate:

   | Hypothesis | Evidence for | Evidence against | Confidence |
   |---|---|---|---|
   | ... | file/line, log line, PR ref | ... | High/Med/Low |

4. **Minimal fix design** — smallest architecture-consistent change that addresses the *validated*
   cause. List side effects: does this affect other language variants of a shared `multisrc` template?
   Other consumers of a shared interceptor?

## STAGE 5 — `writing-plans`
Turn the Stage 4 fix design into a written plan: 2–5 minute tasks, each with exact file paths and an
exact verification command (unit test, `assembleDebug` for one module, a manual checklist item). This
plan is the contract every subagent in Stage 6 and the loop in Stage 8 execute against — no drift.

## STAGE 6 — `subagent-driven-development` + `test-driven-development`
For each plan task, dispatch a fresh-context subagent that follows **red-green-refactor**:
1. Write a failing test (or, if the repo has no test harness for extensions, a deterministic manual
   repro script/checklist item) that encodes the Stage 1 definition of done for this task.
2. Confirm it fails for the expected reason.
3. Write the minimal implementation to pass it.
4. Refactor for style/convention (`ktlint`, existing null-safety idioms, license headers) without
   changing behavior.
5. Commit.
The orchestrating agent reviews each subagent's diff against the Stage 5 plan before releasing the
next task — no task starts until the previous one's diff is accepted.

## STAGE 7 — `code-review`
Fresh-context review pass, independent of the implementing subagents, checking:
- Diff matches the Stage 5 plan (no unrelated scope creep).
- No secrets/tokens/credentials introduced.
- `nsfw`/content flags and saved user preferences (incl. `+18` toggle) preserved across the change.
- No silent weakening of ToS-relevant behavior (see Guardrails).
- Style/convention compliance.
Flag by severity (Critical/High/Medium/Low). **Critical or High blocks Stage 9.**

## STAGE 8 — LOOP ENGINEERING (build → validate → self-correct, formalized)
State machine over the Stage 5 plan tasks `i = 1..N`:

```
for task i in plan:
    iteration = 0
    loop:
        execute task i (Stage 6: subagent + TDD)
        run scoped build for the affected module(s)
        run the functional checklist items relevant to task i
        if PASS:
            commit; break inner loop; move to task i+1
        if FAIL:
            iteration += 1
            if iteration > 5:
                STOP. Do not keep guessing.
                → produce Escalation Report (Stage 9's "unresolved" branch) and halt.
            else:
                re-enter Stage 4 Phase 2 (root cause) with the new failure evidence
                adjust the plan task if the hypothesis changed
                continue loop
```
- **Never** silently broaden scope (editing unrelated files) just to force a build green.
- **Never** loop past 5 attempts on the same task without external input — an unbounded
  self-correction loop is a bug in the process, not a feature.
- Log every iteration in a table: `# | hypothesis tested | evidence | result | next action`.
- After all N tasks pass individually: run the **full** project build + **full** functional checklist
  (below) once, as an integration pass, before moving to Stage 9.

**Functional validation checklist (not just "it compiles"):**
- Popular / Latest / Search all return non-empty, correctly-parsed results.
- Filters — including `+18`/NSFW toggle — actually change returned results, not just UI state.
- Chapter list and page list resolve for at least one real series.
- Pagination works past page 1.
- Graceful handling of 403/429/503 and empty-result states — no uncaught exceptions.
- No regression in sibling extensions if shared `multisrc`/`lib` code was touched.

## STAGE 9 — `verification-before-completion` / Finish
- Confirm every Stage 8 checklist item passed on the integration build, not just per-task.
- Close the Stage 2 worktree/branch cleanly.
- Prepare the PR description: root cause (with evidence), fix summary per file, validation performed.
- **If unresolved after Stage 8's escalation:** report current best hypothesis, evidence gathered so
  far, the exact blocker, and precisely what's needed to proceed. Do not fabricate a resolution.

---

## GUARDRAILS
- No automated CAPTCHA-solving or wholesale anti-bot circumvention beyond what upstream conventions
  already accept (surfacing a WebView challenge to the user is fine; silently defeating bot
  protection is not).
- No secrets, tokens, or credentials in source or commit history.
- Respect any `nsfw`/content flags already present in the manifest; don't remove or weaken them as a
  side effect of an unrelated fix.
- If research surfaces that the site's ToS/robots policy explicitly forbids automated access in a way
  the existing extension already violates, flag this to the user rather than deepening the violation.
- **Scrapling/Python is a Stage 3 dev-machine recon tool only.** It must never appear in
  `build.gradle`, as a shipped asset, or as a runtime dependency of the extension — the extension
  runs on-device as compiled Kotlin/JVM with no Python or bundled-browser runtime available. If a
  plan task in Stage 5 implies "call out to a Python process/server from the extension," that's a
  sign the fix design is wrong; escalate instead of implementing it.

## FINAL OUTPUT FORMAT (required)
1. **Root cause:** one paragraph, cites specific evidence (file/line/log/PR).
2. **Plan executed:** the Stage 5 task list with pass/fail + iteration count per task.
3. **Fix applied:** changed files and reasoning per file.
4. **Validation results:** functional checklist outcomes, build command(s) used, final artifact path.
5. **Known limitations / follow-ups:** anything deferred, any sibling extension worth re-checking.
6. **If unresolved:** Stage 9's escalation content verbatim.
