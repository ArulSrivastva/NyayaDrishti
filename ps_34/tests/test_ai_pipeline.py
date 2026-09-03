import os

import numpy as np

from app.ai.pipeline import AiPipeline


def _temp_dir(tmp_path, name):
    directory = str(tmp_path / name)
    os.makedirs(directory, exist_ok=True)
    return directory


def _blank_image(width=400, height=300):
    return np.zeros((height, width, 3), dtype=np.uint8)


def test_mock_inspection_extracts_fields(tmp_path):
    config = {
        "ocr_engine": "mock",
        "evidence_dir": _temp_dir(tmp_path, "evidence"),
    }
    result = AiPipeline(config).inspect(_blank_image(), run_id="t1")
    assert result.product.name == "Mock Product Biscuit"
    assert result.product.mrp.value == "Rs 100"
    assert result.product.net_quantity.value == "500 g"
    assert result.product.manufacturing_date.value == "08/2026"
    assert result.product.consumer_care.value == "1800-123-4567"


def test_mock_inspection_declarations_and_violations(tmp_path):
    config = {
        "ocr_engine": "mock",
        "evidence_dir": _temp_dir(tmp_path, "evidence"),
    }
    result = AiPipeline(config).inspect(_blank_image(), run_id="t2")
    by_type = {d.type: d for d in result.declarations}
    assert by_type["commodity"].present is True
    assert by_type["manufacturer"].present is True
    assert by_type["mrp"].present is True
    assert by_type["net_quantity"].present is True
    assert by_type["date"].present is True
    assert by_type["consumer_care"].present is True
    assert by_type["importer"].present is False
    assert [v.rule_id for v in result.violations] == ["R6_002"]


def test_mock_inspection_creates_evidence_files(tmp_path):
    evidence_dir = _temp_dir(tmp_path, "evidence")
    config = {"ocr_engine": "mock", "evidence_dir": evidence_dir}
    result = AiPipeline(config).inspect(_blank_image(), run_id="t3")
    assert result.evidence
    for item in result.evidence:
        assert os.path.exists(item.evidence_image)


def test_mock_inspection_layout_readability(tmp_path):
    config = {"ocr_engine": "mock", "evidence_dir": _temp_dir(tmp_path, "evidence")}
    result = AiPipeline(config).inspect(_blank_image(), run_id="t5")
    assert result.readability
    for item in result.readability:
        if item.present:
            assert item.height_px and item.height_px > 0
            assert item.height_mm and item.height_mm > 0
    net_qty = next((r for r in result.readability if r.declaration == "net_quantity"), None)
    assert net_qty is not None
    assert net_qty.min_height_mm == 2.0


def test_mock_inspection_layout_placement(tmp_path):
    config = {"ocr_engine": "mock", "evidence_dir": _temp_dir(tmp_path, "evidence")}
    result = AiPipeline(config).inspect(_blank_image(), run_id="t6")
    assert result.placement
    mrp = next((p for p in result.placement if p.declaration == "mrp"), None)
    assert mrp is not None and mrp.inside_pdp is True


def test_inspect_image_helper(tmp_path):
    from app.ai.pipeline import inspect_image

    config = {
        "ocr_engine": "mock",
        "evidence_dir": _temp_dir(tmp_path, "evidence"),
    }
    image = _blank_image()
    ok, buffer = [], b""
    import cv2

    ok, buffer = cv2.imencode(".jpg", image)
    result, decoded = inspect_image(buffer.tobytes(), run_id="t4", config=config)
    assert decoded.shape == image.shape
    assert result.product.name == "Mock Product Biscuit"