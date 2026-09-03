from datetime import datetime

from pydantic import BaseModel, ConfigDict


class ProductBase(BaseModel):
    name: str | None = None
    manufacturer: str | None = None
    packer: str | None = None
    importer: str | None = None
    net_quantity: str | None = None
    mrp: str | None = None


class ProductCreate(ProductBase):
    pass


class ProductUpdate(ProductBase):
    pass


class ProductOut(ProductBase):
    model_config = ConfigDict(from_attributes=True)
    id: int
    created_at: datetime


class ProductHistoryOut(BaseModel):
    product_id: int
    total_inspections: int
    total_violations: int
    risk_level: str | None
    history: list["InspectionOut"]


class InspectionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    product_id: int | None
    inspector_id: int | None
    image_path: str | None
    status: str
    compliance_status: str | None
    risk_level: str | None
    risk_reason: str | None
    overall_confidence: float | None
    ai_verdict: str | None
    language: str | None
    inspector_decision: str | None
    decision_reason: str | None
    created_at: datetime
    completed_at: datetime | None


class DeclarationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    type: str
    value: str | None
    confidence: float
    bbox: list | None
    present: bool


class ViolationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    rule_id: str | None
    type: str
    description: str
    severity: str
    confidence: float | None
    status: str


class EvidenceOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    violation_id: int | None
    image_path: str
    bbox: list | None
    confidence: float | None
    created_at: datetime


class InspectionDetailOut(InspectionOut):
    declarations: list[DeclarationOut]
    violations: list[ViolationOut]
    evidences: list[EvidenceOut]


class DecisionRequest(BaseModel):
    decision: str
    reason: str | None = None


ProductHistoryOut.model_rebuild()