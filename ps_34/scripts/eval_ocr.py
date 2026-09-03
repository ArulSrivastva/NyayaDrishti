from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2


def load_detection_labels(labels_file: Path) -> dict[str, list[dict]]:
    labels: dict[str, list[dict]] = {}
    with open(labels_file, "r", encoding="utf-8") as handle:
        for raw in handle:
            raw = raw.rstrip("\n")
            if not raw or "\t" not in raw:
                continue
            name, annotation = raw.split("\t", 1)
            labels[name] = json.loads(annotation)
    return labels


def box_iou(a, b) -> float:
    ax1, ay1, ax2, ay2 = a
    bx1, by1, bx2, by2 = b
    ix1, iy1 = max(ax1, bx1), max(ay1, by1)
    ix2, iy2 = min(ax2, bx2), min(ay2, by2)
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    area_a = max(0.0, ax2 - ax1) * max(0.0, ay2 - ay1)
    area_b = max(0.0, bx2 - bx1) * max(0.0, by2 - by1)
    union = area_a + area_b - inter
    return inter / union if union else 0.0


def evaluate(subset_dir: Path, n: int, iou_threshold: float = 0.5, engine: str = "rapidocr", matches: int = 3) -> dict:
    from app.ai.ocr import create_engine, postprocess

    images = sorted((subset_dir / "images").glob("*.*"))[:n]
    labels = load_detection_labels(subset_dir / "labels.txt")
    ocr_engine = create_engine(engine)
    correct = 0
    total = 0
    samples: list[dict] = []
    for image_path in images:
        annotation = labels.get(image_path.name, [])
        boxes = []
        texts = set()
        for region in annotation:
            points = region["points"]
            xs = [p[0] for p in points]
            ys = [p[1] for p in points]
            boxes.append([float(min(xs)), float(min(ys)), float(max(xs)), float(max(ys))])
            texts.add(region["transcription"].lower())
        image = cv2.imread(str(image_path))
        if image is None:
            continue
        lines = postprocess(ocr_engine.run(image))
        detected_boxes = [line.bbox for line in lines]
        predicted_texts = set(line.text.lower() for line in lines)
        matched = 0
        for box in boxes:
            if any(box_iou(box, detected) >= iou_threshold for detected in detected_boxes):
                matched += 1
                correct += 1
            total += 1
        text_hit = sum(1 for t in predicted_texts if t in texts)
        samples.append(
            {
                "file": image_path.name,
                "detection_recall": matched / len(boxes) if boxes else 0.0,
                "text_matches": text_hit,
            }
        )
    return {
        "images": len(images),
        "label_count": total,
        "detection_iou_matches": correct,
        "detection_recall": (correct / total) if total else 0.0,
        "samples": samples[:matches],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate OCR on the Food Packaging OCR Dataset")
    parser.add_argument("--subset", default="valid", choices=["train", "valid", "test"])
    parser.add_argument("--n", type=int, default=50)
    parser.add_argument("--engine", default="rapidocr")
    parser.add_argument("--dataset", default="Food Packaging OCR Dataset")

    args = parser.parse_args()
    base = Path(args.dataset)
    subset_dir = base / "det" / args.subset
    if not subset_dir.exists():
        raise SystemExit(f"Dataset directory not found: {subset_dir}")
    result = evaluate(subset_dir, args.n, engine=args.engine)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()