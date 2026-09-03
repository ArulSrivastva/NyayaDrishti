from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Optional


@dataclass
class QuantityInfo:
    amount: Optional[float] = None
    unit: Optional[str] = None
    raw: Optional[str] = None

    @property
    def parsed(self) -> bool:
        return self.amount is not None and bool(self.unit)

    def to_grams(self) -> Optional[float]:
        if not self.parsed:
            return None
        factors = {"g": 1.0, "kg": 1000.0, "ml": 1.0, "l": 1000.0}
        if self.unit in factors:
            if self.unit in ("ml", "l"):
                return None
            return self.amount * factors[self.unit]
        return None

    def to_millilitres(self) -> Optional[float]:
        if not self.parsed:
            return None
        factors = {"ml": 1.0, "l": 1000.0}
        if self.unit in factors:
            return self.amount * factors[self.unit]
        return None


QUANTITY_RE = re.compile(r"(\d+(?:\.\d+)?)\s*(g|kg|ml|l|m|cm|mm|pcs|u|nos|pieces)\b", re.IGNORECASE)
FORBIDDEN_WORDS = ("minimum", "min", "not less than", "average", "about", "approximately", "approx", "more than", "when packed")
NON_SI_COUNTS = ("dozen", "score", "gross", "great gross")


def parse_quantity(value: str | None) -> QuantityInfo:
    if not value:
        return QuantityInfo()
    match = QUANTITY_RE.search(value.lower())
    if not match:
        return QuantityInfo(raw=value)
    unit = match.group(2).lower()
    unit_map = {"grams": "g", "g": "g", "kgs": "kg", "kg": "kg", "mls": "ml", "ml": "ml", "liters": "l", "litres": "l", "l": "l"}
    unit = unit_map.get(unit, unit)
    return QuantityInfo(amount=float(match.group(1)), unit=unit, raw=value)


def has_forbidden_qualifiers(text: str | None) -> bool:
    if not text:
        return False
    lowered = text.lower()
    return any(word in lowered for word in FORBIDDEN_WORDS)


def has_non_si_count(text: str | None) -> bool:
    if not text:
        return False
    lowered = text.lower()
    return any(word in lowered for word in NON_SI_COUNTS)


def unit_format_valid(info: QuantityInfo) -> bool:
    if not info.parsed:
        return True
    if info.unit == "kg" and info.amount < 1.0:
        return False
    if info.unit == "l" and info.amount < 1.0:
        return False
    return True


def is_exempt_small_package(info: QuantityInfo) -> bool:
    if not info.parsed:
        return False
    if info.unit in ("g", "kg"):
        grams = info.to_grams()
        return grams is not None and grams <= 10.0
    if info.unit in ("ml", "l"):
        ml = info.to_millilitres()
        return ml is not None and ml <= 10.0
    return False