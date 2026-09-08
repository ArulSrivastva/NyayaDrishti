from __future__ import annotations

import re
from decimal import Decimal, ROUND_HALF_UP
from enum import Enum
from typing import Optional, Tuple


class QuantityUnit(str, Enum):
    GRAM = "g"
    KILOGRAM = "kg"
    MILLILITER = "ml"
    LITER = "l"
    UNIT = "unit"
    PAGE = "page"

    def to_base_unit(self) -> QuantityUnit:
        if self in (QuantityUnit.GRAM, QuantityUnit.KILOGRAM):
            return QuantityUnit.GRAM
        if self in (QuantityUnit.MILLILITER, QuantityUnit.LITER):
            return QuantityUnit.MILLILITER
        return self


class StatutoryUspUnit(str, Enum):
    PER_G = "g"
    PER_100G = "100g"
    PER_KG = "kg"
    PER_ML = "ml"
    PER_100ML = "100ml"
    PER_L = "L"
    PER_UNIT = "unit"

    @property
    def base_multiplier(self) -> Decimal:
        multipliers = {
            StatutoryUspUnit.PER_G: Decimal("1"),
            StatutoryUspUnit.PER_100G: Decimal("100"),
            StatutoryUspUnit.PER_KG: Decimal("1000"),
            StatutoryUspUnit.PER_ML: Decimal("1"),
            StatutoryUspUnit.PER_100ML: Decimal("100"),
            StatutoryUspUnit.PER_L: Decimal("1000"),
            StatutoryUspUnit.PER_UNIT: Decimal("1"),
        }
        return multipliers[self]

    def display_unit(self) -> str:
        return f"₹/{self.value}"


class NormalizedQuantity:
    def __init__(
        self,
        value_in_base: Decimal,
        original_unit: QuantityUnit,
        original_amount: Decimal,
        original_text: str,
        confidence: float = 0.9,
    ):
        self.value_in_base = value_in_base
        self.original_unit = original_unit
        self.original_amount = original_amount
        self.original_text = original_text
        self.confidence = confidence

    def is_small_package_exempt(self) -> bool:
        base_unit = self.original_unit.to_base_unit()
        return (
            base_unit in (QuantityUnit.GRAM, QuantityUnit.MILLILITER)
            and self.value_in_base <= Decimal("10")
        )

    def get_canonical_statutory_unit(self) -> StatutoryUspUnit:
        base_unit = self.original_unit.to_base_unit()
        if base_unit == QuantityUnit.GRAM:
            if self.value_in_base < Decimal("1000"):
                return StatutoryUspUnit.PER_100G
            return StatutoryUspUnit.PER_KG
        elif base_unit == QuantityUnit.MILLILITER:
            if self.value_in_base < Decimal("1000"):
                return StatutoryUspUnit.PER_100ML
            return StatutoryUspUnit.PER_L
        return StatutoryUspUnit.PER_UNIT


class UspStatus(str, Enum):
    VERIFIED = "VERIFIED"
    MISSING = "MISSING"
    UNABLE_TO_VERIFY = "UNABLE_TO_VERIFY"
    MISMATCH = "MISMATCH"
    EXEMPT = "EXEMPT"


class UspResult:
    def __init__(
        self,
        calculated_usp: Optional[Decimal],
        display_unit: Optional[str],
        display_value: Optional[str],
        status: UspStatus,
        declared_usp: Optional[str],
        calculated_usp_str: Optional[str],
        mismatch_delta: Optional[Decimal],
    ):
        self.calculated_usp = calculated_usp
        self.display_unit = display_unit
        self.display_value = display_value
        self.status = status
        self.declared_usp = declared_usp
        self.calculated_usp_str = calculated_usp_str
        self.mismatch_delta = mismatch_delta

    def to_dict(self) -> dict:
        return {
            "calculated_usp": str(self.calculated_usp) if self.calculated_usp else None,
            "display_unit": self.display_unit,
            "display_value": self.display_value,
            "status": self.status.value,
            "declared_usp": self.declared_usp,
            "calculated_usp_str": self.calculated_usp_str,
            "mismatch_delta": str(self.mismatch_delta) if self.mismatch_delta else None,
        }


QUANTITY_REGEX = re.compile(
    r"(?i)([0-9]+(?:\.[0-9]+)?)\s*(g|gm|gms|gram|grams|kg|kgs|ml|mls|l|ltr|ltrs|litre|litres|liter|liters|units|n|nos|pages|sheets)\b"
)
MRP_REGEX = re.compile(
    r"(?i)(?:m\.?r\.?p\.?|rs\.?|₹|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)"
)
FALLBACK_NUM_REGEX = re.compile(r"(?i)([0-9,]+(?:\.[0-9]{1,2})?)")


def normalize_quantity(ocr_text: Optional[str]) -> Optional[NormalizedQuantity]:
    if not ocr_text:
        return None
    match = QUANTITY_REGEX.search(ocr_text)
    if not match:
        return None

    val_str = match.group(1)
    unit_str = match.group(2).lower()

    try:
        val = Decimal(val_str)
    except Exception:
        return None

    unit_mapping = {
        "g": QuantityUnit.GRAM,
        "gm": QuantityUnit.GRAM,
        "gms": QuantityUnit.GRAM,
        "gram": QuantityUnit.GRAM,
        "grams": QuantityUnit.GRAM,
        "kg": QuantityUnit.KILOGRAM,
        "kgs": QuantityUnit.KILOGRAM,
        "ml": QuantityUnit.MILLILITER,
        "mls": QuantityUnit.MILLILITER,
        "l": QuantityUnit.LITER,
        "ltr": QuantityUnit.LITER,
        "ltrs": QuantityUnit.LITER,
        "litre": QuantityUnit.LITER,
        "litres": QuantityUnit.LITER,
        "liter": QuantityUnit.LITER,
        "liters": QuantityUnit.LITER,
        "units": QuantityUnit.UNIT,
        "n": QuantityUnit.UNIT,
        "nos": QuantityUnit.UNIT,
        "pages": QuantityUnit.PAGE,
        "sheets": QuantityUnit.PAGE,
    }
    unit = unit_mapping.get(unit_str)
    if not unit:
        return None

    if unit == QuantityUnit.KILOGRAM:
        value_in_base = val * Decimal("1000")
    elif unit == QuantityUnit.LITER:
        value_in_base = val * Decimal("1000")
    else:
        value_in_base = val

    return NormalizedQuantity(
        value_in_base=value_in_base,
        original_unit=unit,
        original_amount=val,
        original_text=ocr_text,
        confidence=0.9,
    )


def extract_mrp_value(mrp_text: Optional[str]) -> Optional[Decimal]:
    if not mrp_text:
        return None
    match = MRP_REGEX.search(mrp_text) or FALLBACK_NUM_REGEX.search(mrp_text)
    if not match:
        return None
    val_str = match.group(1).replace(",", "")
    try:
        return Decimal(val_str)
    except Exception:
        return None


def calculate_usp(mrp_value: Decimal, quantity: NormalizedQuantity) -> UspResult:
    if quantity.value_in_base == Decimal("0"):
        return UspResult(None, None, None, UspStatus.UNABLE_TO_VERIFY, None, None, None)

    if quantity.is_small_package_exempt():
        return UspResult(None, None, None, UspStatus.EXEMPT, None, None, None)

    canonical_unit = quantity.get_canonical_statutory_unit()
    calculated_usp = (
        (mrp_value * canonical_unit.base_multiplier) / quantity.value_in_base
    ).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

    display_unit = canonical_unit.display_unit()
    display_value = f"₹{calculated_usp}/{canonical_unit.value}"

    return UspResult(
        calculated_usp=calculated_usp,
        display_unit=display_unit,
        display_value=display_value,
        status=UspStatus.MISSING,
        declared_usp=None,
        calculated_usp_str=str(calculated_usp),
        mismatch_delta=None,
    )


def parse_declared_usp(declared_usp_text: str) -> Optional[Tuple[Decimal, StatutoryUspUnit]]:
    lower = declared_usp_text.lower()
    num = extract_mrp_value(declared_usp_text)
    if num is None:
        return None

    if re.search(r"(?i)(?:/|per)\s*100\s*(?:g|gm|gms|gram|grams)\b", lower):
        unit = StatutoryUspUnit.PER_100G
    elif re.search(r"(?i)(?:/|per)\s*100\s*(?:m|ml|mls|millilitres?|milliliters?)\b", lower):
        unit = StatutoryUspUnit.PER_100ML
    elif re.search(r"(?i)(?:/|per)\s*(?:kg|kgs|kilogram|kilograms)\b", lower):
        unit = StatutoryUspUnit.PER_KG
    elif re.search(r"(?i)(?:/|per)\s*(?:l|ltr|ltrs|litre|litres|liter|liters)\b", lower):
        unit = StatutoryUspUnit.PER_L
    elif re.search(r"(?i)(?:/|per)\s*(?:g|gm|gms|gram|grams)\b", lower):
        unit = StatutoryUspUnit.PER_G
    elif re.search(r"(?i)(?:/|per)\s*(?:m|ml|mls|millilitres?|milliliters?)\b", lower):
        unit = StatutoryUspUnit.PER_ML
    elif re.search(r"(?i)(?:/|per)\s*(?:unit|u|piece|pcs?|item)\b", lower):
        unit = StatutoryUspUnit.PER_UNIT
    else:
        return None

    return num, unit


def verify_declared_usp(
    declared_usp_text: Optional[str],
    mrp_text: Optional[str],
    quantity_text: Optional[str],
) -> UspResult:
    if mrp_text is None or quantity_text is None:
        return UspResult(None, None, None, UspStatus.UNABLE_TO_VERIFY, declared_usp_text, None, None)

    mrp_value = extract_mrp_value(mrp_text)
    quantity = normalize_quantity(quantity_text)

    if mrp_value is None or quantity is None or quantity.confidence < 0.70:
        return UspResult(None, None, None, UspStatus.UNABLE_TO_VERIFY, declared_usp_text, None, None)

    if quantity.is_small_package_exempt():
        return UspResult(None, None, None, UspStatus.EXEMPT, declared_usp_text, None, None)

    calc_result = calculate_usp(mrp_value, quantity)
    if calc_result.calculated_usp is None:
        calc_result.status = UspStatus.UNABLE_TO_VERIFY
        return calc_result

    if not declared_usp_text or not declared_usp_text.strip():
        calc_result.status = UspStatus.MISSING
        return calc_result

    parsed_declared = parse_declared_usp(declared_usp_text)
    if parsed_declared is None:
        calc_result.status = UspStatus.UNABLE_TO_VERIFY
        calc_result.declared_usp = declared_usp_text
        return calc_result

    declared_value, declared_unit = parsed_declared

    expected_in_declared_unit = (
        (mrp_value * declared_unit.base_multiplier) / quantity.value_in_base
    ).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

    delta = abs(expected_in_declared_unit - declared_value)
    tolerance = max(
        (expected_in_declared_unit * Decimal("0.02")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP),
        Decimal("0.10"),
    )

    status = UspStatus.VERIFIED if delta <= tolerance else UspStatus.MISMATCH

    calc_result.status = status
    calc_result.declared_usp = declared_usp_text
    calc_result.mismatch_delta = delta if status == UspStatus.MISMATCH else None
    return calc_result
