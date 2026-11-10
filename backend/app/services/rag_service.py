from typing import List, Optional, Tuple
from uuid import UUID

from fastapi import HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.course import Course
from app.models.document import Document, DocumentChunk
from app.schemas.assistant import AskResponse, Citation
from app.services.document_service import EMBEDDING_DIMENSIONS
from app.services.gemini_service import embed_texts, generate_structured_json

RAG_TOP_K = 5
MAX_SNIPPET_CHARS = 220

RAG_SYSTEM_INSTRUCTION = (
    "Bạn là trợ lý học tập AI của StudyFlow. Nhiệm vụ của bạn là trả lời câu hỏi của sinh viên "
    "DỰA HOÀN TOÀN vào các đoạn tài liệu học tập được cung cấp.\n"
    "Quy tắc bắt buộc:\n"
    "1. Chỉ sử dụng thông tin có thật trong các đoạn [ĐOẠN i] bên dưới. Tuyệt đối không suy diễn hoặc bịa kiến thức ngoài tài liệu.\n"
    "2. Trả lời bằng tiếng Việt rõ ràng, mạch lạc, đúng trọng tâm học thuật.\n"
    "3. Nếu các đoạn tài liệu không chứa thông tin để trả lời câu hỏi, đặt found_in_context = false, "
    "cited_indices = [], và viết answer: 'Nội dung này chưa được tìm thấy trong tài liệu đã nạp của môn học.'\n"
    "4. Nếu trả lời được từ tài liệu, đặt found_in_context = true và liệt kê chính xác các chỉ số đoạn [i] đã dùng vào cited_indices."
)


class _RagLLMOutput(BaseModel):
    found_in_context: bool = Field(
        ...,
        description="True chỉ khi các đoạn tài liệu được cung cấp chứa đủ căn cứ để trả lời câu hỏi.",
    )
    answer: str = Field(
        ...,
        description="Câu trả lời bằng tiếng Việt dựa trên tài liệu.",
    )
    cited_indices: List[int] = Field(
        default_factory=list,
        description="Danh sách chỉ số nguyên của các đoạn [i] được dùng làm căn cứ trả lời.",
    )


def _build_snippet(text: str, max_chars: int = MAX_SNIPPET_CHARS) -> str:
    cleaned = " ".join(text.split())
    if len(cleaned) <= max_chars:
        return cleaned
    truncated = cleaned[:max_chars].rsplit(" ", 1)[0].strip()
    return f"{truncated or cleaned[:max_chars]}..."


async def ask_question_rag(
    course_id: UUID,
    document_id: Optional[UUID],
    question: str,
    db: AsyncSession,
) -> AskResponse:
    """Truy vấn vector pgvector và sinh câu trả lời RAG có trích dẫn nguồn xác thực."""
    cleaned_question = question.strip()
    if len(cleaned_question) < 2:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "VALIDATION_ERROR", "message": "Câu hỏi cần có ít nhất 2 ký tự.", "field": "question"},
        )

    course = await db.get(Course, course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )

    if document_id is not None:
        doc = await db.get(Document, document_id)
        if not doc:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy tài liệu được chọn."},
            )
        if doc.course_id != course_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={
                    "code": "VALIDATION_ERROR",
                    "message": "Tài liệu được chọn không thuộc môn học hiện tại.",
                    "field": "document_id",
                },
            )
        if doc.status != "ready":
            status_vi = "đang xử lý" if doc.status == "processing" else "bị lỗi"
            return AskResponse(
                answer=f"Tài liệu '{doc.title}' hiện {status_vi}, chưa sẵn sàng để tra cứu.",
                found_in_context=False,
                citations=[],
            )

    # Kiểm tra trước xem phạm vi truy vấn đã có chunk embedding sẵn sàng hay chưa
    has_chunk_stmt = (
        select(DocumentChunk.id)
        .join(Document, DocumentChunk.document_id == Document.id)
        .where(
            DocumentChunk.course_id == course_id,
            Document.status == "ready",
            DocumentChunk.embedding.is_not(None),
        )
    )
    if document_id is not None:
        has_chunk_stmt = has_chunk_stmt.where(DocumentChunk.document_id == document_id)
    has_chunk_res = await db.execute(has_chunk_stmt.limit(1))
    if has_chunk_res.scalar_one_or_none() is None:
        return AskResponse(
            answer="Môn học này chưa có tài liệu nào ở trạng thái sẵn sàng để tra cứu. Hãy tải tệp PDF hoặc TXT lên và chờ xử lý xong.",
            found_in_context=False,
            citations=[],
        )

    # Nhúng câu hỏi thành vector 768 chiều để truy vấn cosine distance trên pgvector
    query_vectors = await embed_texts(
        [cleaned_question],
        task_type="RETRIEVAL_QUERY",
        dimensions=EMBEDDING_DIMENSIONS,
    )
    query_vec = query_vectors[0]

    stmt = (
        select(DocumentChunk, Document.title)
        .join(Document, DocumentChunk.document_id == Document.id)
        .where(
            DocumentChunk.course_id == course_id,
            Document.status == "ready",
            DocumentChunk.embedding.is_not(None),
        )
    )
    if document_id is not None:
        stmt = stmt.where(DocumentChunk.document_id == document_id)

    stmt = stmt.order_by(DocumentChunk.embedding.cosine_distance(query_vec)).limit(RAG_TOP_K)
    result = await db.execute(stmt)
    retrieved_rows: List[Tuple[DocumentChunk, str]] = list(result.all())

    if not retrieved_rows:
        return AskResponse(
            answer="Nội dung này chưa được tìm thấy trong tài liệu đã nạp của môn học.",
            found_in_context=False,
            citations=[],
        )

    context_blocks = []
    for idx, (chunk, doc_title) in enumerate(retrieved_rows):
        page_label = f"Trang {chunk.page_number}" if chunk.page_number is not None else "Không phân trang"
        context_blocks.append(f"[ĐOẠN {idx}] Tài liệu: {doc_title} | {page_label}\n{chunk.text}")

    user_prompt = (
        f"Môn học: {course.name}\n\n"
        f"Các đoạn tài liệu truy xuất được:\n"
        + "\n\n---\n\n".join(context_blocks)
        + f"\n\nCâu hỏi của người học: {cleaned_question}"
    )

    llm_output = await generate_structured_json(
        prompt=user_prompt,
        system_instruction=RAG_SYSTEM_INSTRUCTION,
        schema_cls=_RagLLMOutput,
    )

    if not llm_output.found_in_context:
        fallback_msg = "Nội dung này chưa được tìm thấy trong tài liệu đã nạp của môn học."
        return AskResponse(
            answer=llm_output.answer.strip() or fallback_msg,
            found_in_context=False,
            citations=[],
        )

    # Xác thực chặt chẽ các chỉ số trích dẫn để không bao giờ bịa citation
    seen_chunk_ids = set()
    verified_citations: List[Citation] = []
    for idx in llm_output.cited_indices:
        if isinstance(idx, int) and 0 <= idx < len(retrieved_rows):
            chunk, doc_title = retrieved_rows[idx]
            if chunk.id in seen_chunk_ids:
                continue
            seen_chunk_ids.add(chunk.id)
            verified_citations.append(
                Citation(
                    document_id=chunk.document_id,
                    document_title=doc_title,
                    page_number=chunk.page_number,
                    chunk_id=chunk.id,
                    snippet=_build_snippet(chunk.text),
                )
            )

    if not verified_citations:
        return AskResponse(
            answer="Nội dung này chưa được tìm thấy căn cứ rõ ràng trong tài liệu đã nạp của môn học.",
            found_in_context=False,
            citations=[],
        )

    return AskResponse(
        answer=llm_output.answer.strip(),
        found_in_context=True,
        citations=verified_citations,
    )

