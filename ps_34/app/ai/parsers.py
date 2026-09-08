from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Optional

from app.ai.languages import normalize_text

CURRENCY_PATTERN = re.compile(
    r"(?:MRP|mrp|m\.r\.p\.?|rp|rs\.?|rupees|inr)\s*[:\-]?\s*(?:₹|Rs\.?)?\s*"
    r"(\d{1,7}(?:[.,]\d{1,2})?)",
    re.IGNORECASE,
)
PRICE_PATTERN = re.compile(r"(?:₹|\$|€|£|¥)?\s*(\d{1,7}(?:[.,]\d{1,2})?)\s*(?:only\b|/-)?", re.IGNORECASE)

SIMPLE_UNITS = [
    (r"gr?\.?|grams?", "g"),
    (r"milli\s*meters?|millimeter|mm", "mm"),
    (r"milli\s*lit(?:er|res)?|millilit(?:er|res)?|ml(?!m)", "ml"),
    (r"l(?:it(?:er|res)?)?\b|lt\.?|litre", "l"),
    (r"kilograms?|kgs?|kg(?!l)", "kg"),
    (r"centimeters?|centimetres?|cm", "cm"),
    (r"met(?:er|re)s?|m\b", "m"),
    (r"square\s*m(?:\b|\u00B2)", "m2"),
    (r"sq\.?\s*cm|square\s*cm", "cm2"),
]

QUANTITY_PATTERN = re.compile(
    r"(\d+(?:[.,]\d{1,3})?)\s*(X\s*\d+)?\s*"
    r"(g|grams?|gr?|kg|kilograms?|m(?:l|l?)?|millilit(?:er|res)?|lit(?:er|res)?|"
    r"cm|cm2|centimeters?|met(?:er|re)s?|m2|mm|pcs|pieces|count|no\.?|nos|un?i?t?s?|N|U)\b",
    re.IGNORECASE,
)

DATE_MONTH_YEAR = re.compile(
    r"(?:mfg|mfd|manf|manufactur(?:ed|ing)|pack|pkg|best|use|exp|expiry|import)?"
    r"[\s.:\-/]*(?:([0-1]?[0-9])\s*[-/. |]\s*(\d{2,4})|([1-9])(20\d{2})|(0[1-9]|1[0-2])(20\d{2}))(?:\b|$)",
    re.IGNORECASE,
)
DATE_WORDS = re.compile(
    r"\(?\s*\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s*\d{2,4}\s*\)?",
    re.IGNORECASE,
)
MONTH_MAP = {
    "jan": "01", "feb": "02", "mar": "03", "apr": "04", "may": "05", "jun": "06",
    "jul": "07", "aug": "08", "sep": "09", "oct": "10", "nov": "11", "dec": "12",
}

PHONE_PATTERN = re.compile(
    r"(?<!\d)(?:\+?\d{1,3}[\s\-]?)?(?:(?:1800|1860|1[8-9]00)[\s\-]?\d[\s\-]?\d[\s\-]?\d[\s\-]?\d[\s\-]?\d[\s\-]?\d[\s\-]?\d|"
    r"(?:0\d{2,4}[\s\-]?\d{6,8})|(?:\d{5}[\s\-]?\d{5}))(?:\s*(?:ext\.?\s*\d+)?)(?!\d)"
)
EMAIL_PATTERN = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


@dataclass
class ParsedValue:
    value: Optional[str] = None
    unit: Optional[str] = None
    confidence: float = 0.0
    raw: str = field(default="")

    @property
    def ok(self) -> bool:
        return self.value is not None


@dataclass
class ExtractedField:
    field: str
    value: str
    confidence: float
    bbox: list[float]
    raw: str
    parsed: ParsedValue = field(default_factory=ParsedValue)


def _round_confidence(base: float, match_len: int) -> float:
    return round(max(0.0, min(1.0, base + match_len * 0.01)), 4)


def parse_mrp(text: str, confidence: float = 0.9) -> ParsedValue:
    text = normalize_text(text)
    match = CURRENCY_PATTERN.search(text)
    if match:
        amount = match.group(1).replace(",", "")
        return ParsedValue(f"Rs {amount}", None, _round_confidence(confidence, len(match.group(0))), text)
    match = re.search(r"\d{1,7}(?:[.,]\d{1,2})?", text)
    if match:
        return ParsedValue(f"Rs {match.group(0).replace(',', '')}", None, confidence * 0.7, text)
    return ParsedValue()


def parse_net_quantity(text: str, confidence: float = 0.9) -> ParsedValue:
    text = normalize_text(text)
    # Reject address PIN codes (e.g. 410401)
    if re.search(r"\b[1-9]\d{5}\b", text) and any(w in text.lower() for w in ["pune", "mumbai", "delhi", "road", "estate", "ltd", "india", "lonavala"]):
        return ParsedValue()
    match = QUANTITY_PATTERN.search(text)
    if match:
        amount = match.group(1).replace(",", "")
        if float(amount) >= 50000:
            return ParsedValue()
        unit_raw = match.group(3).lower()
        unit = normalize_unit(unit_raw)
        return ParsedValue(f"{amount} {unit}", unit, _round_confidence(confidence, len(match.group(0))), text)
    return ParsedValue()


def normalize_unit(raw: str) -> str:
    raw = raw.strip(" .")
    unit_map = {
        "g": "g", "gram": "g", "grams": "g", "gm": "g", "gr": "g",
        "kg": "kg", "kilogram": "kg", "kilograms": "kg", "kgs": "kg",
        "ml": "ml", "milliliter": "ml", "milliliters": "ml", "mls": "ml",
        "l": "l", "litre": "l", "liter": "l", "litres": "l", "liters": "l", "lt": "l",
        "cm": "cm", "centimeter": "cm", "centimeters": "cm", "centimetre": "cm",
        "mm": "mm", "millimeter": "mm",
        "m": "m", "meter": "m", "metre": "m", "meters": "m",
        "m2": "m2", "sqm": "m2", "sq m": "m2",
        "cm2": "cm2", "sq cm": "cm2",
        "pcs": "pcs", "pc": "pcs", "pieces": "pcs", "piece": "pcs",
        "no": "pcs", "nos": "pcs", "count": "pcs", "n": "u", "u": "u", "unit": "u", "units": "u",
    }
    return unit_map.get(raw, raw)


def parse_date(text: str, confidence: float = 0.85) -> ParsedValue:
    text = normalize_text(text)
    digits_only = re.sub(r"[^0-9]", "", text)
    has_keyword = bool(re.search(r"(?:mfg|mfd|manf|manufactur|pack|pkg|best|use|exp|expiry|import)", text, re.IGNORECASE))
    if not has_keyword and len(digits_only) >= 8 and not ("/" in text or "-" in text):
        return ParsedValue()

    has_price = bool(re.search(r"(?:mrp|₹|rs\.?|price|tax|incl)", text, re.IGNORECASE))
    if not has_keyword and has_price:
        return ParsedValue()

    has_qty = bool(re.search(r"(?:net\s*qty|quantity|weight|size|length)", text, re.IGNORECASE))
    if not has_keyword and has_qty:
        return ParsedValue()

    month_year = DATE_MONTH_YEAR.search(text)
    if month_year:
        groups = month_year.groups()
        if groups[0] and groups[1]:
            month, year = groups[0], groups[1]
        elif groups[2] and groups[3]:
            month, year = groups[2], groups[3]
        elif groups[4] and groups[5]:
            month, year = groups[4], groups[5]
        else:
            return ParsedValue()
        month = month.zfill(2)
        if not month.isdigit() or not (1 <= int(month) <= 12):
            return ParsedValue()
        if len(year) == 2:
            if not (18 <= int(year) <= 35):
                return ParsedValue()
            year = "20" + year if int(year) <= 35 else "19" + year
        elif len(year) == 4:
            if not (2018 <= int(year) <= 2032):
                return ParsedValue()
        else:
            return ParsedValue()
        return ParsedValue(f"{month}/{year}", None, _round_confidence(confidence, len(text)), text)
    word_match = DATE_WORDS.search(text)
    if word_match:
        for name, num in MONTH_MAP.items():
            if re.search(rf"\b{name}", word_match.group(0), re.IGNORECASE):
                year_match = re.search(r"\d{2,4}", word_match.group(0))
                year = year_match.group(0) if year_match else "????"
                if len(year) == 2:
                    if not (18 <= int(year) <= 35):
                        return ParsedValue()
                    year = "20" + year if int(year) <= 35 else "19" + year
                elif len(year) == 4:
                    if not (2018 <= int(year) <= 2032):
                        return ParsedValue()
                return ParsedValue(f"{num}/{year}", None, _round_confidence(confidence, len(text)), text)
    return ParsedValue()


def parse_phone(text: str, confidence: float = 0.8) -> ParsedValue:
    text = normalize_text(text)
    match = PHONE_PATTERN.search(text)
    if match:
        return ParsedValue(match.group(0).strip(), None, _round_confidence(confidence, len(match.group(0))), text)
    match = EMAIL_PATTERN.search(text)
    if match:
        return ParsedValue(match.group(0), None, _round_confidence(confidence, len(match.group(0))), text)
    return ParsedValue()


def looks_like_address(text: str) -> bool:
    text = normalize_text(text)
    has_letters = re.search(r"[A-Za-z\u0900-\u097F\u0B80-\u0BFF]{3,}", text)
    has_digits = re.search(r"\d{2,}", text)
    return bool(has_letters and has_digits and len(text) >= 8)