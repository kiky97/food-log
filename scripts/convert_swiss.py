#!/usr/bin/env python3
"""Convert the official English FSVO V7.1 XLSX using only Python's standard library."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
SOURCE = 'https://naehrwertdaten.ch/wp-content/uploads/2026/07/Swiss_food_composition_database.xlsx'
FIELDS = ['id', 'name_en', 'name_de', 'name_zh', 'aliases', 'category', 'kcal', 'protein_g',
          'fat_g', 'carb_g', 'fiber_g', 'sugar_g', 'salt_g', 'piece_g', 'slice_g', 'cup_g', 'bowl_g', 'serving_g']
NUTRIENTS = {'kcal': 'Energy, kilocalories (kcal)', 'protein_g': 'Protein (g)',
             'fat_g': 'Fat, total (g)', 'carb_g': 'Carbohydrates, available (g)',
             'fiber_g': 'Dietary fibres (g)', 'sugar_g': 'Sugars (g)', 'salt_g': 'Salt (NaCl) (g)'}


def rows(path):
    with ZipFile(path) as z:
        strings = [''.join(si.itertext()) for si in ET.fromstring(z.read('xl/sharedStrings.xml')).findall('m:si', NS)]
        root = ET.fromstring(z.read('xl/worksheets/sheet1.xml'))
        for row in root.findall('m:sheetData/m:row', NS):
            cells = {}
            for c in row:
                v = c.find('m:v', NS)
                if v is not None:
                    cells[''.join(filter(str.isalpha, c.attrib['r']))] = strings[int(v.text)] if c.get('t') == 's' else v.text
                elif c.get('t') == 'inlineStr':
                    cells[''.join(filter(str.isalpha, c.attrib['r']))] = ''.join(c.find('m:is', NS).itertext())
            yield cells


def convert(path, output):
    source_rows = iter(rows(path))
    title = next(source_rows).get('A', '')
    if 'Generic foods V 7.1' not in title:
        raise ValueError('Expected official Generic foods V 7.1 sheet; review mapping for a new version')
    header = next(r for r in source_rows if r.get('A') == 'ID')
    columns = {v: k for k, v in header.items()}
    required = ['ID', 'Name', 'Synonyms', 'Category', 'Matrix unit', *NUTRIENTS.values()]
    if any(k not in columns for k in required):
        raise ValueError('Unexpected workbook schema')
    accepted, excluded, seen = [], [], set()
    for r in source_rows:
        get = lambda key: r.get(columns[key], '').strip()
        if not get('ID'):
            continue
        fid = 'CH-' + get('ID')
        if fid in seen:
            raise ValueError('Duplicate source ID: ' + fid)
        seen.add(fid)
        reasons, values = [], {}
        if get('Matrix unit') != 'per 100g edible portion':
            reasons.append('unsupported matrix unit: ' + get('Matrix unit'))
        if not get('Name'):
            reasons.append('missing name')
        for field, label in NUTRIENTS.items():
            raw = get(label)
            try:
                value = float(raw)
                if not math.isfinite(value) or value < 0:
                    raise ValueError()
                values[field] = format(value, '.12g')
            except ValueError:
                reasons.append(field + ': ' + (raw or 'missing'))
        if reasons:
            excluded.append({'id': fid, 'name': get('Name'), 'reasons': reasons})
            continue
        accepted.append(dict(id=fid, name_en=get('Name'), aliases=get('Synonyms'),
                             category=get('Category'), **values))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=FIELDS, restval='')
        writer.writeheader()
        writer.writerows(accepted)
    report = {'source': SOURCE, 'attribution': 'Federal Food Safety and Veterinary Office FSVO, Swiss Food Composition Database, V7.1 (2026)',
              'terms': 'https://www.naehrwertdaten.ch/en/downloads/',
              'source_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
              'csv_sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
              'scope': 'Generic Foods sheet only; English names; no invented translations or portion weights',
              'source_rows': len(seen), 'imported_rows': len(accepted), 'excluded_rows': len(excluded),
              'missing_policy': 'Exclude rows with unknown, non-numeric or negative values for any of the seven required nutrients; never replace unknown with zero',
              'excluded': excluded}
    output.with_suffix('.provenance.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Source: {len(seen)}; imported: {len(accepted)}; excluded: {len(excluded)}')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('xlsx', type=Path)
    parser.add_argument('--output', type=Path, default=Path('src/main/resources/data/foods_swiss_v7_1.csv'))
    args = parser.parse_args()
    convert(args.xlsx, args.output)
