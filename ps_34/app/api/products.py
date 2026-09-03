from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user
from app.models import Product, User
from app.schemas.inspection import ProductCreate, ProductOut, ProductUpdate
from app.services import inspection_service

router = APIRouter(prefix="/products", tags=["products"])


@router.post("", response_model=ProductOut, status_code=201)
def create_product(payload: ProductCreate, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    product = Product(**payload.model_dump())
    db.add(product)
    db.commit()
    db.refresh(product)
    return product


@router.get("", response_model=list[ProductOut])
def list_products(skip: int = 0, limit: int = 100, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    return list(db.scalars(select(Product).order_by(Product.id.desc()).offset(skip).limit(limit)))


@router.get("/{product_id}", response_model=ProductOut)
def get_product(product_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    product = db.get(Product, product_id)
    if product is None:
        raise HTTPException(status_code=404, detail="Product not found")
    return product


@router.put("/{product_id}", response_model=ProductOut)
def update_product(product_id: int, payload: ProductUpdate, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    product = db.get(Product, product_id)
    if product is None:
        raise HTTPException(status_code=404, detail="Product not found")
    for key, value in payload.model_dump().items():
        setattr(product, key, value)
    db.commit()
    db.refresh(product)
    return product


@router.delete("/{product_id}", status_code=204)
def delete_product(product_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    product = db.get(Product, product_id)
    if product is None:
        raise HTTPException(status_code=404, detail="Product not found")
    db.delete(product)
    db.commit()


@router.get("/{product_id}/history")
def product_history(product_id: int, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    history = inspection_service.history_for_product(db, product_id)
    if not history:
        raise HTTPException(status_code=404, detail="Product not found")
    return history