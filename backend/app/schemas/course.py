from pydantic import BaseModel, Field
from uuid import UUID
from datetime import datetime
from typing import Optional


class CourseCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=200, description="Tên môn học")
    description: Optional[str] = Field(None, max_length=1000, description="Mô tả môn học")


class CourseUpdate(BaseModel):
    name: Optional[str] = Field(None, min_length=1, max_length=200)
    description: Optional[str] = Field(None, max_length=1000)


class CourseResponse(BaseModel):
    id: UUID
    name: str
    description: Optional[str] = None
    document_count: int = 0
    quiz_count: int = 0
    created_at: datetime

    class Config:
        from_attributes = True

