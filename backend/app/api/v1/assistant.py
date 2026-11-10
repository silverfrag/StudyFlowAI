from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.schemas.assistant import AskRequest, AskResponse
from app.services.gemini_service import AIServiceError
from app.services.rag_service import ask_question_rag

router = APIRouter(prefix="/assistant", tags=["Assistant"])


@router.post("/ask", response_model=AskResponse)
async def ask_assistant(payload: AskRequest, db: AsyncSession = Depends(get_db)):
    """Hỏi đáp trợ lý học tập RAG có trích dẫn nguồn xác thực."""
    try:
        return await ask_question_rag(
            course_id=payload.course_id,
            document_id=payload.document_id,
            question=payload.question,
            db=db,
        )
    except AIServiceError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "AI_SERVICE_UNAVAILABLE", "message": str(exc)},
        ) from exc
