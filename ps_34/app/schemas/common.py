from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


class FieldValue(BaseModel):
    value: Optional[str] = None
    confidence: Optional[float] = None
    bbox: Optional[list[float]] = Field(default=None, description="x1, y1, x2, y2")
    raw: Optional[str] = None


class ProductFields(BaseModel):
    name: Optional[str] = None
    mrp: Optional[FieldValue] = None
    net_quantity: Optional[FieldValue] = None
    manufacturer: Optional[FieldValue] = None
    packer: Optional[FieldValue] = None
    importer: Optional[FieldValue] = None
    manufacturing_date: Optional[FieldValue] = None
    packing_date: Optional[FieldValue] = None
    consumer_care: Optional[FieldValue] = None
    commodity: Optional[FieldValue] = None
    unit_sale_price: Optional[FieldValue] = None


class Declaration(BaseModel):
    type: str
    present: bool
    confidence: float
    bbox: Optional[list[float]] = None
    value: Optional[str] = None


class Violation(BaseModel):
    rule_id: str
    description: str
    severity: str = "violation"
    confidence: Optional[float] = None
    bbox: Optional[list[float]] = None


class EvidenceItem(BaseModel):
    violation: str
    rule_id: Optional[str] = None
    bbox: Optional[list[float]] = None
    confidence: Optional[float] = None
    evidence_image: str


class ConfidenceBlock(BaseModel):
    overall: float = 0.0
    verdict: str = "MANUAL"


class ReadabilityItem(BaseModel):
    declaration: str
    present: bool
    height_px: Optional[float] = None
    height_mm: Optional[float] = None
    min_height_mm: float = 1.0
    readable: bool = True
    note: str = ""


class PlacementItem(BaseModel):
    declaration: str
    present: bool = False
    inside_pdp: bool = True
    clear_space_ok: Optional[bool] = None
    margin_ok: bool = True
    note: str = ""


class Metadata(BaseModel):
    language: str = "en"
    processing_time: float = 0.0
    ocr_lines: int = 0


class InspectionRequest(BaseModel):
    product_name: Optional[str] = None


class AIResult(BaseModel):
    product: ProductFields = Field(default_factory=ProductFields)
    declarations: list[Declaration] = Field(default_factory=list)
    violations: list[Violation] = Field(default_factory=list)
    evidence: list[EvidenceItem] = Field(default_factory=list)
    confidence: ConfidenceBlock = Field(default_factory=ConfidenceBlock)
    metadata: Metadata = Field(default_factory=Metadata)
    readability: list[ReadabilityItem] = Field(default_factory=list)
    placement: list[PlacementItem] = Field(default_factory=list)

    def to_dict(self) -> dict:
        return self.model_dump(exclude_none=False)