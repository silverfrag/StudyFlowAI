from pydantic import BaseModel, Field
from uuid import UUID
from typing import Literal, Optional, List

from app.schemas.common import UtcDatetime


class ChatHistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(..., min_length=1, max_length=2000)


class AskRequest(BaseModel):
    course_id: UUID
    session_id: Optional[UUID] = None
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
    session_id: Optional[UUID] = None
    session_title: Optional[str] = None
    answer: str
    found_in_context: bool
    citations: List[Citation] = []


class AssistantSessionResponse(BaseModel):
    id: UUID
    course_id: UUID
    document_id: Optional[UUID] = None
    document_title: Optional[str] = None
    title: str
    message_count: int = 0
    last_message_preview: Optional[str] = None
    created_at: UtcDatetime
    updated_at: UtcDatetime


class AssistantMessageResponse(BaseModel):
    id: UUID
    session_id: UUID
    course_id: UUID
    document_id: Optional[UUID] = None
    role: Literal["user", "assistant"]
    content: str
    found_in_context: bool = True
    citations: List[Citation] = []
    created_at: UtcDatetime

