from __future__ import annotations

import json
from pathlib import Path

DEFAULT_RULES_FILE = Path(__file__).resolve().parent.parent.parent / "data" / "rules.json"


class RuleRegistry:
    def __init__(self, rules_file: str | Path | None = None):
        path = Path(rules_file) if rules_file else DEFAULT_RULES_FILE
        with open(path, "r", encoding="utf-8") as handle:
            self.rules: list[dict] = json.load(handle)
        self.by_id = {rule["rule_id"]: rule for rule in self.rules}

    def get(self, rule_id: str) -> dict | None:
        return self.by_id.get(rule_id)

    def all(self) -> list[dict]:
        return list(self.rules)

    def mandatory(self) -> list[dict]:
        return [r for r in self.rules if r.get("required")]

    def by_category(self, category: str) -> list[dict]:
        return [r for r in self.rules if r.get("category") == category]


_registry: RuleRegistry | None = None


def get_registry(rules_file: str | Path | None = None) -> RuleRegistry:
    global _registry
    if rules_file is not None:
        return RuleRegistry(rules_file)
    if _registry is None:
        _registry = RuleRegistry()
    return _registry