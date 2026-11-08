from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.schemas.assistant import AskRequest, AskResponse, Citation

router = APIRouter(prefix="/assistant", tags=["Assistant"])


@router.post("/ask", response_model=AskResponse)
async def ask_assistant(payload: AskRequest, db: AsyncSession = Depends(get_db)):
    """Hỏi đáp trợ lý học tập RAG có trích dẫn nguồn."""
    # TODO Thành viên 2:
    # 1. Tạo embedding cho câu hỏi payload.question bằng Gemini text-embedding-004.
    # 2. Truy vấn top chunks liên quan từ bảng document_chunks (pgvector).
    # 3. Tạo prompt gửi Gemini để sinh câu trả lời và trích xuất citations chính xác.
    # 4. Trả về đúng schema với citations hoặc found_in_context=False nếu không có nguồn.

    # Stub mẫu phản hồi
    return AskResponse(
        answer="StudyFlow AI Assistant đã sẵn sàng. Chờ Thành viên 2 kết nối Gemini API để trả lời câu hỏi của bạn.",
        found_in_context=True,
        citations=[],
    )

