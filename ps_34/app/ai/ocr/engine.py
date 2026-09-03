from __future__ import annotations

import logging
from typing import Optional

import numpy as np

logger = logging.getLogger(__name__)


def _normalize_bbox(box) -> list[float]:
    points = np.asarray(box, dtype=np.float32).reshape(-1, 2)
    x1, y1 = points.min(axis=0)
    x2, y2 = points.max(axis=0)
    return [float(x1), float(y1), float(x2), float(y2)]


class OcrLine:
    __slots__ = ("text", "confidence", "bbox")

    def __init__(self, text: str, confidence: float, bbox: list[float]):
        self.text = text
        self.confidence = float(confidence)
        self.bbox = bbox

    def to_dict(self) -> dict:
        return {"text": self.text, "confidence": self.confidence, "bbox": self.bbox}

    def __repr__(self) -> str:
        return f"OcrLine({self.text!r}, {self.confidence:.3f})"


class RapidOcrEngine:
    name = "rapidocr"

    def __init__(self, confidence_threshold: float = 0.4):
        self.threshold = confidence_threshold
        self._engine = None

    def _load(self):
        if self._engine is None:
            from rapidocr_onnxruntime import RapidOCR

            self._engine = RapidOCR()

    def run(self, image: np.ndarray) -> list[OcrLine]:
        self._load()
        result, _ = self._engine(image)
        lines = []
        if result:
            for box, text, conf in result:
                try:
                    confidence = float(conf)
                except (TypeError, ValueError):
                    confidence = 0.0
                if text and confidence >= self.threshold:
                    lines.append(OcrLine(text.strip(), confidence, _normalize_bbox(box)))
        return lines


class PyTesseractEngine:
    name = "tesseract"

    def __init__(self, confidence_threshold: float = 0.4):
        self.threshold = confidence_threshold

    def run(self, image: np.ndarray) -> list[OcrLine]:
        import pytesseract

        data = pytesseract.image_to_data(image, output_type=pytesseract.Output.DICT)
        lines = []
        n = len(data["text"])
        for i in range(n):
            text = (data["text"][i] or "").strip()
            conf = float(data["conf"][i]) / 100.0 if data["conf"][i] != "-1" else 0.0
            if text and conf >= self.threshold and int(data["height"][i]) > 0:
                x, y, w, h = data["left"][i], data["top"][i], data["width"][i], data["height"][i]
                lines.append(OcrLine(text, conf, [float(x), float(y), float(x + w), float(y + h)]))
        return lines


class MockOcrEngine:
    name = "mock"

    def __init__(self, confidence_threshold: float = 0.4):
        self.threshold = confidence_threshold

    def run(self, image: np.ndarray) -> list[OcrLine]:
        return [
            OcrLine("Mock Product Biscuit", 0.95, [10.0, 10.0, 300.0, 40.0]),
            OcrLine("Net Weight: 500 g", 0.98, [10.0, 50.0, 260.0, 80.0]),
            OcrLine("MRP Rs 100 (incl. of all taxes)", 0.97, [10.0, 90.0, 360.0, 120.0]),
            OcrLine("Manufactured by: ABC Foods Pvt Ltd", 0.96, [10.0, 130.0, 340.0, 160.0]),
            OcrLine("Mfg Date: 08/2026", 0.95, [10.0, 170.0, 220.0, 200.0]),
            OcrLine("Customer Care: 1800-123-4567", 0.94, [10.0, 210.0, 300.0, 240.0]),
        ]


ENGINES = {"rapidocr": RapidOcrEngine, "tesseract": PyTesseractEngine, "mock": MockOcrEngine}


def create_engine(name: Optional[str] = None, confidence_threshold: float = 0.4):
    name = (name or "rapidocr").lower()
    if name not in ENGINES:
        logger.warning("OCR engine %r is not registered; falling back to mock.", name)
        name = "mock"
    return ENGINES[name](confidence_threshold=confidence_threshold)