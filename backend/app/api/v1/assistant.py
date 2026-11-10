from typing import List
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.schemas.assistant import (
    AskRequest,
    AskResponse,
    AssistantMessageResponse,
    AssistantSessionResponse,
)
from app.services.gemini_service import AIServiceError
from app.services.rag_service import (
    ask_question_rag,
    delete_assistant_session,
    list_course_assistant_sessions,
    list_session_messages,
)

router = APIRouter(tags=["Assistant"])


@router.post("/assistant/ask", response_model=AskResponse)
async def ask_assistant(payload: AskRequest, db: AsyncSession = Depends(get_db)):
    """Hỏi đáp trợ lý học tập RAG có trích dẫn nguồn xác thực và tự động lưu vào phiên chat của môn học."""
    try:
        return await ask_question_rag(
            course_id=payload.course_id,
            document_id=payload.document_id,
            question=payload.question,
            db=db,
            history=payload.history,
            session_id=payload.session_id,
        )
    except AIServiceError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "AI_SERVICE_UNAVAILABLE", "message": str(exc)},
        ) from exc


@router.get("/courses/{course_id}/assistant/sessions", response_model=List[AssistantSessionResponse])
async def get_course_assistant_sessions(
    course_id: UUID,
    limit: int = Query(default=50, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
):
    """Liệt kê các phiên chat trợ lý AI thuộc riêng group môn học (mới nhất trước)."""
    return await list_course_assistant_sessions(course_id=course_id, db=db, limit=limit)


@router.get("/assistant/sessions/{session_id}/messages", response_model=List[AssistantMessageResponse])
async def get_assistant_session_messages(
    session_id: UUID,
    limit: int = Query(default=100, ge=1, le=200),
    db: AsyncSession = Depends(get_db),
):
    """Lấy toàn bộ tin nhắn (theo thứ tự thời gian tăng dần) của một phiên chat."""
    return await list_session_messages(session_id=session_id, db=db, limit=limit)


@router.delete("/assistant/sessions/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_assistant_session(session_id: UUID, db: AsyncSession = Depends(get_db)):
    """Xóa một phiên chat và toàn bộ lịch sử tin nhắn bên trong."""
    await delete_assistant_session(session_id=session_id, db=db)
    return None
