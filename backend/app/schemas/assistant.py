from pydantic import BaseModel, Field
from uuid import UUID
from typing import Literal, Optional, List


class ChatHistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(..., min_length=1, max_length=2000)


class AskRequest(BaseModel):
    course_id: UUID
    document_id: Optional[UUID] = None
    question: str = Field(..., min_length=2, max_length=1000)
    history: List[ChatHistoryMessage] = Field(default_factory=list, max_length=8)


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

