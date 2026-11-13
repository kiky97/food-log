# Swiss official food composition data

Source: Federal Food Safety and Veterinary Office (FSVO), **Swiss Food Composition Database V7.1 (1 July 2026)**.

- [Official download and reuse terms](https://www.naehrwertdaten.ch/en/downloads/)
- [Original English workbook](https://naehrwertdaten.ch/wp-content/uploads/2026/07/Swiss_food_composition_database.xlsx)
- [Version history](https://www.naehrwertdaten.ch/en/versions-and-updates/)

The official download page permits commercial and scientific reuse with acknowledgment. The project's MIT license applies to code, not a relicensing of FSVO data. Keep this attribution when redistributing the derived CSV.

## Reproduce the conversion

Python 3.10+; no third-party Python packages required:

```bash
curl -L --fail 'https://naehrwertdaten.ch/wp-content/uploads/2026/07/Swiss_food_composition_database.xlsx' -o /tmp/swiss-foods.xlsx
python scripts/convert_swiss.py /tmp/swiss-foods.xlsx
python -m unittest discover -s scripts -p 'test_*.py'
```

The converter reads the **Generic Foods** sheet only: 1,216 source records, of which **1,161** have numeric, nonnegative values for all seven nutrients used by this application. **55 records are excluded**, with IDs and field-level reasons in `src/main/resources/data/foods_swiss_v7_1.provenance.json`. Branded foods are outside this import's scope. These counts describe this workbook, not the full database's published food count.

Mapping is by explicit column headers; schema or version changes fail rather than silently selecting the wrong columns. Unknown values (`n.d.`, blank, inequalities, etc.) are never changed to zero. The converter rejects non-100g matrices. Native `CH-` prefixed FSVO IDs prevent collisions with `S` sample IDs. Source and CSV SHA-256 checksums are recorded in the provenance file.

English names, source synonyms, category, kcal, protein, fat, available carbohydrates, fibre, sugar, and salt per 100g are retained. Numeric output uses 12 significant digits to remove Excel binary floating point noise. No Chinese/German translation, portion weight, or density is invented. Density and micronutrients are not imported into the current domain model.

## Run the official catalogue

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=swiss
```

The `swiss` profile seeds the bundled official CSV into an **empty** `foodlog_swiss` database. The default profile still uses 45 approximate sample foods in `foodlog`. `MONGODB_URI` overrides the database URI in either profile; use an empty dedicated database when switching catalogues. Seeding never replaces an existing catalogue.

```bash
curl -X POST http://localhost:8080/api/v1/logs \
  -H 'Content-Type: application/json' \
  -d '{"userId":"swiss-demo","text":"200g fresh apple for breakfast"}'
```

Expected: `CH-378`, 200g, **104 kcal**, **23.2g available carbohydrates**. This path is covered by `SwissCatalogIntegrationTest` against Testcontainers MongoDB with the mock extractor. It does not test live AI provider quality.

Use grams in demonstrations: official portion weights are unavailable, so count-based units use existing estimated defaults. Volume conversions still assume 1g/ml (see README limitations). The official catalogue has more preparations and variants than the sample data, so users may need to confirm a match; a broad term such as `pork` should not silently select `Pork lard`.
