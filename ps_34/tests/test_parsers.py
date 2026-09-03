from app.ai.parsers import (
    parse_date,
    parse_mrp,
    parse_net_quantity,
    parse_phone,
)


def test_parse_mrp_with_currency():
    result = parse_mrp("MRP Rs 100 (incl. of all taxes)")
    assert result.ok is True
    assert result.value == "Rs 100"


def test_parse_mrp_compact_no_space():
    result = parse_mrp("MRPRs100(incl.ofalltaxes)")
    assert result.ok is True
    assert result.value == "Rs 100"


def test_parse_mrp_plain_rupees():
    result = parse_mrp("Rs. 45.50 only")
    assert result.ok is True
    assert float(result.value.replace("Rs ", "")) == 45.5


def test_parse_mrp_currency_symbol():
    result = parse_mrp("Price: Rs.250")
    assert result.ok is True
    assert result.value == "Rs 250"


def test_parse_mrp_missing():
    result = parse_mrp("Best Before: Nine months")
    assert result.ok is False


def test_parse_net_quantity_grams():
    result = parse_net_quantity("Net Weight: 500 g")
    assert result.ok is True
    assert result.value == "500 g"
    assert result.unit == "g"


def test_parse_net_quantity_kilograms():
    result = parse_net_quantity("Net Qty 1 kg")
    assert result.ok is True
    assert result.value == "1 kg"


def test_parse_net_quantity_millilitres():
    result = parse_net_quantity("Net content: 250 ml")
    assert result.ok is True
    assert result.unit == "ml"


def test_parse_net_quantity_missing():
    result = parse_net_quantity("Gross weight 2 kg")
    assert result.ok is True
    assert result.value == "2 kg"


def test_parse_date_numeric():
    result = parse_date("Manufactured Date: 08/2026")
    assert result.ok is True
    assert result.value == "08/2026"


def test_parse_date_short_year():
    result = parse_date("Mfg Date: 12/25")
    assert result.ok is True
    assert result.value == "12/2025"


def test_parse_date_month_word():
    result = parse_date("Best Before: Mar 2026")
    assert result.ok is True
    assert result.value == "03/2026"


def test_parse_date_missing():
    result = parse_date("Product of India")
    assert result.ok is False


def test_parse_phone_toll_free():
    result = parse_phone("Customer Care: 1800-123-4567")
    assert result.ok is True
    assert result.value == "1800-123-4567"


def test_parse_phone_email_fallback():
    result = parse_phone("Contact us at care@abcfoods.in")
    assert result.ok is True
    assert result.value == "care@abcfoods.in"


def test_parse_phone_none():
    result = parse_phone("Fresh and tasty")
    assert result.ok is False