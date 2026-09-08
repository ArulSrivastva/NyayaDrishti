from app.rules.engine import evaluate
from app.schemas.common import (
    AIResult,
    ConfidenceBlock,
    Declaration,
    FieldValue,
    ProductFields,
)


def _ai_result(
    *,
    mrp=None,
    mrp_raw=None,
    net_quantity=None,
    mfg_date=None,
    manufacturer=None,
    packer=None,
    consumer_care=None,
    commodity=None,
    imported=None,
    unit_sale_price=None,
    verdict="AUTO",
) -> AIResult:
    def fv(value, raw=None):
        return FieldValue(value=value, confidence=0.95, raw=raw or value)

    declarations = [
        Declaration(type="commodity", present=bool(commodity), confidence=0.95, value=commodity),
        Declaration(type="manufacturer", present=bool(manufacturer), confidence=0.95, value=manufacturer),
        Declaration(type="net_quantity", present=bool(net_quantity), confidence=0.95, value=net_quantity),
        Declaration(type="date", present=bool(mfg_date), confidence=0.95, value=mfg_date),
        Declaration(type="mrp", present=bool(mrp), confidence=0.95, value=mrp),
        Declaration(type="consumer_care", present=bool(consumer_care), confidence=0.95, value=consumer_care),
        Declaration(type="packer", present=False, confidence=0.0, value=None),
        Declaration(type="importer", present=bool(imported), confidence=0.95, value=imported),
        Declaration(type="unit_sale_price", present=bool(unit_sale_price), confidence=0.95, value=unit_sale_price),
    ]
    product = ProductFields(
        name=commodity,
        mrp=fv(mrp, mrp_raw) if mrp else None,
        net_quantity=fv(net_quantity) if net_quantity else None,
        manufacturing_date=fv(mfg_date) if mfg_date else None,
        manufacturer=fv(manufacturer) if manufacturer else None,
        packer=fv(packer) if packer else None,
        consumer_care=fv(consumer_care) if consumer_care else None,
        unit_sale_price=fv(unit_sale_price) if unit_sale_price else None,
    )
    return AIResult(
        product=product,
        declarations=declarations,
        confidence=ConfidenceBlock(overall=0.97, verdict=verdict),
    )


def test_fully_compliant_passes():
    outcome = evaluate(
        _ai_result(
            mrp="Rs 100",
            mrp_raw="MRP Rs 100 (incl. of all taxes)",
            net_quantity="500 g",
            unit_sale_price="₹20 / 100g",
            mfg_date="08/2026",
            manufacturer="ABC Foods Pvt Ltd",
            consumer_care="1800-123-4567",
            commodity="Biscuit",
        )
    )
    assert outcome.status == "PASS"
    assert outcome.violations == []


def test_missing_mrp_fails_rule_6_007():
    outcome = evaluate(
        _ai_result(
            net_quantity="500 g",
            mfg_date="08/2026",
            manufacturer="ABC Foods Pvt Ltd",
            consumer_care="1800-123-4567",
            commodity="Biscuit",
        )
    )
    assert outcome.status == "FAIL"
    assert [r.rule_id for r in outcome.violations] == ["R6_007"]


def test_missing_mfg_date_fails_rule_6_006():
    outcome = evaluate(
        _ai_result(
            mrp="Rs 100",
            net_quantity="500 g",
            manufacturer="ABC Foods Pvt Ltd",
            consumer_care="1800-123-4567",
            commodity="Biscuit",
        )
    )
    assert outcome.status == "FAIL"
    assert [r.rule_id for r in outcome.violations] == ["R6_006"]


def test_mrp_without_tax_note_is_review():
    outcome = evaluate(
        _ai_result(
            mrp="Rs 100",
            net_quantity="500 g",
            mfg_date="08/2026",
            manufacturer="ABC Foods Pvt Ltd",
            consumer_care="1800-123-4567",
            commodity="Biscuit",
        )
    )
    tax_rules = [r for r in outcome.results if r.rule_id == "R6_009"]
    assert tax_rules
    assert tax_rules[0].result in ("PASS", "REVIEW")


def test_importer_not_required_when_not_imported():
    outcome = evaluate(
        _ai_result(
            mrp="Rs 100",
            net_quantity="500 g",
            mfg_date="08/2026",
            manufacturer="ABC Foods Pvt Ltd",
            consumer_care="1800-123-4567",
            commodity="Biscuit",
        )
    )
    importer_rules = [r for r in outcome.results if r.rule_id == "R6_003"]
    assert importer_rules
    assert importer_rules[0].result == "PASS"


def test_imported_requires_importer_declaration():
    outcome = evaluate(
        _ai_result(
            mrp="Rs 100",
            net_quantity="500 g",
            mfg_date="08/2026",
            manufacturer="Imported for Traders Ltd",
            consumer_care="1800-123-4567",
            commodity="Biscuit",
            imported=None,
        )
    )
    importer_rules = [r for r in outcome.results if r.rule_id == "R6_003"]
    assert importer_rules and importer_rules[0].result == "FAIL"


def test_review_verdict_yields_review_status():
    outcome = evaluate(
        _ai_result(
            mrp="Rs 100",
            net_quantity="500 g",
            mfg_date="08/2026",
            manufacturer="ABC Foods Pvt Ltd",
            consumer_care="1800-123-4567",
            commodity="Biscuit",
            verdict="REVIEW",
        )
    )
    assert outcome.status == "REVIEW"


def test_usp_parity_8_combinations():
    import app.rules.usp as usp
    from decimal import Decimal

    # Case 1: 500 g @ ₹100, declared per 100 g (₹20 / 100g) -> VERIFIED
    res1 = usp.verify_declared_usp("₹20 / 100g", "₹100.00", "500 g")
    assert res1.status == usp.UspStatus.VERIFIED

    # Case 2: 500 g @ ₹100, declared per kg (₹200 / kg) -> VERIFIED
    res2 = usp.verify_declared_usp("₹200 / kg", "₹100.00", "500 g")
    assert res2.status == usp.UspStatus.VERIFIED

    # Case 3: 1 kg @ ₹100, declared per kg (₹100 / kg) -> VERIFIED
    res3 = usp.verify_declared_usp("₹100 / kg", "₹100.00", "1 kg")
    assert res3.status == usp.UspStatus.VERIFIED

    # Case 4: 1 kg @ ₹100, declared per 100 g (₹10 / 100g) -> VERIFIED
    res4 = usp.verify_declared_usp("₹10 / 100g", "₹100.00", "1 kg")
    assert res4.status == usp.UspStatus.VERIFIED

    # Case 5: 100 ml @ ₹50, declared per 100 ml (₹50 / 100ml) -> VERIFIED
    res5 = usp.verify_declared_usp("₹50 / 100ml", "₹50.00", "100 ml")
    assert res5.status == usp.UspStatus.VERIFIED

    # Case 6: 100 ml @ ₹50, declared per L (₹500 / L) -> VERIFIED
    res6 = usp.verify_declared_usp("₹500 / L", "₹50.00", "100 ml")
    assert res6.status == usp.UspStatus.VERIFIED

    # Case 7: 1 L @ ₹200, declared per L (₹200 / L) -> VERIFIED
    res7 = usp.verify_declared_usp("₹200 / L", "₹200.00", "1 L")
    assert res7.status == usp.UspStatus.VERIFIED

    # Case 8: 1 L @ ₹200, declared per 100 ml (₹20 / 100ml) -> VERIFIED
    res8 = usp.verify_declared_usp("₹20 / 100ml", "₹200.00", "1 L")
    assert res8.status == usp.UspStatus.VERIFIED

    # Negative test: 500 g @ ₹100 with incorrect declared rate (₹15 / 100g) -> MISMATCH
    mismatch = usp.verify_declared_usp("₹15 / 100g", "₹100.00", "500 g")
    assert mismatch.status == usp.UspStatus.MISMATCH

    # Exemption test: 10 g package -> EXEMPT under Rule 26
    exempt = usp.verify_declared_usp(None, "₹10.00", "10 g")
    assert exempt.status == usp.UspStatus.EXEMPT