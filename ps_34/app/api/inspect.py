from __future__ import annotations

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user
from app.models import User
from app.services.inspection_service import inspect_dict, run_inspection

router = APIRouter(tags=["inspect"])


@router.post("/inspect")
def inspect(
    file: UploadFile = File(...),
    product_name: str | None = Form(default=None),
    ocr_engine: str | None = Form(default=None),
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=415, detail="Only image uploads are supported")
    data = file.file.read()
    if not data:
        raise HTTPException(status_code=422, detail="Empty file upload")
    inspection = run_inspection(
        db=db,
        user=user,
        image_data=data,
        original_name=file.filename or "",
        product_name=product_name,
        ocr_engine=ocr_engine,
    )
    return inspect_dict(db, inspection)