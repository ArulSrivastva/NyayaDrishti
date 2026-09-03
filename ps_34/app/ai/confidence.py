from __future__ import annotations


def weighted_confidence(confidences: list[float], weights: list[float] | None = None) -> float:
    if not confidences:
        return 0.0
    if weights is None:
        weights = [1.0] * len(confidences)
    if len(weights) != len(confidences):
        weights = [1.0] * len(confidences)
    total_weight = sum(weights)
    if total_weight <= 0:
        return 0.0
    return round(sum(c * w for c, w in zip(confidences, weights)) / total_weight, 4)


def verdict(confidence: float, auto_threshold: float = 95.0, review_threshold: float = 80.0) -> str:
    confidence = (confidence or 0.0) * 100.0
    if confidence >= auto_threshold:
        return "AUTO"
    if confidence >= review_threshold:
        return "REVIEW"
    return "MANUAL"


def overall_confidence(
    field_confidences: list[float],
    declaration_confidences: list[float],
    missing_penalty: float = 0.15,
) -> float:
    present = [c for c in declaration_confidences if c and c > 0]
    missing = len(declaration_confidences) - len(present)
    base = weighted_confidence(field_confidences + present)
    penalty = min(1.0, missing * missing_penalty)
    return round(max(0.0, base - penalty), 4)