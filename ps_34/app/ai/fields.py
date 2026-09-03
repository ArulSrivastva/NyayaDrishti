from __future__ import annotations

import re

from app.ai.languages import SYNONYMS, normalize_text, strip_punctuation
from app.ai.ocr.engine import OcrLine
from app.ai.parsers import (
    ExtractedField,
    parse_date,
    parse_mrp,
    parse_net_quantity,
    parse_phone,
)

MANUFACTURER_KEYS = frozenset(["manufacturer", "packer", "importer"])
VALUE_FIELDS = ["mrp", "net_quantity", "manufacturer", "packer", "importer", "date", "consumer_care"]
HEAD_KEYWORDS = ("manufactured by", "mfd by", "mfg by", "packed by", "imported by")
MRP_REGEX = re.compile(r"mrp\s*(?:rs\.?|r\s*p\s*\.?|₹)?\s*[:.\-]?\s*\d", re.IGNORECASE)
KEYWORD_SPLIT = re.compile(r"\s{2,}|\|")


def _keyword_hit(line_text: str) -> tuple[str | None, str | None]:
    compact = re.sub(r"\s+", " ", strip_punctuation(normalize_text(line_text)))
    lowered = compact.lower()
    if MRP_REGEX.search(lowered):
        return "mrp", "mrp"
    for field in VALUE_FIELDS:
        for lang, words in SYNONYMS[field].items():
            for word in words:
                pattern = re.escape(word.strip()).replace(r"\ ", r"\s*").replace(r"\.", r"[.\s]?")
                if re.search(rf"(?<![a-z\u0900-\u097F\u0B80-\u0BFF]){pattern}(?![a-z\u0900-\u097F\u0B80-\u0BFF])", lowered):
                    return field, word
    return None, None


def _remaining_after_keyword(text: str, keyword: str) -> str:
    lowered = text.lower()
    pos = lowered.find(keyword.lower().strip())
    if pos == -1:
        return ""
    tail = text[pos + len(keyword):]
    strip_chars = " :>-\t\"'"
    if keyword.lower().strip() in HEAD_KEYWORDS:
        tail = re.split(KEYWORD_SPLIT, tail.strip(), maxsplit=1)[0]
        tail = tail.strip().lstrip(strip_chars).strip()
    else:
        tail = tail.strip().lstrip(strip_chars).strip()
    return tail


def _overlaps_vertical(top_line: OcrLine, candidate: OcrLine) -> bool:
    a_center = (top_line.bbox[1] + top_line.bbox[3]) / 2
    b_center = (candidate.bbox[1] + candidate.bbox[3]) / 2
    return abs(a_center - b_center) <= (top_line.bbox[3] - top_line.bbox[1]) + max(
        6, (candidate.bbox[3] - candidate.bbox[1]) / 2
    )


def _next_line_value(keyword_line: OcrLine, lines: list[OcrLine]) -> list[OcrLine]:
    below = []
    for line in lines:
        if line is keyword_line:
            continue
        if line.bbox[1] >= keyword_line.bbox[3] - 2 and _overlaps_vertical_loose(keyword_line, line):
            above = _overlaps_vertical(keyword_line, line)
            if not above:
                below.append(line)
    below.sort(key=lambda l: (abs(l.bbox[1] - keyword_line.bbox[3]), l.bbox[0]))
    return below[:2]


def _overlaps_vertical_loose(a: OcrLine, b: OcrLine) -> bool:
    return not (a.bbox[2] < b.bbox[0] - 8 or b.bbox[2] < a.bbox[0] - 8)


def _join_lines(texts: list[str]) -> str:
    return " ".join(t for t in texts if t and t != ":")


def extract_entity(line: OcrLine, field: str, keyword: str, lines: list[OcrLine]) -> ExtractedField | None:
    single_line = field in ("mrp", "net_quantity", "date", "consumer_care")
    value_lines = [line]
    if not single_line:
        value_lines += [n for n in _next_line_value(line, lines) if n is not line]
    joined = _join_lines([l.text for l in value_lines])
    bbox = [line.bbox[0], line.bbox[1], max(l.bbox[2] for l in value_lines), max(l.bbox[3] for l in value_lines)]

    if field == "mrp":
        parsed = parse_mrp(joined, confidence=line.confidence)
        if parsed.ok:
            return ExtractedField("mrp", parsed.value, parsed.confidence, bbox, joined, parsed)
    elif field == "net_quantity":
        parsed = parse_net_quantity(joined, confidence=line.confidence)
        if parsed.ok:
            return ExtractedField("net_quantity", parsed.value, parsed.confidence, bbox, joined, parsed)
    elif field in MANUFACTURER_KEYS:
        value = _remaining_after_keyword(joined, keyword)
        value = _remaining_after_keyword(value_lines[0].text, keyword) or value
        if not value:
            second = _next_line_value(line, lines)
            if second:
                value = _join_lines([s.text for s in second])
        if value and len(value) >= 2:
            return ExtractedField(
                field, value.strip(" :,>-"), min(0.95, line.confidence + 0.05), bbox, joined
            )
    elif field == "date":
        parsed = parse_date(joined, confidence=line.confidence)
        if parsed.ok:
            return ExtractedField("date", parsed.value, parsed.confidence, bbox, joined, parsed)
    elif field == "consumer_care":
        parsed = parse_phone(joined, confidence=line.confidence)
        value = parsed.value or _remaining_after_keyword(joined, keyword)
        if value:
            value = value if parsed.value else value.strip(" :,>-")
            return ExtractedField("consumer_care", value, parsed.confidence or line.confidence, bbox, joined, parsed)
    return None


def guess_commodity_name(lines: list[OcrLine], image_height: int) -> ExtractedField | None:
    cap = 0.42
    candidates = [
        l for l in lines
        if l.bbox[1] < image_height * cap and (l.bbox[3] - l.bbox[1]) >= 12
    ]
    if not candidates:
        return None

    def is_field_like(line: OcrLine) -> bool:
        text = normalize_text(line.text)
        lowered = strip_punctuation(text).lower()
        if ":" in text or re.search(r"[₹$€£]", text):
            return True
        if re.search(r"\d\s*(?:g|gm|kg|ml|l|m|cm)\b", lowered):
            return True
        if re.search(r"mrp|rs\s*\d", lowered):
            return True
        if re.search(r"\d{1,2}[-/]\d{2,4}", lowered):
            return True
        if re.search(r"1800|\+\d|care|email|@", lowered):
            return True
        field, _ = _keyword_hit(line.text)
        return field is not None

    scored = [l for l in candidates if not is_field_like(l)]
    if not scored:
        scored = [l for l in candidates if len(l.text.strip()) >= 3]
    if not scored:
        return None
    scored.sort(key=lambda l: (l.bbox[1], -(l.bbox[3] - l.bbox[1])))
    candidate = scored[0]
    low = candidate.text.strip()
    return ExtractedField("commodity", low, min(0.9, candidate.confidence), candidate.bbox, low)


def extract_fields(lines: list[OcrLine], image_shape: tuple) -> dict[str, ExtractedField]:
    found: dict[str, ExtractedField] = {}
    used_lines: set[int] = set()
    for index, line in enumerate(lines):
        field, keyword = _keyword_hit(line.text)
        if field is None or field in found or index in used_lines:
            continue
        extracted = extract_entity(line, field, keyword, lines)
        if extracted:
            found[field] = extracted
            used_lines.add(index)

    height = image_shape[0] if len(image_shape) >= 2 else 400
    if "commodity" not in found:
        commodity = guess_commodity_name(lines, height)
        if commodity:
            found["commodity"] = commodity
    return found