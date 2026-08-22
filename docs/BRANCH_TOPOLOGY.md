# ProComic Branch and Review Topology

**Status:** CURRENT

**Repository:** [LoneVertex/mihon-extension-ar-procomic](https://github.com/LoneVertex/mihon-extension-ar-procomic)

**Default branch:** `main`

**Authoritative remote implementation branch:** [`fix/runtime-eof-search-feeds`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/runtime-eof-search-feeds)

**Documentation snapshot parent HEAD:** [`90234e2`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/90234e2)

**Focused Reader source commit:** [`334888c`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/334888c)

The snapshot parent includes the pushed Reader remediation and the compile-only Jsoup security update; the final documentation commit is the child of this snapshot.

**Implementation baseline HEAD:** [`8f88ec9fe839cbbca9076cd0c866f287a7b684dd`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/8f88ec9fe839cbbca9076cd0c866f287a7b684dd)

**Local checkout:** The verified local checkout uses branch `procomic-ready-to-test`, which tracks `origin/fix/runtime-eof-search-feeds`. Pushes use an explicit `HEAD:fix/runtime-eof-search-feeds` refspec.

**Default branch baseline:** `main` remains unchanged at `76c8ed49ee81d066d30cebe6e412040db2d43a73`.

## Current topology

```text
main  76c8ed49ee81d066d30cebe6e412040db2d43a73
  |
  | 81de09d3  chore(version): bump extension to 1.1
  | 7f8fe084  fix(reader): align page loading with canonical ProComic chapter route
  | bd18af03  fix(details): handle canonical ProComic series route
  | 438828b0  fix(chapters): normalize language and chapter ordering
  | 363c606  fix(details): handle restricted series RSC payloads
  | 9465f803  fix(diagnostics): redact runtime request and response logging
  | cdfa605  fix(chapters): unify legacy and REST chapter normalization
  | 2458256  fix(feeds): use verified Popular API contract
  | 1fd9e7c8  fix(feeds): use verified Latest API contract
  | 666d1f0a  feat(chapters): add persistent show-paid preference
  | e5ef4d01  hardening(parser): bound RSC candidates and image hosts
  | 4265b49  docs: synchronize repository and system documentation
  | 1ce41bb  docs: finalize repository hygiene and release documentation
  |                         ← fix/full-remediation / PR #10 → main
  | 3888b28  fix(runtime): prevent EOF on search and feed responses
  | 85aad15  fix(feeds): preserve Popular cover image URLs
  | 3438b0e  fix(search): harden thumbnail fallback and chapter pagination lifecycle
  | 88500ea  ci: validate focused fix branches
  | 477dc12  ci: publish debug and release APKs
  | 463de71  fix: handle feed variants and restricted reader states
  | 99b2272  fix(search): fix search continuation and escaped reader manifests
  | 89cf1ff  fix: RSC bracket scanner and Search batch consumption
  | b225a90  fix: Search relevance and update official ProComic icon
  | 8cd4ffc  feat: deferred-media Reader flow with proxy-plan tile reconstruction
  | 5145460  fix: defer AVIF native loading until protected tile decoding
  | 6bbdc05  fix: sibling deferred media parsing and stale genre filter
  | 8f88ec9  fix: harden protected tile decoding and rank Search results
  | ceafa8f  docs: synchronize documentation to current implementation state
  | 1285213d  ci: update GitHub Actions to supported runtimes
  | 30824a0  docs: record repository audit and CI remediation
  | affbcf3  fix(procomic): map lifecycle status and harden protected tile decoding
  | e64cee1  docs: synchronize status, reader, and APK evidence
  | 0bda7ea  ci: run contract tests with least-privilege workflow
  | f3f4290  ci: install pinned icon test dependency
  | bf4c8d6  docs: synchronize full audit and CI validation state
  | 334888c  fix(reader): update AVIF decoder for protected tiles
  | 89a2859  docs(reader): record Android 16 remediation evidence
  | 90234e2  chore(repo): synchronize topology and dependency security
  |                         ← fix/runtime-eof-search-feeds / PR #11 → fix/full-remediation
```

The documentation snapshot parent is 37 commits ahead of `main`; the final documentation commit is one additional child.  The first 13 commits form the `fix/full-remediation` review stack represented by PR #10. The remaining 23 commits form the stacked `fix/runtime-eof-search-feeds` review branch represented by PR #11. History remains unsquashed and no public history was rewritten.

## Commit stack

| Position | Commit | Message summary | Review stack |
|---:|---|---|---|
| 1 | `81de09d` | `chore(version): bump extension to 1.1` | PR #10 base |
| 2 | `7f8fe084` | `fix(reader): align page loading with canonical ProComic chapter route` | PR #10 base |
| 3 | `bd18af03` | `fix(details): handle canonical ProComic series route` | PR #10 base |
| 4 | `438828b0` | `fix(chapters): normalize language and chapter ordering` | PR #10 base |
| 5 | `363c606` | `fix(details): handle restricted series RSC payloads` | PR #10 base |
| 6 | `9465f803` | `fix(diagnostics): redact runtime request and response logging` | PR #10 base |
| 7 | `cdfa605` | `fix(chapters): unify legacy and REST chapter normalization` | PR #10 base |
| 8 | `2458256` | `fix(feeds): use verified Popular API contract` | PR #10 base |
| 9 | `1fd9e7c` | `fix(feeds): use verified Latest API contract` | PR #10 base |
| 10 | `666d1f0` | `feat(chapters): add persistent show-paid preference` | PR #10 base |
| 11 | `e5ef4d0` | `hardening(parser): bound RSC candidates and image hosts` | PR #10 base |
| 12 | `4265b49` | `docs: synchronize repository and system documentation` | PR #10 base |
| 13 | `1ce41bb` | `docs: finalize repository hygiene and release documentation` | PR #10 base |
| 14 | `3888b28` | `fix(runtime): prevent EOF on search and feed responses` | PR #11 |
| 15 | `85aad15` | `fix(feeds): preserve Popular cover image URLs` | PR #11 |
| 16 | `3438b0e` | `fix(search): harden thumbnail fallback and chapter pagination lifecycle` | PR #11 |
| 17 | `88500ea` | `ci: validate focused fix branches` | PR #11 |
| 18 | `477dc12` | `ci: publish debug and release APKs` | PR #11 |
| 19 | `463de71` | `fix: handle feed variants and restricted reader states` | PR #11 |
| 20 | `99b2272` | `fix(search): fix search continuation and escaped reader manifests` | PR #11 |
| 21 | `89cf1ff` | `fix: RSC bracket scanner and Search batch consumption` | PR #11 |
| 22 | `b225a90` | `fix: Search relevance and update official ProComic icon` | PR #11 |
| 23 | `8cd4ffc` | `feat: deferred-media Reader flow with proxy-plan tile reconstruction` | PR #11 |
| 24 | `5145460` | `fix: defer AVIF native loading until protected tile decoding` | PR #11 |
| 25 | `6bbdc05` | `fix: sibling deferred media parsing and stale genre filter` | PR #11 |
| 26 | `8f88ec9` | `fix: harden protected tile decoding and rank Search results` | PR #11 |
| 27 | `ceafa8f` | `docs: synchronize documentation to current implementation state` | PR #11 follow-up |
| 28 | `1285213d` | `ci: update GitHub Actions to supported runtimes` | PR #11 follow-up |
| 29 | `30824a0` | `docs: record repository audit and CI remediation` | PR #11 follow-up |
| 30 | `affbcf3` | `fix(procomic): map lifecycle status and harden protected tile decoding` | PR #11 follow-up |
| 31 | `e64cee1` | `docs: synchronize status, reader, and APK evidence` | PR #11 follow-up |
| 32 | `0bda7ea` | `ci: run contract tests with least-privilege workflow` | PR #11 follow-up |
| 33 | `f3f4290` | `ci: install pinned icon test dependency` | PR #11 follow-up |
| 34 | `bf4c8d6` | `docs: synchronize full audit and CI validation state` | PR #11 follow-up |
| 35 | `334888c` | `fix(reader): update AVIF decoder for protected tiles` | PR #11 follow-up |
| 36 | `89a2859` | `docs(reader): record Android 16 remediation evidence` | PR #11 follow-up |
| 37 | `90234e2` | `chore(repo): synchronize topology and dependency security` | PR #11 follow-up |

## Pull requests and remote branches

| Item | Verified state and relationship |
|---|---|
| PR #10 | Open; `fix/full-remediation` → `main`; head `1ce41bb6ab4968dfa7f1862572171333a9129f38`; no merge or close performed |
| PR #11 | Open; `fix/runtime-eof-search-feeds` → `fix/full-remediation`; head `89a2859e261c1e48dbc2ddd36a410d8b90fade76`; push and pull-request CI passed |
| `main` | Default branch; unchanged at `76c8ed49ee81d066d30cebe6e412040db2d43a73` |
| `fix/full-remediation` | PR #10 head remains `1ce41bb6ab4968dfa7f1862572171333a9129f38` |
| `fix/runtime-eof-search-feeds` | Documentation snapshot parent head is `90234e2`; final documentation child follows it |
| Local `procomic-ready-to-test` | Clean checkout tracking `origin/fix/runtime-eof-search-feeds` |

## Dependabot pull requests

Dependabot PRs #1–#9 are open and target `main`. They remain available for separate compatibility review; none was merged, closed, deleted, or retargeted by this operation.

## Tags and releases

The repository has no Git tags and no GitHub Releases. Creating a version tag or publishing a release remains a separate approval-gated operation. CI APK artifacts and checksums are recorded in the current validation evidence and are not treated as a GitHub Release.

## Review and release path

The intended review order is PR #11 into `fix/full-remediation`, followed by PR #10 into `main`, only if each operation is explicitly approved. This operation updates only `fix/runtime-eof-search-feeds`; it does not merge, close, delete, retarget, force-push, tag, release, or modify `main`.
