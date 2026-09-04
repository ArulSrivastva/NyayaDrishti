# NyayaDrishti
### Enforcement-Grade Legal Metrology Compliance Inspection Platform
**Smart India Hackathon (SIH) -- Problem Statement 34**

NyayaDrishti is an autonomous, on-device mobile inspection and legal compliance verification system designed for Legal Metrology Officers enforcing the **Legal Metrology (Packaged Commodities) Rules, 2011** under the Legal Metrology Act, 2009.

The platform operates fully **offline-first** on mobile devices, guaranteeing evidentiary integrity under Section 65B of the Indian Evidence Act, while providing a companion reference central server for administrative analytics and automated batch evaluation.

---

## System Architecture: 7 Enforcement-Grade Upgrades

NyayaDrishti features an end-to-end 12-stage inspection pipeline hardened with seven critical legal enforcement subsystems:

```
Officer Login -> Inspection Draft -> Evidence Capture -> Evidence Integrity (SHA-256)
      |
Adaptive Quality Gate -> On-Device OCR -> 3-Layer Normalization -> Declaration Extraction
      |
Product Classification -> Statutory Rule Applicability -> BigDecimal USP Verification
      |
PDP & Package Geometry Analysis -> Deterministic Compliance Engine -> Evidentiary Sign-Off
      |
Tamper-Evident Statutory PDF Inspection Report
```

### 1. Evidence Integrity & Provenance (`com.sih.model.EvidenceRecord`)
- Cryptographic SHA-256 hashing of original raw captures and gallery imports before any on-device processing.
- Tamper-evident device telemetry capture: hardware model, OS build, UTC timestamp, GPS coordinates, officer ID.
- Admissible under Section 65B of the Indian Evidence Act.

### 2. Session & Draft Recovery (`com.sih.model.InspectionDraft`)
- Auto-saving draft sessions persisted to local SQLite database across device restarts or app closures.
- Active draft banners on the officer dashboard for seamless inspection resumption.

### 3. 3-Layer OCR Normalization (`com.sih.util.ocr.OcrNormalizer`)
- **Layer 1:** Unicode NFKC normalization, statutory symbol rectification (INR, kg, g, ml, L, N), and common OCR character confusion repair (O vs 0, I vs 1).
- **Layer 2:** Bounding-box spatial union grouping for split key-value declarations (e.g., MRP above Rs. 50.00).
- **Layer 3:** Canonical declaration candidate extraction with confidence scoring, preserving raw text unaltered for evidentiary audit.

### 4. Adaptive Quality Gate (`com.sih.util.quality.ImageQualityAnalyzer`)
- Pre-capture physics-based Laplacian variance (blur detection) and specular luminance histogram (glare detection).
- Post-OCR text-density and contrast verification.
- 4x4 localized defect grid highlighting blurry or overexposed quadrants.
- Section 65B officer override audit trail for AMBER condition captures.

### 5. Arbitrary-Precision USP Calculator (`com.sih.domain.compliance.UspCalculator`)
- Financial-grade `java.math.BigDecimal` arithmetic with zero IEEE-754 floating-point drift.
- Multi-unit parsing and statutory unit conversion (grams to kilograms, millilitres to litres).
- Strict adherence to Rule 6(11) of the Legal Metrology Rules, 2011 with statutory tolerance bands and explicit UNABLE_TO_VERIFY fallbacks.

### 6. Package Geometry & PDP Analyzer (`com.sih.util.geometry.PackageGeometryAnalyzer`)
- Aspect ratio classification for common packaging types (BOX_CARTON, POUCH, BOTTLE, CAN, CYLINDER).
- Principal Display Panel (PDP) area calculation and minimum declaration font height verification.
- Multi-surface association (FRONT, BACK, SIDE) with warnings for curved cylindrical surfaces.

### 7. Formal Evidentiary Sign-Off (`com.sih.ui.screens.inspection.SignOffScreen`)
- Per-finding inspector determination (CONFIRMED, REJECTED, UNABLE_TO_VERIFY) with mandatory justification notes.
- Store representative and manufacturer acknowledgement recording.
- Canonical SHA-256 report hashing and digital certificate blocks embedded in statutory PDF reports (`com.sih.util.ReportGenerator`).

---

## Repository Structure

```
.
|-- app/                          # Native Android Mobile Application
|   |-- src/main/java/com/sih/
|   |   |-- data/local/           # SQLite LocalDatabase (v2 schema)
|   |   |-- domain/
|   |   |   |-- classification/   # Product ontology and statutory rules
|   |   |   `-- compliance/       # BigDecimal UspCalculator
|   |   |-- model/                # Inspection, EvidenceRecord, Draft, SignOff models
|   |   |-- network/              # Retrofit API client and LocalBackendServer (offline mode)
|   |   |-- repository/           # Repository pattern data orchestrators
|   |   |-- ui/
|   |   |   |-- components/       # Material 3 reusable Compose widgets
|   |   |   |-- screens/          # 12-stage inspection UI screens and SignOffScreen
|   |   |   `-- theme/            # Material 3 theming and typography
|   |   `-- util/
|   |       |-- geometry/         # PackageGeometryAnalyzer and PDP calculation
|   |       |-- ocr/              # 3-layer OcrNormalizer and spatial grouping
|   |       |-- quality/          # ImageQualityAnalyzer (blur, glare, 4x4 grid)
|   |       |-- OnDeviceAiEngine.kt # Google ML Kit on-device OCR wrapper
|   |       `-- ReportGenerator.kt # Native Canvas PDF report generator
|   `-- src/test/java/com/sih/    # Android unit and enforcement architecture tests
|
|-- ps_34/                        # Python Reference Central Server & Evaluation Suite
|   |-- app/                      # FastAPI REST application (AI pipeline, rules, auth)
|   |-- data/rules.json           # Canonical Legal Metrology statutory rules database
|   |-- scripts/                  # OCR evaluation harness and sample label generators
|   `-- tests/                    # Pytest test suite for statutory rule logic
|
|-- gradle/                       # Gradle wrapper and build configuration
|-- build.gradle.kts              # Root build script
`-- README.md
```

---

## Quick Start

### Android Application
**Requirements:** Android Studio Hedgehog+ (or Gradle 8.5+), JDK 17, Android SDK 34 (min SDK 26).

```powershell
# Build debug APK
.\gradlew.bat assembleDebug

# Run unit and enforcement architecture tests
.\gradlew.bat testDebugUnitTest
```

### Python Companion Server
**Requirements:** Python 3.10+

```powershell
cd ps_34

# Create virtual environment and install dependencies
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt

# Run pytest verification suite
pytest -v
```

---

## Statutory Framework Reference
- **The Legal Metrology Act, 2009** (Act No. 1 of 2010)
- **The Legal Metrology (Packaged Commodities) Rules, 2011** (GSR 202(E) as amended)
- **The Indian Evidence Act, 1872** (Section 65B -- Admissibility of Electronic Records)
