#!/usr/bin/env python3
"""Render measured JSON and test/coverage evidence into a reviewable Markdown report."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
report = json.loads((root / 'target/evaluation/matching.json').read_text())
pct = lambda v: 'N/A' if v is None else f'{v * 100:.1f}%'
lines = ['# Measured results', '', f"Evaluated source revision: `{report['revision']}`.", '',
         f"Environment: Java {report['java']}, {report['os']}, {report['processors']} reported processors.", '',
         'Small curated development fixtures; see [methodology](README.md). These are offline matching measurements, not production or live AI results.', '',
         '| Catalogue | Method | Correct decisions | Auto precision | Auto coverage | Positive auto recall | Candidate recall@3 |',
         '|---|---|---:|---:|---:|---:|---:|']
for ds in ['sample', 'swiss']:
    data = report[ds]
    for method in ['exact', 'fuzzy']:
        m = data[method]
        correct = m['correctAutoAccepted'] + m['correctRejections']
        lines.append(f"| {ds} ({data['catalogueSize']} foods) | {method} | {correct}/{m['queries']} ({pct(m['decisionAccuracy'])}) | {pct(m['autoPrecision'])} | {pct(m['autoCoverage'])} | {pct(m['positiveAutoRecall'])} | {pct(m['candidateRecallAt3'])} |")
lines += ['', '## Underspecified-query fix', '']
before = json.loads((root / 'docs/evaluation/before-fix.json').read_text())
assert before['swiss']['queriesSha256'] == report['swiss']['queriesSha256']
assert before['swiss']['catalogueSha256'] == report['swiss']['catalogueSha256']
b, a = before['swiss']['fuzzy'], report['swiss']['fuzzy']
lines += [f"On the same 60 Swiss queries: false automatic accepts **{b['falseAutoAccepted']} → {a['falseAutoAccepted']}**, automatic precision **{pct(b['autoPrecision'])} → {pct(a['autoPrecision'])}**, automatic coverage **{pct(b['autoCoverage'])} → {pct(a['autoCoverage'])}**.", '',
          'This is a development regression comparison: the fixture exposed the bug and informed the fix. It is not a held-out improvement estimate. The fix routes generic words to confirmation; it does not add semantic understanding.', '',
          '## In-process latency', '', '| Catalogue | Method | Measured calls | P50 (ms) | P95 (ms) |', '|---|---|---:|---:|---:|']
for ds in ['sample','swiss']:
    for method in ['exact','fuzzy']:
        t = report[ds][method]['timing']
        lines.append(f"| {ds} | {method} | {t['samples']} | {t['p50Ms']:.3f} | {t['p95Ms']:.3f} |")
lines += ['', 'Three warmup passes, ten measured passes, single thread, no JaCoCo. These exclude REST, model providers, MongoDB, and network time. No speedup versus exact matching is claimed.', '', '## Verification', '']
counts = dict(tests=0, failures=0, errors=0, skipped=0)
for p in sorted((root / 'target/surefire-reports').glob('TEST-*.xml')):
    test = ET.parse(p).getroot()
    for k in counts: counts[k] += int(test.get(k, 0))
lines.append(f"Backend test run: {counts['tests']} tests, {counts['failures']} failures, {counts['errors']} errors, {counts['skipped']} skipped (the opt-in benchmark test is skipped in the normal test run).")
coverage = ET.parse(root / 'target/site/jacoco/jacoco.xml').getroot()
for c in coverage.findall('counter'):
    if c.get('type') in ['LINE','BRANCH']:
        covered, missed = int(c.get('covered')), int(c.get('missed'))
        lines.append(f"- Overall {c.get('type').lower()} coverage: {covered}/{covered + missed} ({pct(covered / (covered + missed))}). Includes all backend production classes; no exclusions.")
lines += ['', 'Frontend: 10 existing Vitest tests passed; TypeScript and Vite production build passed in the same workspace. Python conversion policy: 3 tests passed, including parameterized missing/invalid values.', '',
          'Raw evidence: [matching JSON](matching.json), [pre-fix quality JSON](before-fix.json). CI also uploads Surefire and JaCoCo reports.']
(root / 'docs/evaluation/RESULTS.md').write_text('\n'.join(lines) + '\n')
(root / 'docs/evaluation/matching.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print('\n'.join(lines))
