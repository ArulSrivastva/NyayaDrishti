from __future__ import annotations

import logging

import numpy as np

logger = logging.getLogger(__name__)


class RegionInfo:
    __slots__ = ("bbox", "kind", "confidence")

    def __init__(self, bbox: list[float], kind: str, confidence: float):
        self.bbox = bbox
        self.kind = kind
        self.confidence = confidence

    def to_dict(self) -> dict:
        return {"bbox": self.bbox, "kind": self.kind, "confidence": float(self.confidence)}


class BaseVisionModel:
    name = "base"

    def analyze(self, image: np.ndarray, text_regions: list[dict]) -> list[RegionInfo]:
        raise NotImplementedError


class HeuristicVisionModel(BaseVisionModel):
    name = "heuristic"

    def analyze(self, image: np.ndarray, text_regions: list[dict]) -> list[RegionInfo]:
        gray = np.asarray(image)
        if gray.ndim == 3:
            gray = np.asarray(image)[:, :, 0]
        h, w = gray.shape[:2]
        regions = []
        for item in text_regions:
            x1, y1, x2, y2 = (int(v) for v in item["bbox"])
            patch = gray[y1:y2, x1:x2]
            if patch.size == 0:
                continue
            density = float(np.count_nonzero(patch < 128)) / patch.size
            confidence = min(0.95, 0.6 + density * 1.5)
            regions.append(RegionInfo(item["bbox"], "text", round(confidence, 3)))
        return regions


class FlorenceVisionModel(BaseVisionModel):
    name = "florence"

    def __init__(self):
        self._model = None
        self._processor = None

    def _load(self):
        if self._model is None:
            from transformers import AutoModelForCausalLM, AutoProcessor

            model_id = "microsoft/Florence-2-base"
            self._processor = AutoProcessor.from_pretrained(model_id, trust_remote_code=True)
            self._model = AutoModelForCausalLM.from_pretrained(model_id, trust_remote_code=True)

    def analyze(self, image: np.ndarray, text_regions: list[dict]) -> list[RegionInfo]:
        self._load()
        from PIL import Image

        prompt = "<OD>"
        pil = Image.fromarray(image[:, :, ::-1])
        inputs = self._processor(text=prompt, images=pil, return_tensors="pt")
        generated_ids = self._model.generate(**inputs, max_new_tokens=512)
        output = self._processor.batch_decode(generated_ids, skip_special_tokens=False)[0]
        parsed = self._processor.post_process_generation(output, task="<OD>", image_size=pil.size)
        regions: list[RegionInfo] = []
        for detection in parsed.get("<OD>", {}).get("bboxes", []):
            left, top, right, bottom = [float(v) for v in detection]
            regions.append(RegionInfo([left, top, right, bottom], "object", 0.9))
        return regions


def create_vision_model(name: str | None = None) -> BaseVisionModel:
    name = (name or "heuristic").lower()
    if name == "florence":
        try:
            return FlorenceVisionModel()
        except Exception as exc:  # noqa: BLE001
            logger.warning("Florence unavailable (%s); using heuristic model.", exc)
    return HeuristicVisionModel()