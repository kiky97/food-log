#!/usr/bin/env python3
"""Optional live-stack browser smoke test. Requires Python playwright and Chromium.
Run the Swiss Spring Boot profile, MongoDB and Vite dev server before this script.
This writes test logs to a dedicated browser-smoke user; use a disposable database.
"""
import argparse
import json
import re
import time
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--url', default='http://127.0.0.1:5173')
parser.add_argument('--chromium', default='/usr/bin/chromium')
parser.add_argument('--output', type=Path, default=Path('target/evaluation/ui'))
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
results = []
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=args.chromium, headless=True, args=['--no-sandbox'])
    for name, viewport in [('desktop', {'width': 1440, 'height': 1000}), ('mobile', {'width': 390, 'height': 844})]:
        context = browser.new_context(viewport=viewport)
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        user = f'browser-smoke-{name}-{time.time_ns()}'
        page.add_init_script('localStorage.setItem("foodlog.userId", ' + json.dumps(user) + ')')
        page.goto(args.url)
        expect(page.locator('.energy-value')).to_have_text('0')
        page.get_by_label('What did you eat?').fill('200g fresh apple for breakfast')
        page.get_by_role('button', name='Log meal', exact=True).click()
        expect(page.locator('.item-name')).to_have_text('Apple, fresh')
        expect(page.locator('.energy-value')).to_have_text('104')
        results.append({'viewport': name, 'flow': 'official apple log and daily total', 'passed': True})
        page.get_by_label('What did you eat?').fill('100g pork for lunch')
        page.get_by_role('button', name='Log meal', exact=True).click()
        expect(page.get_by_text('Which food did you mean?', exact=True)).to_be_visible()
        expect(page.get_by_role('status')).to_contain_text('1 item not counted until confirmed')
        expect(page.locator('.energy-value')).to_have_text('104')
        results.append({'viewport': name, 'flow': 'underspecified food excluded from nutrients', 'passed': True})
        page.get_by_role('searchbox', name='Search foods').fill('Pork, minced, raw')
        page.get_by_role('button', name=re.compile(r'^Pork, minced, raw')).click()
        expect(page.locator('.energy-value')).to_have_text('263')  # 104 + official CH-7 159kcal / 100g
        expect(page.get_by_role('status')).to_have_count(0)
        page.reload()
        expect(page.locator('.energy-value')).to_have_text('263')
        expect(page.locator('article.log')).to_have_count(2)
        assert not errors, errors
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'), 'horizontal overflow'
        results.append({'viewport': name, 'flow': 'catalogue search, confirmation and persistence after reload', 'passed': True})
        page.screenshot(path=str(args.output / f'{name}.png'), full_page=True)
        context.close()
    browser.close()
(args.output / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
print(json.dumps(results, indent=2))
