from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import get_db
from app.deps import get_current_user
from app.models import Inspection, User
from app.services.report_service import generate_report

router = APIRouter(prefix="/reports", tags=["reports"])
settings = get_settings()


@router.get("/file/{filename}")
def download_report(filename: str):
    path = Path(settings.report_dir) / filename
    if not path.exists():
        raise HTTPException(status_code=404, detail="Report not found")
    return FileResponse(path, media_type="application/pdf", filename=filename)


@router.post("/{inspection_id}")
def create_report(inspection_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    if db.get(Inspection, inspection_id) is None:
        raise HTTPException(status_code=404, detail="Inspection not found")
    try:
        path = generate_report(db, inspection_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return {"report_path": path, "report_url": f"/reports/file/{Path(path).name}"}


@router.get("/{inspection_id}")
def get_report(inspection_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    if db.get(Inspection, inspection_id) is None:
        raise HTTPException(status_code=404, detail="Inspection not found")
    try:
        path = generate_report(db, inspection_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return FileResponse(path, media_type="application/pdf", filename=Path(path).name)