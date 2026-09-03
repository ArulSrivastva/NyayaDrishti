# Legal Metrology Compliance System (LMCS)

AI-powered scanning system that checks packaged-commodity product labels/labels for compliance with the **Legal Metrology (Packaged Commodities) Rules, 2011** (S.O. 2803(E)).

When a product image is uploaded, the system:
1. **Preprocesses** the image (resize, enhance, deskew, perspective correction) with OpenCV.
2. **Recognizes text** (OCR) via a pluggable engine (RapidOCR by default).
3. **Extracts mandatory declarations** (MRP, net quantity, manufacturer/packer/importer, manufacturing date, consumer care) using multilingual synonym matching.
4. **Detects presence/absence** of each declaration and assigns confidence (AUTO / REVIEW / MANUAL).
5. **Evaluates rules** from a legal knowledge base (`data/rules.json`) derived from the Rules.
6. **Stores** products, inspections, declarations, violations and annotated evidence images.
7. **Outputs** a compliance status (PASS / FAIL / REVIEW), risk level, and a PDF report.

---

## Architecture

| Layer | Path | Responsibility |
|---|---|---|
| P1 UI | `app/static/` | Login + upload + results + history + PDF (vanilla JS) |
| API | `app/api/`, `app/main.py` | FastAPI routes: auth, products, inspections, violations, reports, inspect |
| P2 AI | `app/ai/` | Preprocessing, OCR engine, field extraction, multilingual synonyms, declaration detection, evidence, confidence, VLM stub, orchestration pipeline |
| P3 Rules | `app/rules/`, `data/rules.json` | Rule registry + evaluation engine + risk scoring |
| Data | `app/models.py`, `app/database.py` | SQLAlchemy (SQLite default) |
| Services | `app/services/` | Inspection orchestration + ReportLab PDF generation |

---

## Quick Start

```bash
# 1. Install dependencies (Python 3.10+; tested on 3.14 Windows)
python -m pip install -r requirements.txt

# 2. Optional: configure environment
copy .env.example .env   # Windows
# edit .env as needed

# 3. Seed the default admin account
python -m scripts.seed_admin
#   admin@lmcs.gov.in / admin1234

# 4. Run the server
uvicorn app.main:app --port 8000
```

Open the UI at <http://localhost:8000/> (or view the auto-generated docs at <http://localhost:8000/docs>).

### First run

The first OCR run downloads the RapidOCR ONNX models automatically. To skip OCR entirely (deterministic mock text), set `OCR_ENGINE=mock` in `.env`.

---

## Sample Labels

Generated demo labels allow quick end-to-end testing:

```bash
python -m scripts.generate_sample_labels
```

- `samples/sample_compliant.jpg` — all declarations present.
- `samples/sample_non_compliant.jpg` — missing MRP, consumer care and packer.

Run the pipeline directly (no server):

```bash
python -m scripts.eval_ocr            # OCR accuracy heuristics on the dataset
python -m scripts.generate_sample_labels
```

---

## Tests

```bash
python -m pytest tests/ -q
```

Covers parsers, the rules engine, the AI pipeline (mock OCR), and the full HTTP flow (register → login → upload → inspect → history). Tests run against an isolated SQLite DB and the mock OCR engine via `tests/conftest.py`.

---

## API Overview

Protected endpoints require `Authorization: Bearer <token>` (obtain via `/auth/login`).

| Method | Path | Description |
|---|---|---|
| POST | `/auth/register` | Register inspector/admin |
| POST | `/auth/login` | Login → JWT |
| GET | `/auth/me` | Current user |
| GET | `/health` | Health check |
| POST | `/inspect` | Upload `file` (image) → run inspection (optional `product_name`, `ocr_engine`) |
| GET | `/products` | List products |
| GET | `/products/{id}` | Product detail |
| GET | `/products/{id}/history` | Inspection history for a product |
| GET | `/inspections` | List inspections |
| GET | `/inspections/{id}` | Inspection detail (schemas) |
| GET | `/inspections/{id}/full` | Full inspection (raw product/declarations/violations/evidence) |
| POST | `/inspections/{id}/decision` | Inspector decision (PASS/FAIL/REVIEW/ACCEPT/REJECT) |
| GET | `/inspections/{id}/violations` | Violations of an inspection |
| GET | `/violations/{id}/evidence` | Evidence records of a violation |
| POST | `/reports/{inspection_id}` | Generate PDF report |
| GET | `/reports/{inspection_id}` | Download PDF report |

### Example inspect request (curl)

```bash
TOKEN=$(curl -s -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@lmcs.gov.in","password":"admin1234"}' | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

curl -s -X POST http://localhost:8000/inspect \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@samples/sample_compliant.jpg" \
  -F "product_name=Premium Protein Biscuit"
```

---

## Configuration (`.env`)

| Variable | Default | Notes |
|---|---|---|
| `APP_NAME` | Legal Metrology Compliance System | |
| `DEBUG` | `true` | |
| `DATABASE_URL` | `sqlite:///./lmcs.db` | SQLAlchemy URL |
| `JWT_SECRET` | `change-me-in-production` | **change in production** |
| `JWT_EXPIRE_MINUTES` | `480` | |
| `UPLOAD_DIR` | `./uploads` | |
| `EVIDENCE_DIR` | `./evidence` | annotated evidence crop |
| `REPORT_DIR` | `./reports` | PDF output |
| `RULES_FILE` | `./data/rules.json` | legal knowledge base |
| `OCR_ENGINE` | `rapidocr` | `rapidocr` | `tesseract` | `mock` |
| `OCR_CONFIDENCE_THRESHOLD` | `0.4` | minimum OCR confidence |
| `SCAN_DPI` | `300.0` | assumed print resolution for mm font-size estimation |
| `AUTO_VERDICT_THRESHOLD` | `95.0` | %% overall → AUTO |
| `REVIEW_VERDICT_THRESHOLD` | `80.0` | %% overall → REVIEW |

---

## Rule Coverage (`data/rules.json`)

| Rule | Requirement |
|---|---|
| R6_001 | Name and address of manufacturer |
| R6_002 | Name and address of packer (Rule 10) |
| R6_003 | Name and address of importer (imports) |
| R6_004 | Common/generic name of commodity |
| R6_005 | Net quantity in standard units |
| R6_006 | Month & year of manufacture/pre-packing |
| R6_007 | Retail sale price (MRP) |
| R6_008 | Consumer complaint / customer care contact |
| R6_009 | MRP wording `... incl. of all taxes` style |
| R7_002 | Minimum height of numerals (Tables I/II) and letters (1 mm) in declarations |
| R8_001 | Declarations shall appear on the principal display panel |
| R8_002 | Area around the net quantity declaration free of other printed information |
| R12_001 | Standard unit format (g/ml etc., no forbidden qualifiers) |
| R13_001 | No `dozen`/`score`/`gross` counts |
| R9_001 | Language & legibility (confidence check) |
| R26_001 | Rule 26 exemption for ≤10 g/10 ml packages |

---

## Project layout

```
app/
  ai/            preprocessing, ocr, parsers, fields, declarations, evidence, vlm, pipeline
  api/           auth, products, inspections, violations, reports, inspect
  rules/         registry, engine, units, risk
  schemas/       auth, inspection, common
  services/      inspection_service, report_service
  static/        index.html, app.css, app.js
config.py  database.py  main.py  models.py  security.py  deps.py
data/rules.json
scripts/         seed_admin, eval_ocr, generate_sample_labels
samples/         generated demo label images
tests/           pytest suite (parsers, rules, pipeline, API)
```

## Notes

- **Density/readability (Rule 7, Rule 8)**: estimated character height in mm (via `SCAN_DPI`) is compared with the minimum figures in Table I (weight/volume), Table II (length/area/number / PDP area) and the 1 mm minimum for letters; placement checks verify declarations sit on the principal display panel, that no other printed information intrudes on the area around the net quantity, and that declaration lines are not truncated at the label edge.
- The **confidence → verdict** mapping: AUTO ≥ 95%, REVIEW 80–95%, MANUAL < 80%.
- Evidence images are written to `EVIDENCE_DIR` for every detected or missing declaration and linked to the inspection via the DB.
- PDF reports are generated with ReportLab and stored in `REPORT_DIR`.