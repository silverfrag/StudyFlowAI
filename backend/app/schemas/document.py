from pydantic import BaseModel
from uuid import UUID
from typing import Optional, Literal

from app.schemas.common import UtcDatetime

DocumentStatus = Literal["processing", "ready", "error"]


class DocumentResponse(BaseModel):
    id: UUID
    course_id: UUID
    title: str
    file_size: int
    mime_type: str
    status: DocumentStatus
    error_message: Optional[str] = None
    created_at: UtcDatetime

    class Config:
        from_attributes = True


class DocumentStatusResponse(BaseModel):
    id: UUID
    status: DocumentStatus
    error_message: Optional[str] = None

