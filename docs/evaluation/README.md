# Reproducible evaluation

## Commands

JDK 17+, Python 3.10+, and Docker for integration tests:

```bash
./mvnw clean verify
./mvnw test -Dtest=MatchingEvaluationTest -Dfoodlog.evaluate=true -Djacoco.skip=true \
  -Dsurefire.reportsDirectory=target/evaluation-test-reports \
  -Devaluation.revision=$(git rev-parse HEAD)
```

Normal tests produce `target/site/jacoco/index.html` and XML/CSV coverage. The second command produces `target/evaluation/matching.json`. The evaluation is opt-in and runs without JaCoCo; benchmark calls do not inflate reported test coverage. CI runs both commands and uploads coverage, evaluation, and Surefire evidence.

## Data and baseline

| Dataset | Catalogue | Query labels | Composition |
|---|---:|---:|---|
| sample | 45 approximate demo foods | 100 | 80 positive (65 catalogue names across English/German/Chinese, 15 variants), 20 negative |
| swiss | 1,161 complete-nutrient official foods | 60 | 40 positive (20 catalogue names, 20 variants), 10 synthetic unknowns, 10 underspecified categories |

The labels are committed in `src/test/resources/evaluation/`. Positives specify one FSVO/sample ID; a blank ID means the matcher must **not automatically accept** a food. Either `UNMATCHED` or `NEEDS_CONFIRMATION` is a correct negative decision. Underspecified labels model the product policy of asking for preparation details, not a universal semantic ground truth. The sample catalogue intentionally supplies broad aliases such as `pork`; the official catalogue does not invent these aliases.

The exact baseline scans the **same** catalogue names and aliases, trims whitespace and ignores case. A single exact hit is accepted; multiple hits request confirmation. The fuzzy implementation uses configured defaults: automatic threshold 0.85, minimum threshold 0.6, 3 candidates. The baseline comparison measures algorithm differences on the same data, not a before/after deployment experiment.

## Metric definitions

- Decision accuracy = (correct automatic positives + safely rejected negatives) / all queries.
- Automatic precision = correct automatic positives / all automatically accepted queries.
- Automatic coverage = all automatically accepted queries / all queries. This includes errors; read alongside precision.
- Positive automatic recall = correctly automatically accepted positives / all positives.
- Candidate recall@3 = positives whose expected food is in the returned candidate list / all positives.
- Safe rejection rate = negatives not automatically accepted / all negatives.

Counts, per-category and per-language breakdowns, and every prediction are in JSON. Undefined ratios are `null`, not fabricated 0% or 100%.

## Timing and limits

Three warmup passes precede ten measured passes in a single JVM, using `System.nanoTime`. P50/P95 use nearest-rank percentiles. This is single-thread **in-process matching latency**, excluding HTTP, AI calls, MongoDB, network, and user confirmation. It is not an API load test, an SLA, or a throughput claim. JVM/JIT, hardware, GC, and method order affect results; rerun locally. Exact matching runs before fuzzy matching and the two methods do different amounts of work.

These are small, manually curated development fixtures, with many direct catalogue names and synthetic negative queries. They are **not independent held-out production data**, statistically representative user traffic, or an LLM extraction evaluation. The Swiss challenge set helped identify and fix an underspecified-query bug, so results after that fix are regression measurements. No claim of 100% general accuracy, real-user improvement, live provider validation, cost savings, or business impact is justified.

For a future release, freeze the implementation first, collect a separately annotated held-out set with preparation-specific labels, use realistic near-negative foods, and record the precision/coverage trade-off before tuning thresholds.

## Optional live-stack browser smoke

With the Swiss backend and MongoDB running, start Vite (`cd frontend && npm ci && npm run dev`). In a Python environment with `playwright` installed and Chromium available, run:

```bash
python scripts/smoke_swiss_ui.py --chromium /usr/bin/chromium
python scripts/report_evaluation.py
```

This **creates test logs** under unique `browser-smoke-*` users. Use a disposable database, e.g. `MONGODB_URI=mongodb://localhost:27017/foodlog_swiss_smoke`. The default script URL is `http://127.0.0.1:5173`; use `--url` to override it. The checks use the real browser → Vite proxy → Spring Boot → MongoDB path, with mock extraction. Screenshots and JSON go to `target/evaluation/ui/`. The browser check is optional and has not been added to CI's dependency requirements.
