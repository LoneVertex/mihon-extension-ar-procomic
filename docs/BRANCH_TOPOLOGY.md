# ProComic Branch and Commit Topology

**Status:** CURRENT
**Repository:** [LoneVertex/mihon-extension-ar-procomic](https://github.com/LoneVertex/mihon-extension-ar-procomic)
**Default branch:** `main`
**Authoritative PR:** [#10](https://github.com/LoneVertex/mihon-extension-ar-procomic/pull/10)
**Authoritative implementation branch:** [`fix/full-remediation`](https://github.com/LoneVertex/mihon-extension-ar-procomic/tree/fix/full-remediation)

## Current Topology

```text
main  76c8ed49ee81d066d30cebe6e412040db2d43a73
  |
  | 81de09d3  chore(version): bump extension to 1.1
  | 7f8fe084  fix(reader): align page loading with canonical ProComic chapter route
  | bd18af03  fix(details): handle canonical ProComic series route
  | 438828b  fix(chapters): normalize language and chapter ordering
  | 363c606  fix(details): handle restricted series RSC payloads
  | 9465f803  fix(diagnostics): redact runtime request and response logging
  | cdfa605  fix(chapters): unify legacy and REST chapter normalization
  | 2458256  fix(feeds): use verified Popular API contract
  | 1fd9e7c  fix(feeds): use verified Latest API contract
  | 666d1f0  feat(chapters): add persistent show-paid preference
  | e5ef4d0  hardening(parser): bound RSC candidates and image hosts
  |
  └── fix/full-remediation → PR #10 → main
```

## Exact Remediation Commits

| Order | SHA | Message |
|---:|---|---|
| 1 | `9465f80385bbc60082a79f2823f941f529e2a916` | `fix(diagnostics): redact runtime request and response logging` |
| 2 | `cdfa605cf7e0527a3c2f8a7c4f75d5cb0a25a5ae` | `fix(chapters): unify legacy and REST chapter normalization` |
| 3 | `2458256961a6c62b631def9c909d66846c498abf` | `fix(feeds): use verified Popular API contract` |
| 4 | `1fd9e7c808e53014455560635345e6112ecb83f7` | `fix(feeds): use verified Latest API contract` |
| 5 | `666d1f0a8fe51f30cf62525c8ed54f145ab854c5` | `feat(chapters): add persistent show-paid preference` |
| 6 | `e5ef4d0175dab33433767582c7900e20061b1ab3` | `hardening(parser): bound RSC candidates and image hosts` |

The six remediation commits remain unsquashed and are followed by no application commits. The final branch also contains the previously validated baseline commits shown in the topology above.

## Retained Remote Branches

`main` and `fix/full-remediation` are retained. The nine Dependabot branches are retained because each has unique dependency or GitHub Actions work associated with an open Dependabot PR. No intermediate application branches remain remotely after evidence-backed cleanup; their commits remain preserved in the final branch history.

## Review and Release Path

The only authoritative implementation review path is PR #10 from `fix/full-remediation` into `main`. It is not merged automatically during release preparation. The software gate passed, but the final external Android/Mihon validation session remains pending.
