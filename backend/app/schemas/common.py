from pydantic import BaseModel
from typing import Optional, Any, List


class ValidationErrorItem(BaseModel):
    field: Optional[str] = None
    message: str


class ErrorDetail(BaseModel):
    code: str
    message: str
    field: Optional[str] = None
    errors: Optional[List[ValidationErrorItem]] = None


class StandardErrorEnvelope(BaseModel):
    detail: ErrorDetail

