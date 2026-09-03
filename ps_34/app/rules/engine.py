from __future__ import annotations

from functools import lru_cache

import app.rules.units as units
from app.rules.registry import get_registry

TAX_NOTE_PATTERNS = ("incl", "inclusive", "taxes")


class RuleResult:
    __slots__ = ("rule_id", "result", "reason", "severity")

    def __init__(self, rule_id: str, result: str, reason: str, severity: str):
        self.rule_id = rule_id
        self.result = result
        self.reason = reason
        self.severity = severity

    def to_dict(self) -> dict:
        return {
            "rule_id": self.rule_id,
            "result": self.result,
            "reason": self.reason,
            "severity": self.severity,
        }


class ComplianceOutcome:
    def __init__(self, status: str, results: list[RuleResult], notes: list[str]):
        self.status = status
        self.results = results
        self.notes = notes

    def to_dict(self) -> dict:
        return {
            "status": self.status,
            "results": [r.to_dict() for r in self.results],
            "notes": self.notes,
        }

    @property
    def violations(self) -> list[RuleResult]:
        return [r for r in self.results if r.result == "FAIL"]

    @property
    def reviews(self) -> list[RuleResult]:
        return [r for r in self.results if r.result == "REVIEW"]


def _declaration_map(ai_result) -> dict:
    mapping: dict[str, dict] = {}
    for declaration in ai_result.declarations:
        mapping[declaration.type] = {
            "present": declaration.present,
            "confidence": declaration.confidence,
            "value": declaration.value,
        }
    return mapping


def _find_field_value(product, field_name: str) -> str | None:
    field = getattr(product, field_name, None)
    if field is None:
        return None
    value = field.value if field.value else None
    if value:
        return value
    return field.raw if field.raw else None


def _find_field_raw(product, field_name: str) -> str | None:
    field = getattr(product, field_name, None)
    if field is None:
        return None
    return field.raw if field.raw else None


def _imported_context(ai_result) -> bool:
    values = []
    for name in ("manufacturer", "packer", "importer"):
        value = _find_field_value(ai_result.product, name)
        if value:
            values.append(value)
    joined = " ".join(values).lower()
    return bool(joined) and ("import" in joined or "आयात" in joined or "இறக்குமதி" in joined)


def _mrp_raw(ai_result) -> str | None:
    field = ai_result.product.mrp
    if field is None:
        return None
    if field.raw:
        return field.raw
    return _find_field_value(ai_result.product, "mrp")


def evaluate(ai_result, rules_file=None) -> ComplianceOutcome:
    registry = get_registry(rules_file)
    declarations = _declaration_map(ai_result)
    notes: list[str] = []

    mrp = _find_field_value(ai_result.product, "mrp")
    net_qty = _find_field_value(ai_result.product, "net_quantity")
    net_qty_info = units.parse_quantity(net_qty)
    exempt = units.is_exempt_small_package(net_qty_info)
    if exempt:
        notes.append("Rule 26 exemption appears applicable (net quantity 10 g/10 ml or less).")

    results: list[RuleResult] = []
    for rule in registry.all():
        rule_id = rule["rule_id"]
        condition = rule["condition"]
        declaration = declarations.get(rule["field"])
        present = bool(declaration and declaration.get("present"))
        severity = rule.get("severity", "violation")

        result, reason = _apply_condition(
            condition=condition,
            present=present,
            rule=rule,
            ai_result=ai_result,
            net_qty_info=net_qty_info,
            exempt=exempt,
        )
        results.append(RuleResult(rule_id, result, reason, severity))

    status = _overall_status(results, ai_result, exempt)
    return ComplianceOutcome(status, results, notes)


def _apply_condition(condition, present, rule, ai_result, net_qty_info, exempt) -> tuple[str, str]:
    rule_id = rule["rule_id"]
    failure = rule["failure"]

    if exempt and rule.get("category") == "mandatory_declaration":
        return "PASS", "Rule 26 exemption applicable; mandatory declarations not required."
    if exempt and rule_id == "R26_001":
        return "REVIEW", "Package appears to fall under the Rule 26 exemption (10 g/10 ml or less)."

    if rule_id in ("R6_004", "R6_005", "R6_006", "R6_007", "R6_001", "R6_008") and not present:
        return "FAIL", failure

    if condition == "present":
        return ("PASS", "Declaration present.") if present else ("FAIL", failure)

    if condition == "present_or_not_manufacturer":
        if present:
            return "PASS", "Packer declaration present."
        manufacturer = _declaration_map(ai_result).get("manufacturer") or {}
        if manufacturer.get("present"):
            return "PASS", "Manufacturer declared; the manufacturer is assumed to be the packer (Rule 10(1))."
        return "REVIEW", "Neither packer nor manufacturer declaration detected; confirm the packer identity (Rule 10(1))."

    if condition == "present_if_imported":
        if not _imported_context(ai_result):
            return "PASS", "Import context not detected; importer declaration not mandatory."
        return ("PASS", "Importer declaration present.") if present else ("FAIL", failure)

    if condition == "mrp_includes_tax_note":
        raw = _mrp_raw(ai_result)
        if not raw:
            return "PASS", "MRP not present; format check not applicable."
        lowered = raw.lower()
        if any(token in lowered for token in TAX_NOTE_PATTERNS):
            return "PASS", "MRP declared with inclusive-of-all-taxes wording."
        return "REVIEW", failure

    if condition == "net_quantity_unit_format":
        if not net_qty_info.raw:
            return "PASS", "Net quantity not parsed; format check not applicable."
        problems = []
        if units.has_forbidden_qualifiers(net_qty_info.raw):
            problems.append("forbidden qualifier like 'minimum/about/approximately' used")
        if not units.unit_format_valid(net_qty_info):
            problems.append("unit format invalid (below 1 kg must use grams, below 1 L must use millilitres)")
        if problems:
            return "REVIEW", failure + " Found: " + "; ".join(problems)
        return "PASS", "Net quantity unit format is acceptable."

    if condition == "no_dozen_score_gross":
        if net_qty_info.raw and units.has_non_si_count(net_qty_info.raw):
            return "REVIEW", failure
        return "PASS", "No forbidden count terms detected."

    if condition == "font_size_check":
        readability = getattr(ai_result, "readability", [])
        detected = [item for item in readability if item.present]
        under_sized = [item for item in detected if not item.readable]
        if under_sized:
            names = ", ".join(item.declaration for item in under_sized)
            return "REVIEW", failure + f" More than one declaration below size: {names}."
        if not detected:
            return "PASS", "No declarations detected; font size check not applicable."
        return "PASS", "All detected declarations meet the minimum prescribed height."

    if condition == "placement_check":
        placement = getattr(ai_result, "placement", [])
        detected = [item for item in placement if item.present]
        misplaced = [item for item in detected if not item.inside_pdp or not item.margin_ok]
        if misplaced:
            names = ", ".join(item.declaration for item in misplaced)
            return "REVIEW", failure + f" Possibly misplaced or truncated: {names}."
        if not detected:
            return "PASS", "No declarations detected; placement check not applicable."
        return "PASS", "All detected declarations are within the principal display panel."

    if condition == "clear_space_check":
        placement = getattr(ai_result, "placement", [])
        net_qty = next((item for item in placement if item.declaration == "net_quantity"), None)
        if net_qty is None or net_qty.clear_space_ok is None:
            return "PASS", "Net quantity declaration not detected; clear-space check not applicable."
        if net_qty.clear_space_ok is True:
            return "PASS", "The area around the net quantity declaration is free of other printed information."
        return "REVIEW", failure

    if condition == "legibility_and_language":
        ocr_confidence = getattr(ai_result.confidence, "overall", 0.0)
        if ocr_confidence < 0.6:
            return "REVIEW", "OCR confidence is low; legibility of declarations should be visually verified."
        return "PASS", "Declarations detected with acceptable legibility confidence."

    if rule_id == "R26_001":
        return "INFO", "Package checked against Rule 26 exemption conditions."

    return "PASS", "No issue detected."


def _overall_status(results: list[RuleResult], ai_result, exempt: bool) -> str:
    failures = [r for r in results if r.result == "FAIL"]
    if failures:
        return "FAIL"
    verdict = getattr(ai_result.confidence, "verdict", "MANUAL")
    if verdict != "AUTO":
        return "REVIEW"
    reviews = [r for r in results if r.result == "REVIEW"]
    if reviews:
        return "REVIEW"
    return "PASS"