from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import JSON, Boolean, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    full_name: Mapped[str] = mapped_column(String(255), nullable=False)
    role: Mapped[str] = mapped_column(String(32), default="inspector")
    hashed_password: Mapped[str] = mapped_column(String(255), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    inspections: Mapped[list["Inspection"]] = relationship(back_populates="inspector")


class Product(Base):
    __tablename__ = "products"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str | None] = mapped_column(String(255), nullable=True, index=True)
    manufacturer: Mapped[str | None] = mapped_column(String(255), nullable=True)
    packer: Mapped[str | None] = mapped_column(String(255), nullable=True)
    importer: Mapped[str | None] = mapped_column(String(255), nullable=True)
    net_quantity: Mapped[str | None] = mapped_column(String(64), nullable=True)
    mrp: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    inspections: Mapped[list["Inspection"]] = relationship(back_populates="product", cascade="all, delete-orphan")


class Inspection(Base):
    __tablename__ = "inspections"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    product_id: Mapped[int | None] = mapped_column(ForeignKey("products.id"), nullable=True, index=True)
    inspector_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)
    image_path: Mapped[str | None] = mapped_column(String(512), nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="pending")
    compliance_status: Mapped[str | None] = mapped_column(String(32), nullable=True)
    risk_level: Mapped[str | None] = mapped_column(String(16), nullable=True)
    risk_reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    overall_confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    ai_verdict: Mapped[str | None] = mapped_column(String(16), nullable=True)
    language: Mapped[str | None] = mapped_column(String(64), nullable=True)
    processing_time: Mapped[float | None] = mapped_column(Float, nullable=True)
    inspector_decision: Mapped[str | None] = mapped_column(String(16), nullable=True)
    decision_reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    decision_time: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    raw_ai_json: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    rule_results: Mapped[list | None] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    product: Mapped["Product"] = relationship(back_populates="inspections")
    inspector: Mapped["User"] = relationship(back_populates="inspections")
    declarations: Mapped[list["Declaration"]] = relationship(back_populates="inspection", cascade="all, delete-orphan")
    violations: Mapped[list["Violation"]] = relationship(back_populates="inspection", cascade="all, delete-orphan")
    evidences: Mapped[list["Evidence"]] = relationship(back_populates="inspection", cascade="all, delete-orphan")


class Declaration(Base):
    __tablename__ = "declarations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    inspection_id: Mapped[int] = mapped_column(ForeignKey("inspections.id"), index=True)
    type: Mapped[str] = mapped_column(String(64))
    value: Mapped[str | None] = mapped_column(Text, nullable=True)
    confidence: Mapped[float] = mapped_column(Float, default=0.0)
    bbox: Mapped[list | None] = mapped_column(JSON, nullable=True)
    present: Mapped[bool] = mapped_column(Boolean, default=False)

    inspection: Mapped["Inspection"] = relationship(back_populates="declarations")


class Violation(Base):
    __tablename__ = "violations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    inspection_id: Mapped[int] = mapped_column(ForeignKey("inspections.id"), index=True)
    rule_id: Mapped[str | None] = mapped_column(String(32), index=True)
    type: Mapped[str] = mapped_column(String(64))
    description: Mapped[str] = mapped_column(Text)
    severity: Mapped[str] = mapped_column(String(16), default="violation")
    confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    status: Mapped[str] = mapped_column(String(16), default="open")

    inspection: Mapped["Inspection"] = relationship(back_populates="violations")
    evidence: Mapped[list["Evidence"]] = relationship(back_populates="violation", cascade="all, delete-orphan")


class Evidence(Base):
    __tablename__ = "evidences"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    violation_id: Mapped[int | None] = mapped_column(ForeignKey("violations.id"), index=True, nullable=True)
    inspection_id: Mapped[int] = mapped_column(ForeignKey("inspections.id"), index=True)
    image_path: Mapped[str] = mapped_column(String(512))
    bbox: Mapped[list | None] = mapped_column(JSON, nullable=True)
    confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    violation: Mapped["Violation"] = relationship(back_populates="evidence")
    inspection: Mapped["Inspection"] = relationship(back_populates="evidences")