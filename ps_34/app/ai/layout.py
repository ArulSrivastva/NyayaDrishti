from __future__ import annotations

from app.ai.ocr.engine import OcrLine
from app.ai.parsers import ExtractedField
from app.rules.units import parse_quantity

MM_PER_INCH = 25.4
LETTER_MIN_MM = 1.0

READABILITY_ORDER = ("commodity", "manufacturer", "packer", "importer", "net_quantity", "date", "mrp", "consumer_care")


def _table_i_numerals(value, info) -> float | None:
    if info.unit in ("g", "kg"):
        grams = info.to_grams()
        if grams is not None:
            if grams <= 200:
                return 1.0
            if grams <= 500:
                return 2.0
            return 4.0
    if info.unit in ("ml", "l"):
        millilitres = info.to_millilitres()
        if millilitres is not None:
            if millilitres <= 200:
                return 1.0
            if millilitres <= 500:
                return 2.0
            return 4.0
    return None


def _table_ii_numerals(pdp_area_cm2: float) -> float:
    if pdp_area_cm2 <= 100:
        return 1.0
    if pdp_area_cm2 <= 500:
        return 2.0
    if pdp_area_cm2 <= 2500:
        return 4.0
    return 6.0


def required_numeral_height_mm(net_info, pdp_area_cm2: float) -> float:
    from_table_i = _table_i_numerals(net_info.raw, net_info)
    if from_table_i is not None:
        return from_table_i
    return _table_ii_numerals(pdp_area_cm2)


def estimate_pdp(lines: list[OcrLine], image_shape: tuple) -> list[float]:
    height, width = image_shape[:2]
    if not lines:
        return [0.0, 0.0, float(width), float(height)]
    return [
        float(min(line.bbox[0] for line in lines)),
        float(min(line.bbox[1] for line in lines)),
        float(max(line.bbox[2] for line in lines)),
        float(max(line.bbox[3] for line in lines)),
    ]


def _bbox_intersects(a: list[float], b: list[float]) -> bool:
    return not (a[2] < b[0] or b[2] < a[0] or a[3] < b[1] or b[3] < a[1])


def _inside(pdp: list[float], bbox: list[float]) -> bool:
    tol = 1.0
    return (
        bbox[0] >= pdp[0] - tol
        and bbox[1] >= pdp[1] - tol
        and bbox[2] <= pdp[2] + tol
        and bbox[3] <= pdp[3] + tol
    )


def analyze_layout(
    lines: list[OcrLine],
    fields: dict[str, ExtractedField],
    image_shape: tuple,
    dpi: float = 300.0,
) -> tuple[list[dict], list[dict], list[float]]:
    mm_per_px = MM_PER_INCH / dpi
    height, width = image_shape[:2]
    pdp = estimate_pdp(lines, image_shape)
    pdp_w, pdp_h = max(1.0, pdp[2] - pdp[0]), max(1.0, pdp[3] - pdp[1])
    pdp_area_cm2 = (pdp_w * pdp_h) * (mm_per_px * mm_per_px) / 100.0

    net_field = fields.get("net_quantity")
    net_text = net_field.value if net_field else None
    net_info = parse_quantity(net_text)

    readability = []
    for declaration in READABILITY_ORDER:
        field = fields.get(declaration)
        if field is None:
            readability.append(
                {
                    "declaration": declaration,
                    "present": False,
                    "height_px": None,
                    "height_mm": None,
                    "min_height_mm": LETTER_MIN_MM,
                    "readable": False,
                    "note": "Declaration not detected; font size cannot be assessed.",
                }
            )
            continue
        height_px = field.bbox[3] - field.bbox[1]
        height_mm = height_px * mm_per_px
        if declaration in ("net_quantity", "mrp"):
            min_height = required_numeral_height_mm(net_info, pdp_area_cm2)
        else:
            min_height = LETTER_MIN_MM
        readable = height_mm >= min_height * 0.9
        readability.append(
            {
                "declaration": declaration,
                "present": True,
                "height_px": round(height_px, 2),
                "height_mm": round(height_mm, 3),
                "min_height_mm": min_height,
                "readable": bool(readable),
                "note": (
                    f"Estimated character height {height_mm:.2f} mm (required {min_height:.2f} mm "
                    f"at {dpi:.0f} DPI assumption)."
                ),
            }
        )

    placement = []
    for declaration in READABILITY_ORDER:
        field = fields.get(declaration)
        if field is None:
            placement.append(
                {
                    "declaration": declaration,
                    "present": False,
                    "inside_pdp": False,
                    "clear_space_ok": None,
                    "margin_ok": False,
                    "note": "Declaration not detected on the label.",
                }
            )
            continue
        bbox = field.bbox
        inside = _inside(pdp, bbox)
        margin_ok = bbox[0] > 0 and bbox[1] > 0 and bbox[2] < width - 1 and bbox[3] < height - 1
        item = {
            "declaration": declaration,
            "present": True,
            "inside_pdp": bool(inside),
            "clear_space_ok": None,
            "margin_ok": bool(margin_ok),
            "note": "Declaration is within the estimated principal display panel."
            if inside
            else "Declaration appears outside the estimated principal display panel.",
        }
        if declaration == "net_quantity":
            item["clear_space_ok"] = _clear_space_ok(field.bbox, lines)
            if item["clear_space_ok"] is False:
                item["note"] = "Other printed information intrudes on the area around the net quantity declaration."
        placement.append(item)
    return readability, placement, pdp


def _clear_space_ok(qty_bbox: list[float], lines: list[OcrLine]) -> bool | None:
    height_px = qty_bbox[3] - qty_bbox[1]
    protected = [qty_bbox[0] - 2 * height_px, qty_bbox[1] - height_px, qty_bbox[2] + 2 * height_px, qty_bbox[3] + height_px]
    for other in lines:
        if _bbox_intersects(other.bbox, protected):
            if other.bbox is qty_bbox or _same_bbox(other.bbox, qty_bbox):
                continue
            if _bbox_intersects(other.bbox, qty_bbox):
                continue
            return False
    return True


def _same_bbox(a: list[float], b: list[float]) -> bool:
    return abs(a[0] - b[0]) < 1 and abs(a[1] - b[1]) < 1 and abs(a[2] - b[2]) < 1 and abs(a[3] - b[3]) < 1