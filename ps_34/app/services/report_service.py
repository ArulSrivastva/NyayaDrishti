from __future__ import annotations

import os
from datetime import datetime
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    Image,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

from app.config import get_settings
from app.services.inspection_service import inspect_dict

settings = get_settings()


def _style_sheet():
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(name="TitleLm", parent=styles["Title"], fontSize=18, spaceAfter=4))
    styles.add(ParagraphStyle(name="Subtitle", parent=styles["Normal"], fontSize=9, textColor=colors.grey))
    styles.add(ParagraphStyle(name="Section", parent=styles["Heading2"], fontSize=13, spaceBefore=10))
    styles.add(ParagraphStyle(name="Cell", parent=styles["Normal"], fontSize=8.5, leading=11))
    return styles


def _table(headers: list[str], rows: list[list[str]]) -> Table:
    table = Table([headers] + rows, repeatRows=1, hAlign="LEFT")
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1f3864")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTSIZE", (0, 0), (-1, 0), 8.5),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.grey),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("FONTSIZE", (0, 1), (-1, -1), 8.5),
            ]
        )
    )
    return table


def generate_report(db, inspection_id: int) -> str:
    from app.models import Declaration, Evidence, Inspection, Violation

    inspection = db.get(Inspection, inspection_id)
    if inspection is None:
        raise ValueError("Inspection not found")
    payload = inspect_dict(db, inspection)
    declarations = db.query(Declaration).filter_by(inspection_id=inspection_id).all()
    violations = db.query(Violation).filter_by(inspection_id=inspection_id).all()
    evidences = db.query(Evidence).filter_by(inspection_id=inspection_id).all()

    Path(settings.report_dir).mkdir(parents=True, exist_ok=True)
    filename = f"inspection_{inspection_id}_{datetime.now():%Y%m%d_%H%M%S}.pdf"
    path = os.path.join(settings.report_dir, filename)
    styles = _style_sheet()

    doc = SimpleDocTemplate(path, pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm, topMargin=16 * mm, bottomMargin=16 * mm)
    story: list = []

    story.append(Paragraph("INSPECTION REPORT", styles["TitleLm"]))
    story.append(Paragraph("Legal Metrology (Packaged Commodities) Rules, 2011 - AI-assisted compliance inspection", styles["Subtitle"]))
    story.append(Spacer(1, 6))

    product = payload.get("product") or {}
    created = payload.get("created_at") or ""
    story.append(_table(
        ["Inspection Details", "Value"],
        [
            ["Inspection ID", str(inspection_id)],
            ["Date / Time", str(created)],
            ["Inspector ID", str(inspection.inspector_id or "N/A")],
            ["Image", str(inspection.image_path or "N/A")],
        ],
    ))

    story.append(Paragraph("Product Information", styles["Section"]))
    story.append(_table(
        ["Field", "Value"],
        [
            ["Name", str(product.get("name") or "N/A")],
            ["MRP", str(product.get("mrp") or "N/A")],
            ["Net Quantity", str(product.get("net_quantity") or "N/A")],
            ["Manufacturer", str(product.get("manufacturer") or "N/A")],
            ["Packer", str(product.get("packer") or "N/A")],
            ["Importer", str(product.get("importer") or "N/A")],
        ],
    ))

    story.append(Paragraph("Detected Declarations", styles["Section"]))
    story.append(_table(
        ["Declaration", "Value", "Present", "Confidence"],
        [
            [d.type, str(d.value or ""), "Yes" if d.present else "No", f"{d.confidence:.2f}"]
            for d in declarations
        ],
    ))

    story.append(Paragraph("Rule Results", styles["Section"]))
    outcome_rows = []
    for result in payload.get("rule_results", []):
        outcome_rows.append([result["rule_id"], result["result"], result["reason"]])
    if outcome_rows:
        story.append(_table(["Rule", "Result", "Reason"], outcome_rows))

    raw_ai = inspection.raw_ai_json or {}
    readability = raw_ai.get("readability", [])
    if readability:
        story.append(Paragraph("Font Size & Readability (Rule 7)", styles["Section"]))
        story.append(_table(
            ["Declaration", "Height (mm)", "Minimum (mm)", "Readable", "Note"],
            [
                [
                    item.get("declaration", ""),
                    f"{item.get('height_mm') or 0.0:.2f}",
                    f"{item.get('min_height_mm') or 0.0:.2f}",
                    "Yes" if item.get("readable") else "No",
                    str(item.get("note", "")),
                ]
                for item in readability
            ],
        ))

    placement = raw_ai.get("placement", [])
    if placement:
        story.append(Paragraph("Placement & Principal Display Panel (Rule 8)", styles["Section"]))
        story.append(_table(
            ["Declaration", "Inside PDP", "Clear Space", "Not Truncated", "Note"],
            [
                [
                    item.get("declaration", ""),
                    "Yes" if item.get("inside_pdp") else "No",
                    "OK" if item.get("clear_space_ok") is True else ("Check" if item.get("clear_space_ok") is False else "N/A"),
                    "Yes" if item.get("margin_ok") else "No",
                    str(item.get("note", "")),
                ]
                for item in placement
            ],
        ))

    story.append(Paragraph("Violations", styles["Section"]))
    story.append(_table(
        ["Rule", "Description", "Severity", "Status"],
        [[v.rule_id or "", v.description, v.severity, v.status] for v in violations],
    ))

    story.append(Paragraph("AI Confidence", styles["Section"]))
    story.append(_table(
        ["Item", "Value"],
        [
            ["Overall Confidence", f"{inspection.overall_confidence or 0.0:.2f}"],
            ["AI Verdict", str(inspection.ai_verdict or "N/A")],
            ["Language Detected", str(inspection.language or "N/A")],
            ["Processing Time (s)", f"{inspection.processing_time or 0.0:.3f}"],
        ],
    ))

    story.append(Paragraph("Evidence Images", styles["Section"]))
    for evidence in evidences:
        if evidence.image_path and Path(evidence.image_path).exists():
            story.append(Image(str(Path(evidence.image_path).resolve()), width=90 * mm, height=110 * mm, kind="proportional"))
            story.append(Spacer(1, 6))

    story.append(Paragraph("Inspector Decision", styles["Section"]))
    story.append(_table(
        ["Item", "Value"],
        [
            ["AI Recommendation", str(payload.get("compliance", {}).get("status") or "N/A")],
            ["Risk Level", f"{inspection.risk_level or 'N/A'} - {inspection.risk_reason or ''}"],
            ["Inspector Decision", str(inspection.inspector_decision or "PENDING")],
            ["Decision Reason", str(inspection.decision_reason or "")],
            ["Decision Time", str(inspection.decision_time or "")],
        ],
    ))

    story.append(Spacer(1, 12))
    story.append(Paragraph("Report generated automatically by the Legal Metrology Compliance System. AI results are recommendations and do not carry legal validity without inspector verification.", styles["Subtitle"]))

    if outcome_rows:
        story.append(PageBreak())
    doc.build(story)
    return path