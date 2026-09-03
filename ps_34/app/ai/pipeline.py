from __future__ import annotations

import os
import time

import cv2
import numpy as np

from app.ai.confidence import overall_confidence, verdict
from app.ai.declarations import detect_declarations
from app.ai.evidence import generate_evidence
from app.ai.fields import extract_fields
from app.ai.languages import scripts_in_lines
from app.ai.layout import analyze_layout
from app.ai.ocr import create_engine, postprocess
from app.ai.preprocessing import preprocess
from app.ai.vlm import RegionInfo, create_vision_model
from app.schemas.common import AIResult, Declaration, EvidenceItem, FieldValue, PlacementItem, ProductFields, ReadabilityItem, Violation

RULE_ID_MAP = {
    "mrp": "R6_007",
    "net_quantity": "R6_005",
    "manufacturer": "R6_001",
    "packer": "R6_002",
    "importer": "R6_003",
    "date": "R6_006",
    "consumer_care": "R6_008",
    "commodity": "R6_004",
}


def read_image_bytes(data: bytes) -> np.ndarray:
    buffer = np.frombuffer(data, dtype=np.uint8)
    image = cv2.imdecode(buffer, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("Could not decode image bytes")
    return image


class AiPipeline:
    def __init__(self, config=None):
        config = config or {}
        self.ocr_engine = create_engine(
            config.get("ocr_engine", "rapidocr"),
            confidence_threshold=config.get("ocr_confidence_threshold", 0.4),
        )
        self.vision = create_vision_model(config.get("vlm", "heuristic"))
        self.evidence_dir = config.get("evidence_dir", "./evidence")
        self.auto_threshold = config.get("auto_verdict_threshold", 95.0)
        self.review_threshold = config.get("review_verdict_threshold", 80.0)
        self.dpi = config.get("scan_dpi", 300.0)

    def inspect(self, image: np.ndarray, run_id: str = "") -> AIResult:
        started = time.time()
        original = image
        processed = preprocess(original)
        lines = postprocess(self.ocr_engine.run(processed))
        fields = extract_fields(lines, processed.shape)

        regions = [{"bbox": l.bbox, "text": l.text, "confidence": l.confidence} for l in lines]
        vision_regions: list[RegionInfo] = []
        if lines:
            vision_regions = self.vision.analyze(processed, regions)

        declarations = detect_declarations(fields, lines)
        product = self._build_product(fields, declarations)

        readability, placement, pdp = analyze_layout(lines, fields, processed.shape, dpi=self.dpi)

        field_confidences = [f.confidence for f in fields.values()]
        mandatory_types = {"commodity", "manufacturer", "net_quantity", "date", "mrp", "consumer_care", "packer"}
        mandatory_decls = [d for d in declarations if d.get("type") in mandatory_types]
        overall = overall_confidence(field_confidences, [d.get("confidence", 0.0) for d in mandatory_decls])
        decision = verdict(overall, self.auto_threshold, self.review_threshold)

        label_bbox = self._label_bbox(lines, original.shape)
        imported = any(
            "import" in line.text.lower() or "आयात" in line.text or "இறக்குமதி" in line.text
            for line in lines
        )
        violations, bbox_map = self._initial_violations(declarations, label_bbox, imported=imported)
        evidence_list: list[EvidenceItem] = []
        for violation in violations:
            bbox = bbox_map.get(violation["rule_id"])
            if bbox:
                evidence_path = self._make_evidence(original, violation["rule_id"], violation["description"], bbox, run_id)
                evidence_list.append(
                    EvidenceItem(
                        violation=violation["description"],
                        rule_id=violation["rule_id"],
                        bbox=bbox,
                        confidence=violation.get("confidence"),
                        evidence_image=evidence_path,
                    )
                )
        for declaration in declarations:
            if declaration["present"] and declaration.get("bbox"):
                rule_id = RULE_ID_MAP.get(declaration["type"])
                evidence_path = self._make_evidence(original, rule_id, f"Detected: {declaration['type']}", declaration["bbox"], run_id)
                evidence_list.append(
                    EvidenceItem(
                        violation=f"Detected: {declaration['type']}",
                        rule_id=rule_id,
                        bbox=declaration["bbox"],
                        confidence=declaration.get("confidence"),
                        evidence_image=evidence_path,
                    )
                )

        language = self._detect_language(lines)
        result = AIResult(
            product=product,
            declarations=[Declaration(**d) for d in declarations],
            violations=[Violation(**v) for v in violations],
            evidence=evidence_list,
            confidence={"overall": overall, "verdict": decision},
            metadata={
                "language": language,
                "processing_time": round(time.time() - started, 3),
                "ocr_lines": len(lines),
            },
            readability=[ReadabilityItem(**item) for item in readability],
            placement=[PlacementItem(**item) for item in placement],
        )
        return result

    def inspect_from_path(self, path: str | os.PathLike, run_id: str = "") -> AIResult:
        image = cv2.imread(str(path))
        if image is None:
            raise ValueError(f"Could not read image at {path}")
        return self.inspect(image, run_id=run_id)

    def _build_product(self, fields: dict, declarations: list[dict]) -> ProductFields:
        def fv(field_name: str) -> FieldValue | None:
            field = fields.get(field_name)
            if field is None:
                return None
            return FieldValue(
                value=field.value,
                confidence=round(field.confidence, 4),
                bbox=field.bbox,
                raw=field.raw or field.value,
            )

        product = ProductFields(
            name=fv("commodity").value if fv("commodity") else None,
            mrp=fv("mrp"),
            net_quantity=fv("net_quantity"),
            manufacturer=fv("manufacturer"),
            packer=fv("packer"),
            importer=fv("importer"),
            consumer_care=fv("consumer_care"),
            commodity=fv("commodity"),
        )
        date_field = fields.get("date")
        if date_field:
            raw_start = (date_field.raw or date_field.value or "").lower()[:40]
            fv_date = FieldValue(value=date_field.value, confidence=round(date_field.confidence, 4), bbox=date_field.bbox)
            if any(keyword in raw_start for keyword in ("manufactur", "mfd", "mfg", "manf", "date of mfg")):
                product.manufacturing_date = fv_date
            elif any(keyword in raw_start for keyword in ("packed on", "packing", "pack date")):
                product.packing_date = fv_date
            else:
                product.manufacturing_date = fv_date
        return product

    def _initial_violations(self, declarations: list[dict], label_bbox: list[float], imported: bool = False) -> tuple[list[dict], dict]:
        violations: list[dict] = []
        bbox_map: dict[str, list[float]] = {}
        descriptions = {
            "commodity": "Common or generic name of the commodity is missing",
            "manufacturer": "Name and address of manufacturer is missing",
            "packer": "Name and address of packer is missing",
            "importer": "Name and address of importer is missing for an imported package",
            "net_quantity": "Net quantity declaration is missing",
            "date": "Month and year of manufacture/pre-packing is missing",
            "mrp": "Retail sale price (MRP) declaration is missing",
            "consumer_care": "Consumer complaint contact / customer care details are missing",
        }
        for declaration in declarations:
            if declaration["present"]:
                continue
            if declaration["type"] == "importer" and not imported:
                continue
            rule_id = RULE_ID_MAP.get(declaration["type"], "R6_004")
            violations.append(
                {
                    "rule_id": rule_id,
                    "description": descriptions.get(declaration["type"], f"{declaration['type']} declaration missing"),
                    "severity": "violation",
                    "confidence": declaration.get("confidence") or None,
                    "bbox": label_bbox,
                }
            )
            bbox_map[rule_id] = label_bbox
        return violations, bbox_map

    @staticmethod
    def _label_bbox(lines, image_shape: tuple) -> list[float]:
        height, width = image_shape[:2]
        if not lines:
            return [0.0, 0.0, float(width), float(height)]
        x1 = min(line.bbox[0] for line in lines)
        y1 = min(line.bbox[1] for line in lines)
        x2 = max(line.bbox[2] for line in lines)
        y2 = max(line.bbox[3] for line in lines)
        return [float(x1), float(y1), float(x2), float(y2)]

    def _make_evidence(self, image: np.ndarray, rule_id: str, description: str, bbox: list[float], run_id: str) -> str:
        label = rule_id
        return generate_evidence(image, label, bbox, self.evidence_dir, run_id)

    def _detect_language(self, lines) -> str:
        texts = [l.text for l in lines]
        scripts = scripts_in_lines(texts)
        if "hi" in scripts and "en" in scripts:
            return "hi+en"
        if "hi" in scripts:
            return "hi"
        if "ta" in scripts and "en" in scripts:
            return "ta+en"
        if "ta" in scripts:
            return "ta"
        return "en"


def inspect_image(data: bytes, run_id: str = "", config=None) -> tuple[AIResult, np.ndarray]:
    image = read_image_bytes(data)
    pipeline = AiPipeline(config)
    result = pipeline.inspect(image, run_id=run_id)
    return result, image