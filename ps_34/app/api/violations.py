from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user
from app.models import Evidence, Inspection, User, Violation
from app.schemas.inspection import EvidenceOut, ViolationOut

router = APIRouter(tags=["violations"])


@router.get("/inspections/{inspection_id}/violations", response_model=list[ViolationOut])
def list_violations(inspection_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    if db.get(Inspection, inspection_id) is None:
        raise HTTPException(status_code=404, detail="Inspection not found")
    return list(db.scalars(select(Violation).where(Violation.inspection_id == inspection_id).order_by(Violation.id)))


@router.get("/violations/{violation_id}/evidence", response_model=list[EvidenceOut])
def list_evidence(violation_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    return list(db.scalars(select(Evidence).where(Evidence.violation_id == violation_id).order_by(Evidence.id)))