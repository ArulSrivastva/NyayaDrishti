from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user
from app.models import Inspection, User
from app.schemas.inspection import DecisionRequest, InspectionDetailOut
from app.services.inspection_service import InspectionService, inspect_dict

router = APIRouter(prefix="/inspections", tags=["inspections"])


@router.get("", response_model=list[InspectionDetailOut])
def list_inspections(skip: int = 0, limit: int = 100, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    return InspectionService.list(db, skip=skip, limit=limit)


@router.get("/{inspection_id}", response_model=InspectionDetailOut)
def get_inspection(inspection_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    inspection = db.get(Inspection, inspection_id)
    if inspection is None:
        raise HTTPException(status_code=404, detail="Inspection not found")
    return inspection


@router.get("/{inspection_id}/full")
def inspection_full(inspection_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    if db.get(Inspection, inspection_id) is None:
        raise HTTPException(status_code=404, detail="Inspection not found")
    return inspect_dict(db, db.get(Inspection, inspection_id))


@router.post("/{inspection_id}/decision")
def post_decision(
    inspection_id: int,
    payload: DecisionRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    inspection = db.get(Inspection, inspection_id)
    if inspection is None:
        raise HTTPException(status_code=404, detail="Inspection not found")
    inspection = InspectionService.set_decision(db, inspection, payload.decision, payload.reason, user)
    return inspect_dict(db, inspection)