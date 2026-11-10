from pydantic import BaseModel, Field, field_validator
from uuid import UUID
from typing import Optional

from app.schemas.common import UtcDatetime


class CourseCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=200, description="Tên môn học")
    description: Optional[str] = Field(None, max_length=1000, description="Mô tả môn học")

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        cleaned = v.strip()
        if not cleaned:
            raise ValueError("Tên môn học không được để trống.")
        return cleaned

    @field_validator("description")
    @classmethod
    def normalize_description(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        cleaned = v.strip()
        return cleaned or None


class CourseUpdate(BaseModel):
    name: Optional[str] = Field(None, min_length=1, max_length=200)
    description: Optional[str] = Field(None, max_length=1000)

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        cleaned = v.strip()
        if not cleaned:
            raise ValueError("Tên môn học không được để trống.")
        return cleaned

    @field_validator("description")
    @classmethod
    def normalize_description(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        cleaned = v.strip()
        return cleaned or None


class CourseResponse(BaseModel):
    id: UUID
    name: str
    description: Optional[str] = None
    document_count: int = 0
    quiz_count: int = 0
    created_at: UtcDatetime

    class Config:
        from_attributes = True

