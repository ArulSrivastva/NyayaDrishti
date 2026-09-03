from app.ai.layout import analyze_layout, required_numeral_height_mm
from app.ai.ocr.engine import OcrLine
from app.ai.parsers import ExtractedField
from app.rules.units import parse_quantity


def _line(text, x1, y1, x2, y2, conf=0.95):
    return OcrLine(text, conf, [x1, y1, x2, y2])


def _field(line, value=None):
    return ExtractedField(line.text, value or line.text, 0.95, line.bbox, line.text)


def test_table_i_weights():
    for value, expected in (("50 g", 1.0), ("200 g", 1.0), ("250 g", 2.0), ("500 ml", 2.0), ("1 kg", 4.0), ("2 L", 4.0)):
        info = parse_quantity(value)
        assert required_numeral_height_mm(info, pdp_area_cm2=50) == expected


def test_table_ii_fallback():
    info = parse_quantity("12 pcs")
    assert isinstance(info.unit, str)
    assert required_numeral_height_mm(info, pdp_area_cm2=50) == 1.0
    assert required_numeral_height_mm(info, pdp_area_cm2=300) == 2.0
    assert required_numeral_height_mm(info, pdp_area_cm2=600) == 4.0
    assert required_numeral_height_mm(info, pdp_area_cm2=3000) == 6.0


def _small_layout():
    lines = [
        _line("Biscuit", 20, 20, 200, 55),
        _line("Net Quantity: 500 g", 20, 70, 300, 105),
        _line("MRP Rs 100 incl. taxes", 20, 140, 320, 175),
    ]
    fields = {
        "commodity": _field(lines[0]),
        "net_quantity": _field(lines[1], "500 g"),
        "mrp": _field(lines[2], "Rs 100"),
    }
    return lines, fields


def test_readability_large_font_at_low_dpi():
    lines, fields = _small_layout()
    readability, _, _ = analyze_layout(lines, fields, (400, 220), dpi=72.0)
    by_type = {item["declaration"]: item for item in readability}
    assert by_type["net_quantity"]["present"] is True
    assert by_type["net_quantity"]["height_mm"] > by_type["net_quantity"]["min_height_mm"]
    assert by_type["net_quantity"]["readable"] is True


def test_readability_small_font_at_high_dpi():
    lines, fields = _small_layout()
    readability, _, _ = analyze_layout(lines, fields, (400, 220), dpi=600.0)
    net_qty = next(item for item in readability if item["declaration"] == "net_quantity")
    assert net_qty["readable"] is False


def test_clear_space_ok_when_isolated():
    lines = [_line("Net Quantity: 500 g", 20, 70, 300, 105)]
    fields = {"net_quantity": _field(lines[0], "500 g")}
    _, placement, _ = analyze_layout(lines, fields, (400, 220), dpi=300.0)
    net_qty = next(item for item in placement if item["declaration"] == "net_quantity")
    assert net_qty["clear_space_ok"] is True


def test_clear_space_violation_when_text_nearby():
    lines, fields = _small_layout()
    _, placement, _ = analyze_layout(lines, fields, (400, 220), dpi=300.0)
    net_qty = next(item for item in placement if item["declaration"] == "net_quantity")
    assert net_qty["clear_space_ok"] is False


def test_declaration_inside_pdp():
    lines, fields = _small_layout()
    _, placement, _ = analyze_layout(lines, fields, (400, 220), dpi=300.0)
    for item in placement:
        if item["present"]:
            assert item["inside_pdp"] is True