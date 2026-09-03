from __future__ import annotations

from collections import Counter

SEVERITY_WEIGHT = {"violation": 1.0, "format": 0.5, "info": 0.0}


def risk_level(
    current_rule_ids: list[str],
    history_rule_ids: list[list[str]],
    severities: list[str] | None = None,
) -> tuple[str, str]:
    severities = severities or (["violation"] * len(current_rule_ids))
    weights = [SEVERITY_WEIGHT.get(s, 1.0) for s in severities]
    score = sum(weights)
    repeat_counter: Counter[str] = Counter()
    for previous in history_rule_ids:
        repeat_counter.update(set(previous))

    repeated = [rule_id for rule_id in current_rule_ids if repeat_counter.get(rule_id, 0) >= 1]
    repeated_any = any(repeat_counter.get(rule_id, 0) >= 2 for rule_id in current_rule_ids)

    if score == 0:
        return "LOW", "No violations detected."
    if repeated_any or score >= 3.0:
        reason = "Repeated violations detected across inspections." if repeated_any else f"Multiple violations detected ({int(round(score))})."
        return "HIGH", reason
    if repeated:
        return "MEDIUM", f"Violations repeated from earlier inspections: {', '.join(sorted(set(repeated)))}."
    return "MEDIUM", f"{int(round(score))} violation(s) detected."