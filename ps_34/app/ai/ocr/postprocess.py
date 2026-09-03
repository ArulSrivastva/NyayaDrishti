from __future__ import annotations

import re

from app.ai.ocr.engine import OcrLine


def dedupe_overlapping(lines: list[OcrLine], iou_threshold: float = 0.5) -> list[OcrLine]:
    kept: list[OcrLine] = []
    for line in sorted(lines, key=lambda l: -l.confidence):
        duplicate = False
        for other in kept:
            if _iou(line.bbox, other.bbox) >= iou_threshold:
                duplicate = True
                break
        if not duplicate:
            kept.append(line)
    return kept


def merge_lines(lines: list[OcrLine], same_row_tolerance: float = 0.5) -> list[OcrLine]:
    merged: list[OcrLine] = []
    for line in sorted(lines, key=lambda l: (l.bbox[1], l.bbox[0])):
        if not merged:
            merged.append(line)
            continue
        prev = merged[-1]
        height = max(1.0, prev.bbox[3] - prev.bbox[1])
        row_aligned = abs(line.bbox[1] - prev.bbox[1]) <= same_row_tolerance * height
        adjacent = line.bbox[0] - prev.bbox[2] < 1.5 * height and line.bbox[0] >= prev.bbox[0]
        if row_aligned and adjacent:
            merged[-1] = OcrLine(
                f"{prev.text} {line.text}".strip(),
                min(prev.confidence, line.confidence),
                [prev.bbox[0], min(prev.bbox[1], line.bbox[1]), max(prev.bbox[2], line.bbox[2]), max(prev.bbox[3], line.bbox[3])],
            )
        else:
            merged.append(line)
    return merged


def sort_reading_order(lines: list[OcrLine]) -> list[OcrLine]:
    remaining = list(lines)
    ordered: list[OcrLine] = []
    while remaining:
        candidate = min(remaining, key=lambda l: (round(l.bbox[1] / max(1.0, l.bbox[3] - l.bbox[1]), 1), l.bbox[0]))
        remaining.remove(candidate)
        ordered.append(candidate)
    return ordered


def _iou(a: list[float], b: list[float]) -> float:
    x1 = max(a[0], b[0])
    y1 = max(a[1], b[1])
    x2 = min(a[2], b[2])
    y2 = min(a[3], b[3])
    inter = max(0.0, x2 - x1) * max(0.0, y2 - y1)
    area_a = max(0.0, a[2] - a[0]) * max(0.0, a[3] - a[1])
    area_b = max(0.0, b[2] - b[0]) * max(0.0, b[3] - b[1])
    union = area_a + area_b - inter
    return inter / union if union else 0.0


def postprocess(lines: list[OcrLine], *, dedupe: bool = True, merge: bool = True, sort: bool = True) -> list[OcrLine]:
    if dedupe:
        lines = dedupe_overlapping(lines)
    if merge:
        lines = merge_lines(lines)
    if sort:
        lines = sort_reading_order(lines)
    return lines