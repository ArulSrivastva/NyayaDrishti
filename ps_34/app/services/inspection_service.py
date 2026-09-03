from __future__ import annotations

import os
import time
import uuid
from pathlib import Path
from typing import Optional

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

import app.rules.engine as rules_engine
from app.ai.pipeline import AiPipeline
from app.config import get_settings
from app.models import Declaration, Evidence, Inspection, Product, User, Violation
from app.rules.risk import risk_level

settings = get_settings()


def ensure_dirs() -> None:
    for directory in (settings.upload_dir, settings.evidence_dir, settings.report_dir):
        Path(directory).mkdir(parents=True, exist_ok=True)


def save_upload(data: bytes, original_name: str = "") -> str:
    ensure_dirs()
    ext = Path(original_name or "upload.jpg").suffix or ".jpg"
    name = f"insp_{int(time.time() * 1000)}_{uuid.uuid4().hex[:8]}{ext}"
    path = os.path.join(settings.upload_dir, name)
    with open(path, "wb") as handle:
        handle.write(data)
    return path


def _find_or_create_product(db: Session, ai_result, product_name: Optional[str]) -> Product:
    name = product_name or (ai_result.product.name if ai_result.product.name else None)
    if name:
        existing = db.scalar(select(Product).where(Product.name == name))
        if existing:
            return existing
    product = Product(
        name=name,
        manufacturer=_value(ai_result.product.manufacturer),
        packer=_value(ai_result.product.packer),
        importer=_value(ai_result.product.importer),
        net_quantity=_value(ai_result.product.net_quantity),
        mrp=_value(ai_result.product.mrp),
    )
    db.add(product)
    db.flush()
    return product


def _value(field) -> Optional[str]:
    if field is None:
        return None
    return field.value if field.value else None


def _previous_violation_rule_ids(db: Session, product_id: int) -> list[list[str]]:
    inspections = db.scalars(
        select(Inspection).where(Inspection.product_id == product_id).order_by(Inspection.created_at)
    ).all()
    rows = []
    for inspection in inspections:
        rules = db.scalars(select(Violation.rule_id).where(Violation.inspection_id == inspection.id)).all()
        rows.append([rule_id for rule_id in rules if rule_id])
    return rows


def run_inspection(
    db: Session,
    user: User,
    image_data: bytes,
    original_name: str = "",
    product_name: Optional[str] = None,
    ocr_engine: Optional[str] = None,
) -> Inspection:
    ensure_dirs()
    image_path = save_upload(image_data, original_name)
    run_id = uuid.uuid4().hex

    config = {
        "ocr_engine": ocr_engine or settings.ocr_engine,
        "ocr_confidence_threshold": settings.ocr_confidence_threshold,
        "scan_dpi": settings.scan_dpi,
        "evidence_dir": settings.evidence_dir,
        "auto_verdict_threshold": settings.auto_verdict_threshold,
        "review_verdict_threshold": settings.review_verdict_threshold,
    }
    try:
        ai_result = AiPipeline(config).inspect_from_path(image_path, run_id=run_id)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=422, detail=f"AI inspection failed: {exc}") from exc

    product = _find_or_create_product(db, ai_result, product_name)
    previous = _previous_violation_rule_ids(db, product.id)

    outcome = rules_engine.evaluate(ai_result)
    violated_rules = outcome.violations
    rule_ids = [r.rule_id for r in violated_rules]
    severities = [r.severity for r in violated_rules]
    level, reason = risk_level(rule_ids, previous, severities)

    inspection = Inspection(
        product_id=product.id,
        inspector_id=user.id,
        image_path=image_path,
        status="pending",
        compliance_status=outcome.status,
        risk_level=level,
        risk_reason=reason,
        overall_confidence=ai_result.confidence.overall,
        ai_verdict=ai_result.confidence.verdict,
        language=ai_result.metadata.language,
        processing_time=ai_result.metadata.processing_time,
        raw_ai_json=ai_result.to_dict(),
        rule_results=[r.to_dict() for r in outcome.results],
    )
    db.add(inspection)
    db.flush()

    for declaration in ai_result.declarations:
        db.add(
            Declaration(
                inspection_id=inspection.id,
                type=declaration.type,
                value=declaration.value,
                confidence=declaration.confidence,
                bbox=declaration.bbox,
                present=declaration.present,
            )
        )

    violation_by_rule: dict[str, Violation] = {}
    for result in outcome.results:
        if result.result not in ("FAIL", "REVIEW"):
            continue
        violation = Violation(
            inspection_id=inspection.id,
            rule_id=result.rule_id,
            type=_rule_field(result.rule_id),
            description=result.reason,
            severity=result.severity,
            status="open",
        )
        db.add(violation)
        db.flush()
        violation_by_rule[result.rule_id] = violation

    ai_violation_by_rule: dict[str, Violation] = {}
    for item in ai_result.violations:
        if item.rule_id in violation_by_rule:
            continue
        if item.rule_id in ai_violation_by_rule:
            continue
        violation = Violation(
            inspection_id=inspection.id,
            rule_id=item.rule_id,
            type=item.rule_id,
            description=item.description,
            severity=item.severity,
            status="open",
        )
        db.add(violation)
        db.flush()
        ai_violation_by_rule[item.rule_id] = violation

    for evidence_item in ai_result.evidence:
        target = violation_by_rule.get(evidence_item.rule_id) or ai_violation_by_rule.get(evidence_item.rule_id)
        db.add(
            Evidence(
                violation_id=target.id if target else None,
                inspection_id=inspection.id,
                image_path=evidence_item.evidence_image,
                bbox=evidence_item.bbox,
                confidence=evidence_item.confidence,
            )
        )

    if outcome.status == "PASS":
        inspection.status = "completed"
        inspection.completed_at = inspection.created_at

    db.commit()
    db.refresh(inspection)
    return inspection


def _rule_field(rule_id: str) -> str:
    return rule_id.split("_")[-1] if "_" in rule_id else rule_id


class InspectionService:
    @staticmethod
    def get(db: Session, inspection_id: int) -> Inspection | None:
        return db.get(Inspection, inspection_id)

    @staticmethod
    def list(db: Session, skip: int = 0, limit: int = 100) -> list[Inspection]:
        return list(db.scalars(select(Inspection).order_by(Inspection.id.desc()).offset(skip).limit(limit)))

    @staticmethod
    def set_decision(db: Session, inspection: Inspection, decision: str, reason: Optional[str], user: User) -> Inspection:
        normalized = decision.upper()
        if normalized not in ("PASS", "FAIL", "REVIEW", "ACCEPT", "REJECT"):
            raise HTTPException(status_code=422, detail="Decision must be PASS, FAIL, REVIEW, ACCEPT or REJECT")
        if normalized == "ACCEPT":
            normalized = "PASS"
        elif normalized == "REJECT":
            normalized = "FAIL"
        from datetime import datetime, timezone

        inspection.inspector_decision = normalized
        inspection.decision_reason = reason
        inspection.decision_time = datetime.now(timezone.utc)
        inspection.status = "completed"
        inspection.completed_at = inspection.decision_time
        db.commit()
        db.refresh(inspection)
        return inspection


def get_inspection_with_relations(db: Session, inspection_id: int) -> dict:
    inspection = db.get(Inspection, inspection_id)
    if inspection is None:
        return {}
    return inspect_dict(db, inspection)


def inspect_dict(db: Session, inspection: Inspection) -> dict:
    declarations = db.scalars(
        select(Declaration).where(Declaration.inspection_id == inspection.id).order_by(Declaration.id)
    ).all()
    violations = db.scalars(
        select(Violation).where(Violation.inspection_id == inspection.id).order_by(Violation.id)
    ).all()
    evidences = db.scalars(
        select(Evidence).where(Evidence.inspection_id == inspection.id).order_by(Evidence.id)
    ).all()

    def serialize(mapping):
        def row(obj):
            values = {column.name: getattr(obj, column.name) for column in obj.__table__.columns}
            return values

        return [row(obj) for obj in mapping]

    return {
        "inspection_id": inspection.id,
        "product": serialize([inspection.product])[0] if inspection.product else None,
        "declarations": serialize(declarations),
        "violations": serialize(violations),
        "evidence": serialize(evidences),
        "rule_results": inspection.rule_results or [],
        "risk": {"level": inspection.risk_level, "reason": inspection.risk_reason},
        "confidence": {"overall": inspection.overall_confidence, "verdict": inspection.ai_verdict},
        "compliance": {"status": inspection.compliance_status},
        "inspector": {
            "id": inspection.inspector_id,
            "decision": inspection.inspector_decision,
            "reason": inspection.decision_reason,
        },
        "image_path": inspection.image_path,
        "created_at": inspection.created_at.isoformat() if inspection.created_at else None,
    }


def history_for_product(db: Session, product_id: int) -> dict:
    product = db.get(Product, product_id)
    if product is None:
        return {}
    inspections = db.scalars(
        select(Inspection).where(Inspection.product_id == product_id).order_by(Inspection.id.asc())
    ).all()
    rows = []
    total_violations = 0
    for inspection in inspections:
        violations = db.scalars(
            select(Violation).where(Violation.inspection_id == inspection.id)
        ).all()
        total_violations += len([v for v in violations if v.severity == "violation"])
        rows.append(inspect_dict(db, inspection))
    previous = _previous_violation_rule_ids(db, product_id)
    level, reason = risk_level([], previous, [])
    return {
        "product_id": product_id,
        "product_name": product.name,
        "total_inspections": len(inspections),
        "total_violations": total_violations,
        "risk_level": level if inspections else None,
        "risk_reason": reason if inspections else None,
        "history": rows,
    }