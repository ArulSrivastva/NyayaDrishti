from __future__ import annotations

import os
import time
import uuid
from pathlib import Path

import cv2
import numpy as np


def _clip_bbox(bbox: list[float], width: int, height: int) -> tuple[int, int, int, int]:
    x1 = max(0, int(round(bbox[0])))
    y1 = max(0, int(round(bbox[1])))
    x2 = min(width - 1, int(round(bbox[2])))
    y2 = min(height - 1, int(round(bbox[3])))
    if x2 <= x1 or y2 <= y1:
        x2, y2 = min(width, x1 + 40), min(height, y1 + 40)
    return x1, y1, x2, y2


def crop_region(image: np.ndarray, bbox: list[float]) -> np.ndarray:
    height, width = image.shape[:2]
    x1, y1, x2, y2 = _clip_bbox(bbox, width, height)
    return image[y1:y2, x1:x2]


def annotate(image: np.ndarray, bbox: list[float], label: str | None = None, color=(0, 0, 255)) -> np.ndarray:
    height, width = image.shape[:2]
    x1, y1, x2, y2 = _clip_bbox(bbox, width, height)
    margin = max(1, int(max(width, height) * 0.004))
    annotated = image.copy()
    cv2.rectangle(annotated, (x1 - margin, y1 - margin), (x2 + margin, y2 + margin), color, 2)
    if label:
        scale = max(0.5, min(1.0, width / 900))
        font = cv2.FONT_HERSHEY_SIMPLEX
        (tw, th), baseline = cv2.getTextSize(label, font, scale, 1)
        text_y = max(0, y1 - margin - th - 6)
        cv2.rectangle(annotated, (x1 - margin, text_y), (x1 - margin + tw + 4, y1 - margin), color, -1)
        cv2.putText(annotated, label, (x1 - margin + 2, text_y + th), font, scale, (255, 255, 255), 1)
    return annotated


def save_image(image: np.ndarray, directory: str, prefix: str = "evidence", ext: str = "jpg") -> str:
    Path(directory).mkdir(parents=True, exist_ok=True)
    filename = f"{prefix}_{int(time.time() * 1000)}_{uuid.uuid4().hex[:8]}.{ext}"
    path = os.path.join(directory, filename)
    ok, buffer = cv2.imencode(f".{ext}", image)
    if not ok:
        raise RuntimeError(f"Could not encode image for {path}")
    buffer.tofile(path)
    return path


def generate_evidence(
    original: np.ndarray,
    violation_label: str,
    bbox: list[float],
    evidence_dir: str,
    run_id: str,
) -> str:
    annotated = annotate(original, bbox, violation_label)
    return save_image(annotated, evidence_dir, prefix=f"ev_{run_id[:8]}")