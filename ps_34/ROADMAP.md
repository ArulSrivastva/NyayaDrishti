# AI + Backend + Compliance — Development Roadmap

## Project Overview

This roadmap covers the complete **AI, backend, compliance, database, evidence, history, and API pipeline** for the product-package inspection system.

### Final System

```text
                    ┌──────────────┐
                    │   P1 - UI    │
                    └──────┬───────┘
                           │
                           ↓
                    ┌──────────────┐
                    │   FastAPI    │
                    │    Backend   │
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              ↓                         ↓
       ┌──────────────┐          ┌──────────────┐
       │ P2 - AI/ML   │          │ P3 - Rules   │
       │ OCR + VLM    │          │ DB + Legal   │
       └──────┬───────┘          └──────┬───────┘
              │                         │
              └────────────┬────────────┘
                           ↓
                   ┌───────────────┐
                   │ Final Decision │
                   └───────┬───────┘
                           ↓
                    ┌──────────────┐
                    │   P1 - UI    │
                    └──────────────┘
```

---

# PERSON 2 — AI + Backend + Compliance

## Responsibility

Person 2 owns the **AI intelligence pipeline**:

```text
Image
  ↓
Preprocessing
  ↓
OCR
  ↓
Field Extraction
  ↓
VLM
  ↓
Multilingual Processing
  ↓
Declaration Detection
  ↓
Evidence Generation
  ↓
Confidence
  ↓
Structured JSON
  ↓
FastAPI
```

---

# PHASE 1 — Core AI Pipeline

## Goal

Get the basic pipeline working:

```text
IMAGE → OCR → FIELD EXTRACTION → JSON → API
```

### Step 1 — Image Preprocessing

Implement preprocessing using OpenCV.

Tasks:

- [ ] Image resizing
- [ ] Noise removal
- [ ] Grayscale conversion
- [ ] Contrast enhancement
- [ ] Sharpening
- [ ] Perspective correction
- [ ] Rotation correction
- [ ] Cropping
- [ ] Optional thresholding

Pipeline:

```text
Input Image
     ↓
Resize
     ↓
Denoise
     ↓
Contrast Enhancement
     ↓
Perspective Correction
     ↓
OCR-ready Image
```

### Deliverable

A preprocessing module:

```text
preprocessing/
├── resize.py
├── enhance.py
├── rotate.py
├── perspective.py
└── pipeline.py
```

---

# Step 2 — OCR

Integrate **PaddleOCR**.

Extract:

- Text
- Bounding boxes
- OCR confidence
- Language information where available

Expected output:

```json
{
  "text": "MRP ₹100",
  "confidence": 0.97,
  "bbox": [120, 240, 380, 290]
}
```

### Deliverable

OCR module:

```text
ocr/
├── paddleocr_engine.py
├── postprocess.py
└── detector.py
```

---

# Step 3 — Field Extraction

Initially detect the following fields:

| Field | Example |
|---|---|
| MRP | ₹100 |
| Net Quantity | 500 g |
| Manufacturer | ABC Foods Pvt Ltd |
| Packer | ABC Foods Pvt Ltd |
| Importer | XYZ Imports |
| Manufacturing/Packing Date | 08/2026 |
| Consumer Care | 1800-XXX-XXXX |

Convert OCR output into structured data.

### Example

```json
{
  "mrp": {
    "value": "₹100",
    "confidence": 0.97
  },
  "net_quantity": {
    "value": "500 g",
    "confidence": 0.94
  },
  "manufacturer": {
    "value": "ABC Foods Pvt Ltd",
    "confidence": 0.91
  }
}
```

---

# Step 4 — Common JSON Contract

Person 2 and Person 3 must agree on the JSON format before integration.

Recommended structure:

```json
{
  "product": {
    "mrp": null,
    "net_quantity": null,
    "manufacturer": null,
    "packer": null,
    "importer": null,
    "manufacturing_date": null,
    "packing_date": null,
    "consumer_care": null
  },
  "declarations": [],
  "violations": [],
  "evidence": [],
  "confidence": {},
  "metadata": {
    "language": "en",
    "processing_time": 0
  }
}
```

### Important Rule

**Do not change this schema randomly after integration begins.**

Any schema changes should be discussed between P1, P2 and P3.

---

# Step 5 — Basic API

Create an initial API endpoint:

```text
POST /inspect
```

Input:

```text
Image
```

Output:

```json
{
  "product": {},
  "declarations": [],
  "confidence": {}
}
```

### Phase 1 Deliverable

At the end of Phase 1:

```text
IMAGE
  ↓
OpenCV
  ↓
PaddleOCR
  ↓
Field Extraction
  ↓
Structured JSON
  ↓
FastAPI
```

### Phase 1 Checkpoint

- [ ] OCR working
- [ ] Bounding boxes available
- [ ] Required fields extracted
- [ ] JSON standardized
- [ ] API working
- [ ] P1 can consume JSON
- [ ] P3 can consume JSON

---

# PHASE 2 — AI Innovation + Compliance

## Goal

Add the differentiating AI capabilities.

---

# Step 6 — VLM Integration

Integrate a lightweight VLM such as **Florence-2** where appropriate.

Use it for:

- [ ] Declaration detection
- [ ] Spatial understanding
- [ ] Region identification
- [ ] Bounding-box refinement
- [ ] Difficult OCR cases
- [ ] Context-aware field extraction

Pipeline:

```text
Image
 ↓
OCR
 ↓
VLM
 ↓
Combined Understanding
 ↓
Structured Information
```

### Important

Do not make the VLM responsible for everything.

Use:

```text
OCR → Text extraction
VLM → Visual/spatial understanding
Rule Engine → Legal decision
```

This separation makes the system easier to debug.

---

# Step 7 — Multilingual Processing

Initial languages:

1. English
2. Hindi
3. Tamil

Pipeline:

```text
English ─┐
Hindi ───┼──→ Normalization → Common Fields
Tamil ───┘
```

Example:

```text
MRP
Maximum Retail Price
अधिकतम खुदरा मूल्य
அதிகபட்ச சில்லறை விலை
```

All should map to:

```json
{
  "field": "mrp"
}
```

### Tasks

- [ ] Language identification
- [ ] Multilingual OCR testing
- [ ] Field-name normalization
- [ ] Synonym dictionary
- [ ] Hindi normalization
- [ ] Tamil normalization
- [ ] Mixed-language testing

---

# Step 8 — Declaration Detection

Detect whether required declarations are present.

Example:

```text
Image
 ↓
OCR + VLM
 ↓
Declaration Detection
 ↓
Required Declaration
 ↓
Present / Missing
```

Example output:

```json
{
  "declaration": "manufacturer",
  "present": true,
  "confidence": 0.96,
  "bbox": [100, 400, 600, 470]
}
```

---

# Step 9 — Evidence Generation

Every detected violation should have evidence.

Structure:

```text
Original Image
      ↓
Detected Region
      ↓
Bounding Box
      ↓
Violation
      ↓
Confidence
```

Example:

```json
{
  "violation": "MRP declaration issue",
  "bbox": [100, 200, 450, 270],
  "confidence": 0.96,
  "evidence_image": "evidence/inspection_001_mrp.jpg"
}
```

### Evidence Tasks

- [ ] Save original image
- [ ] Store bounding box
- [ ] Crop detected region
- [ ] Annotate violation
- [ ] Store evidence image
- [ ] Link evidence to inspection
- [ ] Store confidence

---

# Step 10 — Confidence System

Use the initial confidence policy:

```text
95–100% → AUTO
80–95%  → REVIEW
<80%    → MANUAL
```

Represent it explicitly:

```json
{
  "confidence": 0.96,
  "decision": "AUTO"
}
```

### Important

Confidence should not automatically mean legal validity.

```text
AI Confidence
      ↓
Recommendation
      ↓
Rule Engine
      ↓
Final Decision
```

---

# Step 11 — AI Output Validation

Before sending AI results to the backend:

```text
AI Output
   ↓
Schema Validation
   ↓
Missing Field Check
   ↓
Confidence Check
   ↓
Normalized JSON
```

Reject malformed outputs.

Example:

```text
Missing required key
        ↓
Validation Error
        ↓
Do NOT send invalid result to database
```

---

# PHASE 2 CHECKPOINT

- [ ] VLM integrated
- [ ] Declaration detection working
- [ ] Bounding boxes working
- [ ] English supported
- [ ] Hindi supported
- [ ] Tamil supported
- [ ] Evidence images generated
- [ ] Confidence generated
- [ ] AI JSON validated
- [ ] P3 can consume AI results

---

# PERSON 3 — BACKEND + LEGAL METROLOGY + DATABASE

## Responsibility

Person 3 owns the **decision, persistence and compliance layer**.

```text
AI Result
    ↓
Backend
    ↓
Database
    ↓
Rule Engine
    ↓
Risk
    ↓
Human Verification
    ↓
Report
```

---

# PHASE 1 — Backend Foundation

## Step 1 — FastAPI Setup

Create backend using FastAPI.

Recommended structure:

```text
backend/
├── main.py
├── api/
│   ├── auth.py
│   ├── products.py
│   ├── inspections.py
│   ├── violations.py
│   └── reports.py
├── models/
├── schemas/
├── services/
├── rules/
├── database/
├── evidence/
└── tests/
```

---

# Step 2 — Authentication

Implement:

- [ ] User registration/login
- [ ] Authentication
- [ ] Password hashing
- [ ] JWT/session management
- [ ] Inspector role
- [ ] Admin role

Basic flow:

```text
User
 ↓
Login
 ↓
Authentication
 ↓
Access Token
 ↓
Protected APIs
```

---

# Step 3 — Database

Recommended relational structure:

```text
Product
   │
   ├── Inspection
   │       │
   │       ├── Declaration
   │       ├── Violation
   │       └── Evidence
   │
   └── Inspection
```

### Core Models

#### Product

```text
Product
├── id
├── name
├── manufacturer
├── packer
├── importer
├── net_quantity
├── mrp
└── created_at
```

#### Inspection

```text
Inspection
├── id
├── product_id
├── inspector_id
├── image
├── status
├── risk_level
├── created_at
└── completed_at
```

#### Declaration

```text
Declaration
├── id
├── inspection_id
├── type
├── value
├── confidence
├── bbox
└── present
```

#### Violation

```text
Violation
├── id
├── inspection_id
├── type
├── description
├── severity
├── confidence
└── status
```

#### Evidence

```text
Evidence
├── id
├── violation_id
├── image_path
├── bbox
├── confidence
└── created_at
```

---

# Step 4 — CRUD APIs

Create basic endpoints.

```text
POST   /products
GET    /products
GET    /products/{id}
PUT    /products/{id}
DELETE /products/{id}
```

Inspections:

```text
POST /inspections
GET  /inspections
GET  /inspections/{id}
```

Violations:

```text
GET /inspections/{id}/violations
```

Evidence:

```text
GET /violations/{id}/evidence
```

---

# Step 5 — Mock AI Integration

P3 should NOT wait for P2.

Create mock AI JSON:

```json
{
  "product": {
    "mrp": "₹100",
    "net_quantity": "500 g"
  },
  "declarations": [
    {
      "type": "manufacturer",
      "present": true,
      "confidence": 0.97
    }
  ]
}
```

Use it to develop:

- Database
- API
- Rule engine
- Reports
- History
- Risk scoring

---

# PHASE 2 — COMPLIANCE INTELLIGENCE

# Step 6 — Legal Metrology Rule Engine

Keep legal rules **separate from the AI model**.

Architecture:

```text
P2 AI Result
      ↓
Validated JSON
      ↓
Legal Metrology Rule Engine
      ↓
PASS / FAIL / REVIEW
```

Create structured rules for:

- [ ] Mandatory declarations
- [ ] MRP
- [ ] Net quantity
- [ ] Manufacturer
- [ ] Packer
- [ ] Importer
- [ ] Consumer care
- [ ] Date declarations
- [ ] Applicable measurement/format requirements

### Rule Structure

Example:

```json
{
  "rule_id": "MRP_001",
  "field": "mrp",
  "required": true,
  "condition": "present",
  "failure": "MRP declaration missing"
}
```

---

# Step 7 — Compliance Decision

Each inspection should produce:

```text
PASS
FAIL
REVIEW
```

Example:

```json
{
  "rule_id": "MRP_001",
  "result": "FAIL",
  "reason": "Required MRP declaration not detected"
}
```

### Important Separation

```text
AI says:
"MRP not detected"

Rule Engine says:
"Inspection FAIL because MRP declaration is required."
```

This makes the legal logic auditable.

---

# Step 8 — Human Verification

Confidence determines the recommended workflow:

```text
≥95%
 ↓
AUTO

80–95%
 ↓
INSPECTOR REVIEW

<80%
 ↓
MANUAL VERIFICATION
```

Store:

```text
AI recommendation
Inspector decision
Inspector ID
Timestamp
Reason
```

Example:

```json
{
  "ai_result": "FAIL",
  "ai_confidence": 0.91,
  "inspector_decision": "PASS",
  "inspector_id": 42,
  "decision_time": "2026-09-01T10:30:00"
}
```

---

# Step 9 — Product History

Maintain complete inspection history.

```text
Product
 │
 ├── Inspection 1
 │    ├── Violations
 │    └── Evidence
 │
 ├── Inspection 2
 │    ├── Violations
 │    └── Evidence
 │
 └── Inspection 3
      ├── Violations
      └── Evidence
```

API:

```text
GET /products/{id}/history
```

Return:

```json
{
  "product_id": 123,
  "total_inspections": 4,
  "violations": 5,
  "history": []
}
```

---

# Step 10 — Repeat Non-Compliance Detection

Calculate a simple risk level.

```text
0 violations
    ↓
LOW

1–2 violations
    ↓
MEDIUM

Repeated violations
    ↓
HIGH
```

Example:

```text
Repeated MRP violations detected
across 4 inspections.
```

Recommended logic:

```text
No violations → LOW

1–2 violations → MEDIUM

Same/similar violation
appearing repeatedly → HIGH
```

---

# Step 11 — Risk Score

Create a simple risk calculation.

Example:

```text
Base Risk
    +
Violation Count
    +
Repeated Violations
    +
Severity
    ↓
Final Risk
```

Keep the first version simple.

Do not build an unnecessarily complicated ML risk model.

---

# Step 12 — PDF Reports

Generate an official-looking inspection report.

Report structure:

```text
INSPECTION REPORT

1. Inspection Details
       ↓
2. Product Information
       ↓
3. Detected Declarations
       ↓
4. Violations
       ↓
5. Evidence Images
       ↓
6. AI Confidence
       ↓
7. Inspector Decision
       ↓
8. Risk Level
```

PDF should contain:

- Inspection ID
- Date/time
- Product information
- Inspector
- Detected declarations
- Violations
- Evidence images
- Confidence
- Rule results
- Inspector decision
- Risk level

---

# PHASE 3 — FULL INTEGRATION

## Goal

Connect P1 + P2 + P3 into one working system.

---

# Step 1 — AI → API

P2 connects the AI pipeline to FastAPI.

```text
Image
 ↓
P2 AI
 ↓
Structured JSON
 ↓
FastAPI
```

---

# Step 2 — API → Database

P3 receives the JSON.

```text
FastAPI
 ↓
Validation
 ↓
Database
```

Store:

- Product
- Inspection
- Declarations
- Violations
- Evidence
- Confidence
- Inspector decision

---

# Step 3 — Rule Engine Integration

```text
P2 AI
 ↓
FastAPI
 ↓
P3 Validation
 ↓
Rule Engine
 ↓
PASS / FAIL / REVIEW
```

---

# Step 4 — Evidence Integration

```text
P2
 ↓
Evidence Image
 ↓
FastAPI
 ↓
Storage
 ↓
Database Reference
 ↓
P1
```

P1 should be able to display:

- Original image
- Detected region
- Bounding box
- Violation
- Confidence

---

# Step 5 — Product History Integration

P1 should receive:

```text
Product
 ↓
Inspection History
 ↓
Violations
 ↓
Risk
 ↓
Repeated Violations
```

Example UI information:

```text
Product: ABC Biscuit

Inspections: 4

Risk: HIGH

Repeated violations:
• MRP — 3 times
• Consumer care — 2 times
```

---

# Step 6 — Final JSON to P1

Recommended final response:

```json
{
  "inspection_id": 101,
  "product": {},
  "declarations": [],
  "violations": [],
  "evidence": [],
  "risk": {
    "level": "HIGH",
    "reason": "Repeated violations"
  },
  "confidence": {},
  "compliance": {
    "status": "FAIL"
  },
  "history": {},
  "inspector": {
    "decision": "PENDING"
  }
}
```

---

# TESTING PHASE

## Step 1 — Dataset

Collect:

```text
20–50 real package images
```

Include:

- [ ] Different brands
- [ ] Different package sizes
- [ ] Different layouts
- [ ] English packaging
- [ ] Hindi packaging
- [ ] Tamil packaging
- [ ] Mixed-language packaging
- [ ] Blurry images
- [ ] Rotated images
- [ ] Low-light images
- [ ] Small text
- [ ] Damaged packaging

---

# Step 2 — OCR Testing

Measure:

```text
OCR Accuracy
Field Extraction Accuracy
Bounding Box Accuracy
```

Test individually:

```text
MRP
Net Quantity
Manufacturer
Packer
Importer
Dates
Consumer Care
```

---

# Step 3 — Field Extraction Testing

Create a test table:

| Field | Correct | Incorrect | Accuracy |
|---|---:|---:|---:|
| MRP | | | |
| Net Quantity | | | |
| Manufacturer | | | |
| Packer | | | |
| Importer | | | |
| Date | | | |
| Consumer Care | | | |

---

# Step 4 — Compliance Testing

Create known test cases.

### Case 1

```text
All mandatory declarations present

Expected:
PASS
```

### Case 2

```text
MRP missing

Expected:
FAIL
```

### Case 3

```text
AI confidence = 87%

Expected:
REVIEW
```

### Case 4

```text
AI confidence = 65%

Expected:
MANUAL
```

### Case 5

```text
Same violation repeated across inspections

Expected:
HIGH RISK
```

---

# Step 5 — API Testing

Test:

```text
POST /inspect
POST /products
GET /products/{id}
GET /products/{id}/history
GET /inspections/{id}
GET /inspections/{id}/violations
GET /violations/{id}/evidence
POST /inspections/{id}/decision
GET /reports/{id}
```

---

# INTEGRATION CHECKPOINTS

## Checkpoint 1 — AI Ready

```text
Image
 ↓
OCR
 ↓
Fields
 ↓
JSON
```

Requirements:

- [ ] P2 complete
- [ ] JSON schema frozen
- [ ] P3 can use mock/real JSON

---

## Checkpoint 2 — Backend Ready

```text
API
 ↓
Database
 ↓
CRUD
```

Requirements:

- [ ] FastAPI working
- [ ] Database working
- [ ] Authentication working
- [ ] Models created
- [ ] CRUD APIs working

---

## Checkpoint 3 — Compliance Ready

```text
AI
 ↓
Rule Engine
 ↓
PASS/FAIL/REVIEW
```

Requirements:

- [ ] Rules implemented
- [ ] Confidence workflow implemented
- [ ] Human verification implemented
- [ ] Risk calculation implemented

---

## Checkpoint 4 — Evidence Ready

```text
Violation
 ↓
Bounding Box
 ↓
Evidence Image
 ↓
Database
```

Requirements:

- [ ] Evidence generated
- [ ] Evidence stored
- [ ] Evidence linked to violation
- [ ] P1 can display evidence

---

## Checkpoint 5 — History Ready

```text
Product
 ↓
Multiple Inspections
 ↓
Violation History
 ↓
Risk
```

Requirements:

- [ ] Product history working
- [ ] Repeat violations detected
- [ ] Risk level calculated

---

## Checkpoint 6 — Final Demo Ready

Complete pipeline:

```text
                    PHOTO
                      ↓
                OpenCV Processing
                      ↓
                 OCR + VLM
                      ↓
             Multilingual Processing
                      ↓
              Field Extraction
                      ↓
            Declaration Detection
                      ↓
                  Evidence
                      ↓
                 Confidence
                      ↓
                Rule Engine
                      ↓
              PASS / FAIL / REVIEW
                      ↓
                Human Decision
                      ↓
                  Database
                      ↓
                Product History
                      ↓
                  Risk Score
                      ↓
                    API
                      ↓
                   P1 UI
```

---

# RECOMMENDED DEVELOPMENT ORDER

Do not build everything simultaneously.

## Week 1 — Foundation

### P2

- [ ] OpenCV preprocessing
- [ ] PaddleOCR
- [ ] Basic field extraction

### P3

- [ ] FastAPI
- [ ] Database
- [ ] Models
- [ ] Basic APIs
- [ ] Mock AI JSON

### Integration

- [ ] Agree on JSON schema

---

# Week 2 — Core System

### P2

- [ ] Improve field extraction
- [ ] Bounding boxes
- [ ] Confidence calculation
- [ ] API integration

### P3

- [ ] CRUD
- [ ] Inspection workflow
- [ ] Declaration model
- [ ] Violation model
- [ ] Evidence model

### Integration

```text
Image → AI → API → DB
```

---

# Week 3 — Innovation

### P2

- [ ] VLM
- [ ] Declaration detection
- [ ] Spatial understanding
- [ ] Hindi
- [ ] Tamil
- [ ] Evidence generation

### P3

- [ ] Rule engine
- [ ] PASS/FAIL/REVIEW
- [ ] Human verification
- [ ] Risk calculation

---

# Week 4 — History + Reports

### P2

- [ ] Improve difficult OCR cases
- [ ] Confidence tuning
- [ ] Multilingual improvements

### P3

- [ ] Product history
- [ ] Repeat violations
- [ ] Risk levels
- [ ] PDF reports

---

# Week 5 — Integration + Testing

- [ ] Connect P1
- [ ] End-to-end API
- [ ] Test 20–50 images
- [ ] Fix OCR failures
- [ ] Fix extraction errors
- [ ] Fix compliance errors
- [ ] Tune confidence thresholds
- [ ] Test database history
- [ ] Test PDF generation

---

# Week 6 — Final Demo Preparation

## Final Checklist

### AI

- [ ] OCR stable
- [ ] VLM stable
- [ ] Multilingual support
- [ ] Field extraction
- [ ] Declaration detection
- [ ] Evidence generation
- [ ] Confidence scores

### Backend

- [ ] FastAPI stable
- [ ] Authentication
- [ ] API validation
- [ ] Database stable
- [ ] Error handling

### Compliance

- [ ] Rules implemented
- [ ] PASS/FAIL/REVIEW
- [ ] Human verification
- [ ] Risk scoring
- [ ] Repeat violation detection

### Reports

- [ ] PDF generation
- [ ] Evidence included
- [ ] Inspector decision included
- [ ] Confidence included

### Integration

- [ ] P1 connected
- [ ] P2 connected
- [ ] P3 connected
- [ ] End-to-end test passed

---

# Definition of Done

The project is considered complete when the following works with a real package image:

```text
USER TAKES PHOTO
       ↓
     P1 UI
       ↓
    FastAPI
       ↓
      P2 AI
       ↓
   OCR + VLM
       ↓
Field Extraction
       ↓
Declaration Detection
       ↓
   Evidence
       ↓
  Confidence
       ↓
    P3 Rules
       ↓
PASS / FAIL / REVIEW
       ↓
 Human Verification
       ↓
    Database
       ↓
 Product History
       ↓
   Risk Detection
       ↓
   PDF Report
       ↓
     P1 UI
```

### Final Demonstration Should Show

1. Inspector uploads/takes a package photo.
2. OCR extracts the declarations.
3. AI identifies fields and their locations.
4. System detects missing/incorrect declarations.
5. Evidence is highlighted on the original image.
6. Confidence is displayed.
7. Legal rules produce PASS/FAIL/REVIEW.
8. Inspector can accept/reject the AI recommendation.
9. Inspection is saved.
10. Previous inspections are displayed.
11. Repeated violations increase the risk level.
12. A complete PDF inspection report is generated.

---

# Team Ownership

| Component | P1 | P2 | P3 |
|---|:---:|:---:|:---:|
| UI | ✅ | | |
| Image Upload | ✅ | | |
| OpenCV | | ✅ | |
| PaddleOCR | | ✅ | |
| VLM | | ✅ | |
| Field Extraction | | ✅ | |
| Multilingual AI | | ✅ | |
| Evidence Generation | | ✅ | |
| FastAPI | | ✅ | ✅ |
| Authentication | | | ✅ |
| Database | | | ✅ |
| CRUD APIs | | | ✅ |
| Rule Engine | | | ✅ |
| Legal Metrology Rules | | | ✅ |
| Confidence Workflow | | ✅ | ✅ |
| Human Verification | | | ✅ |
| Product History | | | ✅ |
| Risk Detection | | | ✅ |
| PDF Reports | | | ✅ |
| Final Integration | ✅ | ✅ | ✅ |
| End-to-End Testing | ✅ | ✅ | ✅ |

---

# Most Important Architecture Rule

Keep these three layers separate:

```text
┌─────────────────────────┐
│        P2 AI            │
│ "What does the image    │
│  appear to contain?"    │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│      P3 Rule Engine     │
│ "Does it satisfy the   │
│  applicable rules?"     │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│     Human Inspector     │
│ "What is the final      │
│  verified decision?"    │
└─────────────────────────┘
```

**AI should recommend.  
Rules should evaluate.  
Humans should be able to verify.**

This separation will make the system easier to test, explain, demonstrate, and modify later.