# ProComic Branch and Commit Topology

**Status:** CURRENT

**Repository:** [LoneVertex/mihon-extension-ar-procomic](https://github.com/LoneVertex/mihon-extension-ar-procomic)

**Default branch:** `main`

**Authoritative remote implementation branch:** [`fix/runtime-eof-search-feeds`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/runtime-eof-search-feeds)

**Implementation baseline HEAD:** [`8f88ec9fe839cbbca9076cd0c866f287a7b684dd`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/8f88ec9fe839cbbca9076cd0c866f287a7b684dd)

**Latest source-remediation HEAD:** [`affbcf3784412746d4e8ea5c8609924e1aa20e11`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/affbcf3784412746d4e8ea5c8609924e1aa20e11)

The documentation synchronization and CI action remediation are historical non-source commits after the implementation baseline. The 26-commit count below refers only to the original implementation/review stack; the current source-remediation commit `affbcf3` is a focused follow-up source/test commit after that stack.

**Local checkout note:** The verified local checkout uses branch `procomic-ready-to-test`, which tracks `origin/fix/runtime-eof-search-feeds`. Documentation pushes use an explicit `HEAD:fix/runtime-eof-search-feeds` refspec.

**Default branch baseline:** `main` remains unchanged at `76c8ed49ee81d066d30cebe6e412040db2d43a73`.

## Current Topology

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
  | 1285213d  ci: modernize action versions and synchronize audit documentation
  | affbcf3   fix(procomic): map lifecycle status and harden protected tile decoding
  |                         ← fix/runtime-eof-search-feeds / PR #11 → fix/full-remediation
```

The historical implementation stack is 26 commits ahead of `main`. The history remains unsquashed: the first 13 commits form the `fix/full-remediation` review stack, and the later 13 commits form the `fix/runtime-eof-search-feeds` stack. The documentation-only synchronization and CI remediation commits are intentionally excluded from that count; the current `affbcf3` source/test remediation is a focused follow-up after the historical stack.

## Exact Stack Summary

| Stack position | Commit | Message summary | Review stack |
|---:|---|---|---|
| 1 | `81de09d` | `chore(version): bump extension to 1.1` | PR #10 base stack |
| 2 | `7f8fe084` | `fix(reader): align page loading with canonical ProComic chapter route` | PR #10 base stack |
| 3 | `bd18af03` | `fix(details): handle canonical ProComic series route` | PR #10 base stack |
| 4 | `438828b0` | `fix(chapters): normalize language and chapter ordering` | PR #10 base stack |
| 5 | `363c606` | `fix(details): handle restricted series RSC payloads` | PR #10 base stack |
| 6 | `9465f803` | `fix(diagnostics): redact runtime request and response logging` | PR #10 base stack |
| 7 | `cdfa605` | `fix(chapters): unify legacy and REST chapter normalization` | PR #10 base stack |
| 8 | `2458256` | `fix(feeds): use verified Popular API contract` | PR #10 base stack |
| 9 | `1fd9e7c` | `fix(feeds): use verified Latest API contract` | PR #10 base stack |
| 10 | `666d1f0` | `feat(chapters): add persistent show-paid preference` | PR #10 base stack |
| 11 | `e5ef4d0` | `hardening(parser): bound RSC candidates and image hosts` | PR #10 base stack |
| 12 | `4265b49` | `docs: synchronize repository and system documentation` | PR #10 base stack |
| 13 | `1ce41bb` | `docs: finalize repository hygiene and release documentation` | PR #10 base stack |
| 14 | `3888b28` | `fix(runtime): prevent EOF on search and feed responses` | PR #11 stack |
| 15 | `3438b0e` | `fix(search): harden thumbnail fallback and chapter pagination lifecycle` | PR #11 stack |
| 16 | `88500ea` | `ci: validate focused fix branches` | PR #11 stack |
| 17 | `477dc12` | `ci: publish debug and release APKs` | PR #11 stack |
| 18 | `463de71` | `fix: handle feed variants and restricted reader states` | PR #11 stack |
| 19 | `99b2272` | `fix(search): fix search continuation and escaped reader manifests` | PR #11 stack |
| 20 | `89cf1ff` | `fix: RSC bracket scanner and Search batch consumption` | PR #11 stack |
| 21 | `b225a90` | `fix: Search relevance and update official ProComic icon` | PR #11 stack |
| 22 | `8cd4ffc` | `feat: deferred-media Reader flow with proxy-plan tile reconstruction` | PR #11 stack |
| 23 | `5145460` | `fix: defer AVIF native loading until protected tile decoding` | PR #11 stack |
| 24 | `6bbdc05` | `fix: sibling deferred media parsing and stale genre filter` | PR #11 stack |
| 25 | `8f88ec9` | `fix: harden protected tile decoding and rank Search results` | PR #11 stack |

The original implementation baseline is `8f88ec9fe839cbbca9076cd0c866f287a7b684dd`. The documentation synchronization (`ceafa8f`) and CI action remediation (`1285213d`) are historical non-implementation commits; the current focused source/test remediation is `affbcf3`. The 26-commit historical stack remains unchanged and unsquashed.

## Pull Requests and Remote Branches

| Item | State and relationship |
|---|---|
| PR #10 | Open; `fix/full-remediation` → `main`; must not be merged without explicit approval |
| PR #11 | Open; `fix/runtime-eof-search-feeds` → `fix/full-remediation`; must not be merged without explicit approval |
| `main` | Default branch; protected by process; unchanged at `76c8ed49ee81d066d30cebe6e412040db2d43a73` |
| `fix/full-remediation` | Open PR #10 head at `1ce41bb6ab4968dfa7f1862572171333a9129f38` before the documentation sync |
| `fix/runtime-eof-search-feeds` | Open PR #11 branch; historical implementation baseline `8f88ec9fe839cbbca9076cd0c866f287a7b684dd`, followed by documentation/CI commits and current source-remediation commit `affbcf3` |
| Local `procomic-ready-to-test` | Clean checkout tracking `origin/fix/runtime-eof-search-feeds` |

## Dependabot Pull Requests

Dependabot PRs #1–#9 are open and target `main`. They remain available for a separate compatibility review; none is merged, closed, or retargeted by this documentation synchronization.

## Tags and Releases

The repository currently has no Git tags and no GitHub Releases. Creating a version tag or publishing a release is a separate approval-gated operation. CI APK artifacts and their checksums are recorded in `docs/VALIDATION.md` and the external synchronization evidence bundle, not treated as a GitHub Release.

## Review and Release Path

The intended review order is PR #11 into `fix/full-remediation`, followed by PR #10 into `main`, only if each operation is explicitly approved. The documentation sync pushes only to `fix/runtime-eof-search-feeds`; it does not merge, close, delete, retarget, force-push, tag, release, or modify `main`.
