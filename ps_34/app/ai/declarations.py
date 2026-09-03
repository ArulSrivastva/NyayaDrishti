from __future__ import annotations

from app.ai.ocr.engine import OcrLine
from app.ai.parsers import ExtractedField, looks_like_address

REQUIRED_DECLARATIONS = [
    ("commodity", "Common or generic name of commodity (Rule 6(1)(b))"),
    ("manufacturer", "Name and address of manufacturer (Rule 6(1)(a), Rule 10)"),
    ("net_quantity", "Net quantity in standard units (Rule 6(1)(c))"),
    ("date", "Month and year of manufacture/pre-packing/import (Rule 6(1)(d))"),
    ("mrp", "Retail sale price / MRP (Rule 6(1)(e), Rule 2(m))"),
    ("consumer_care", "Consumer complaint contact / Customer care (Rule 6(2))"),
]
PACKER_DECLARATION = ("packer", "Name and address of packer (Rule 6(1)(a))")
IMPORTER_DECLARATION = ("importer", "Name and address of importer for imports (Rule 6(1)(a), Rule 25)")


def _declaration(field_type: str, present: bool, confidence: float, bbox: list[float] | None, value: str | None) -> dict:
    return {
        "type": field_type,
        "present": bool(present),
        "confidence": round(float(confidence), 4),
        "bbox": bbox,
        "value": value,
    }


def _looks_imported(lines: list[OcrLine]) -> bool:
    for line in lines:
        lowered = line.text.lower()
        if "import" in lowered or "आयात" in line.text or "இறக்குமதி" in line.text:
            return True
    return False


def detect_declarations(
    fields: dict[str, ExtractedField],
    lines: list[OcrLine],
) -> list[dict]:
    declarations: list[dict] = []
    for field_type, description in REQUIRED_DECLARATIONS:
        field = fields.get(field_type)
        if field_type == "manufacturer" and field is None:
            text_lines = " ".join(l.text for l in lines)
            address_near = any(looks_like_address(l.text) for l in lines)
            if address_near and not _has_other_party_marker(lines):
                declarations.append(_declaration("manufacturer", True, 0.6, None, text_lines[:400]))
                continue
        if field:
            declarations.append(
                _declaration(field_type, True, field.confidence, field.bbox, field.value if field_type != "net_quantity" else field.value)
            )
        else:
            declarations.append(_declaration(field_type, False, 0.0, None, None))

    packer_field = fields.get("packer")
    packer_present = packer_field is not None or any("packed by" in l.text.lower() or "packed at" in l.text.lower() for l in lines)
    declarations.append(
        _declaration(
            PACKER_DECLARATION[0],
            packer_present,
            packer_field.confidence if packer_field else (0.6 if packer_present else 0.0),
            packer_field.bbox if packer_field else None,
            packer_field.value if packer_field else None,
        )
    )

    importer_field = fields.get("importer")
    imported = _looks_imported(lines)
    importer_present = importer_field is not None or (imported and any("imported by" in l.text.lower() for l in lines))
    declarations.append(
        _declaration(
            IMPORTER_DECLARATION[0],
            importer_present,
            importer_field.confidence if importer_field else (0.6 if importer_present else 0.0),
            importer_field.bbox if importer_field else None,
            importer_field.value if importer_field else None,
        )
    )
    return declarations


def _has_other_party_marker(lines: list[OcrLine]) -> bool:
    markers = ("packed by", "packed at", "imported by", "made in china", "imported")
    for line in lines:
        lowered = line.text.lower()
        if any(lowered.find(marker) != -1 for marker in markers):
            return True
    return False