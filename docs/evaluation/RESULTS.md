# Measured results

Evaluated source revision: `66c38e4b65ddedb254fad5008cc835a258c08e8f`.

Environment: Java 17.0.21, Linux amd64, 2 reported processors.

Small curated development fixtures; see [methodology](README.md). These are offline matching measurements, not production or live AI results.

| Catalogue | Method | Correct decisions | Auto precision | Auto coverage | Positive auto recall | Candidate recall@3 |
|---|---|---:|---:|---:|---:|---:|
| sample (45 foods) | exact | 86/100 (86.0%) | 100.0% | 66.0% | 82.5% | 82.5% |
| sample (45 foods) | fuzzy | 100/100 (100.0%) | 100.0% | 80.0% | 100.0% | 100.0% |
| swiss (1161 foods) | exact | 40/60 (66.7%) | 100.0% | 33.3% | 50.0% | 50.0% |
| swiss (1161 foods) | fuzzy | 58/60 (96.7%) | 95.2% | 70.0% | 100.0% | 100.0% |

## Underspecified-query fix

On the same 60 Swiss queries: false automatic accepts **5 → 2**, automatic precision **88.9% → 95.2%**, automatic coverage **75.0% → 70.0%**.

This is a development regression comparison: the fixture exposed the bug and informed the fix. It is not a held-out improvement estimate. The fix routes generic words to confirmation; it does not add semantic understanding.

## In-process latency

| Catalogue | Method | Measured calls | P50 (ms) | P95 (ms) |
|---|---|---:|---:|---:|
| sample | exact | 1000 | 0.048 | 0.112 |
| sample | fuzzy | 1000 | 0.706 | 1.858 |
| swiss | exact | 600 | 0.524 | 1.190 |
| swiss | fuzzy | 600 | 9.589 | 28.236 |

Three warmup passes, ten measured passes, single thread, no JaCoCo. These exclude REST, model providers, MongoDB, and network time. No speedup versus exact matching is claimed.

## Verification

Backend test run: 56 tests, 0 failures, 0 errors, 1 skipped (the opt-in benchmark test is skipped in the normal test run).
- Overall branch coverage: 229/292 (78.4%). Includes all backend production classes; no exclusions.
- Overall line coverage: 623/672 (92.7%). Includes all backend production classes; no exclusions.

Separately recorded local verification: 10 existing Vitest tests and the TypeScript / Vite production build passed; Python conversion policy had 3 passing tests, including parameterized missing/invalid values. This renderer does not rerun those commands; CI executes the checks separately.

Raw evidence: [matching JSON](matching.json), [pre-fix quality JSON](before-fix.json). CI also uploads Surefire and JaCoCo reports.

## Remaining Swiss errors

| Query | Automatically selected food ID |
|---|---|
| bread | CH-750 |
| cheese | CH-14024 |

The remaining `bread → Breadcrumbs` and `cheese → Cheeseburger` failures show lexical prefix similarity can still cause unsafe automatic decisions. A future matcher should distinguish category names from prepared foods; do not describe this version as fully safe or semantically accurate.

## Live-stack browser smoke

6/6 scenario checks passed across desktop (1440×1000) and mobile (390×844) Chromium viewports, with real Spring Boot and MongoDB, the official Swiss catalogue, and mock AI extraction.

Checks cover official apple nutrients, exclusion of unresolved pork, catalogue search and confirmation, persisted state after reload, no uncaught browser errors, and no horizontal page overflow. Real microphones and live model calls remain untested.

[Desktop screenshot](ui/desktop.png) · [Mobile screenshot](ui/mobile.png) · [Raw browser evidence](ui/results.json)
