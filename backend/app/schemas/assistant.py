from pydantic import BaseModel, Field
from uuid import UUID
from typing import Optional, List


class AskRequest(BaseModel):
    course_id: UUID
    document_id: Optional[UUID] = None
    question: str = Field(..., min_length=2, max_length=1000)


class Citation(BaseModel):
    document_id: UUID
    document_title: str
    page_number: Optional[int] = None
    chunk_id: UUID
    snippet: str


class AskResponse(BaseModel):
    answer: str
    found_in_context: bool
    citations: List[Citation] = []

