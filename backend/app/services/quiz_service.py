from typing import Dict, List, Literal, Optional, Tuple
from uuid import UUID

from fastapi import HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.course import Course
from app.models.document import Document, DocumentChunk
from app.models.quiz import Question, Quiz
from app.schemas.quiz import (
    OptionSchema,
    QuizClientResponse,
    QuizCreateRequest,
    QuizQuestionClientResponse,
)
from app.services.document_service import EMBEDDING_DIMENSIONS
from app.services.gemini_service import AIServiceError, embed_texts, generate_structured_json
from app.services.recommendation_service import canonicalize_topic_display, normalize_topic_group_key

CANONICAL_OPTION_IDS = ("A", "B", "C", "D")
MAX_CONTEXT_CHUNKS_FOR_QUIZ = 8

QUIZ_SYSTEM_INSTRUCTION = (
    "Bạn là chuyên gia ra đề trắc nghiệm học thuật cho ứng dụng StudyFlow AI.\n"
    "Hãy tạo bộ câu hỏi trắc nghiệm 4 lựa chọn (A, B, C, D) bằng tiếng Việt "
    "DỰA HOÀN TOÀN vào các đoạn tài liệu học tập được cung cấp.\n"
    "Quy tắc bắt buộc:\n"
    "1. Tạo CHÍNH XÁC số lượng câu hỏi được yêu cầu.\n"
    "2. Mỗi câu hỏi có ĐÚNG 4 phương án với id lần lượt là 'A', 'B', 'C', 'D' và duy nhất 1 đáp án đúng.\n"
    "3. Không tạo đáp án kiểu 'Tất cả các đáp án trên' nếu không thật cần thiết; các phương án nhiễu phải hợp lý và phân biệt rõ.\n"
    "4. Đặt trường topic (dưới 50 ký tự) THEO QUY TẮC NHẤT QUÁN:\n"
    "   - Nếu người dùng đã chỉ định 'Chủ đề trọng tâm yêu cầu', hãy gán ĐÚNG tên chủ đề đó cho toàn bộ câu hỏi trong bài quiz để theo dõi tiến độ ôn tập chính xác.\n"
    "   - Nếu là bài kiểm tra tổng hợp (không chỉ định chủ đề trọng tâm), TUYỆT ĐỐI KHÔNG đặt mỗi câu một topic khác nhau; "
    "hãy gom các câu hỏi vào CHỈ 2–3 chủ đề kiến thức lớn (mỗi chủ đề có ít nhất 2 câu khi số câu >= 4) và ưu tiên tái sử dụng đúng tên chủ đề đã có của môn học nếu cùng nội dung.\n"
    "5. Điền giải thích ngắn gọn (explanation) và chỉ số đoạn [ĐOẠN i] (source_chunk_index) làm căn cứ; nếu không gắn một đoạn cụ thể thì đặt -1."
)


class _QuizOptionLLM(BaseModel):
    id: Literal["A", "B", "C", "D"]
    text: str


class _QuizQuestionLLM(BaseModel):
    prompt: str
    topic: str
    options: List[_QuizOptionLLM]
    correct_option_id: Literal["A", "B", "C", "D"]
    explanation: str
    source_chunk_index: int


class _QuizLLMOutput(BaseModel):
    questions: List[_QuizQuestionLLM]


async def _select_quiz_chunks(
    course_id: UUID,
    document_id: Optional[UUID],
    topic: Optional[str],
    db: AsyncSession,
) -> List[Tuple[DocumentChunk, str]]:
    base_stmt = (
        select(DocumentChunk, Document.title)
        .join(Document, DocumentChunk.document_id == Document.id)
        .where(
            DocumentChunk.course_id == course_id,
            Document.status == "ready",
        )
    )
    if document_id is not None:
        base_stmt = base_stmt.where(DocumentChunk.document_id == document_id)

    cleaned_topic = (topic or "").strip()
    if cleaned_topic:
        # Nếu có chủ đề trọng tâm và chunks có embedding, ưu tiên lấy theo cosine distance
        embedded_stmt = base_stmt.where(DocumentChunk.embedding.is_not(None))
        check_emb = await db.execute(embedded_stmt.limit(1))
        if check_emb.scalar_one_or_none() is not None:
            query_vecs = await embed_texts(
                [cleaned_topic],
                task_type="RETRIEVAL_QUERY",
                dimensions=EMBEDDING_DIMENSIONS,
            )
            ranked_stmt = (
                embedded_stmt.order_by(DocumentChunk.embedding.cosine_distance(query_vecs[0]))
                .limit(MAX_CONTEXT_CHUNKS_FOR_QUIZ)
            )
            res = await db.execute(ranked_stmt)
            rows = list(res.all())
            if rows:
                return rows

    all_stmt = base_stmt.order_by(Document.created_at.asc(), DocumentChunk.ordinal.asc())
    res = await db.execute(all_stmt)
    all_rows = list(res.all())
    if len(all_rows) <= MAX_CONTEXT_CHUNKS_FOR_QUIZ:
        return all_rows

    # Lấy mẫu rải đều xuyên suốt tài liệu để bao quát kiến thức
    sampled: List[Tuple[DocumentChunk, str]] = []
    step = len(all_rows) / float(MAX_CONTEXT_CHUNKS_FOR_QUIZ)
    for i in range(MAX_CONTEXT_CHUNKS_FOR_QUIZ):
        sampled.append(all_rows[int(i * step)])
    return sampled


def _validate_and_normalize_questions(
    output: _QuizLLMOutput,
    expected_count: int,
    fallback_topic: str,
    preferred_topic: Optional[str] = None,
    existing_topic_map: Optional[Dict[str, str]] = None,
) -> List[Dict]:
    if len(output.questions) < expected_count:
        raise ValueError(
            f"AI chỉ sinh {len(output.questions)}/{expected_count} câu hỏi."
        )

    cleaned_preferred = canonicalize_topic_display(preferred_topic or "")
    normalized: List[Dict] = []
    for item in output.questions[:expected_count]:
        prompt_text = item.prompt.strip()
        if not prompt_text:
            raise ValueError("Câu hỏi sinh ra bị trống nội dung.")

        opt_map: Dict[str, str] = {}
        for opt in item.options:
            opt_id = opt.id.upper()
            opt_text = opt.text.strip()
            if opt_id in CANONICAL_OPTION_IDS and opt_text and opt_id not in opt_map:
                opt_map[opt_id] = opt_text

        if set(opt_map.keys()) != set(CANONICAL_OPTION_IDS):
            raise ValueError("Câu hỏi không đủ 4 phương án hợp lệ A, B, C, D.")

        correct_id = item.correct_option_id.upper()
        if correct_id not in CANONICAL_OPTION_IDS:
            raise ValueError("Đáp án đúng nằm ngoài A, B, C, D.")

        if cleaned_preferred:
            topic_value = cleaned_preferred[:200]
        else:
            raw_topic = canonicalize_topic_display(item.topic) or fallback_topic
            if existing_topic_map:
                matched = existing_topic_map.get(normalize_topic_group_key(raw_topic))
                if matched:
                    raw_topic = matched
            topic_value = raw_topic[:200]

        explanation_text = (
            item.explanation.strip()
            or f"Đáp án đúng là {correct_id} theo nội dung tài liệu môn học."
        )

        normalized.append(
            {
                "prompt": prompt_text,
                "topic": topic_value,
                "options": [{"id": oid, "text": opt_map[oid]} for oid in CANONICAL_OPTION_IDS],
                "correct_option_id": correct_id,
                "explanation": explanation_text,
                "source_chunk_index": item.source_chunk_index,
            }
        )

    # Nếu bài quiz tổng hợp >= 4 câu mà AI vẫn chia vụn quá 3 chủ đề đơn lẻ, gom các topic xuất hiện 1 lần về chủ đề chính gần nhất
    if not cleaned_preferred and len(normalized) >= 4:
        group_counts: Dict[str, int] = {}
        group_display: Dict[str, str] = {}
        for q in normalized:
            gk = normalize_topic_group_key(q["topic"])
            group_counts[gk] = group_counts.get(gk, 0) + 1
            group_display.setdefault(gk, q["topic"])

        if len(group_counts) > 3:
            major_groups = [
                gk for gk, cnt in sorted(group_counts.items(), key=lambda kv: (-kv[1], kv[0]))
                if cnt >= 2
            ]
            if not major_groups:
                major_groups = [gk for gk, _ in sorted(group_counts.items(), key=lambda kv: (-kv[1], kv[0]))[:2]]
            major_groups = major_groups[:3]

            for idx, q in enumerate(normalized):
                gk = normalize_topic_group_key(q["topic"])
                if gk not in major_groups:
                    target_group = major_groups[idx % len(major_groups)]
                    # Ưu tiên chủ đề của câu kế bên cùng source_chunk_index nếu có
                    for other in normalized:
                        other_gk = normalize_topic_group_key(other["topic"])
                        if (
                            other is not q
                            and other_gk in major_groups
                            and other["source_chunk_index"] == q["source_chunk_index"]
                            and q["source_chunk_index"] >= 0
                        ):
                            target_group = other_gk
                            break
                    q["topic"] = group_display[target_group]

    return normalized


async def generate_quiz_with_ai(
    payload: QuizCreateRequest,
    db: AsyncSession,
) -> QuizClientResponse:
    """Sinh đề kiểm tra MCQ từ tài liệu đã xử lý và lưu vào PostgreSQL."""
    course = await db.get(Course, payload.course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )

    if payload.document_id is not None:
        doc = await db.get(Document, payload.document_id)
        if not doc:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy tài liệu được chọn."},
            )
        if doc.course_id != payload.course_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={
                    "code": "VALIDATION_ERROR",
                    "message": "Tài liệu được chọn không thuộc môn học hiện tại.",
                    "field": "document_id",
                },
            )
        if doc.status != "ready":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={
                    "code": "VALIDATION_ERROR",
                    "message": "Tài liệu được chọn chưa xử lý xong để tạo bài kiểm tra.",
                    "field": "document_id",
                },
            )

    selected_chunks = await _select_quiz_chunks(
        course_id=payload.course_id,
        document_id=payload.document_id,
        topic=payload.topic,
        db=db,
    )
    if not selected_chunks:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "code": "VALIDATION_ERROR",
                "message": "Môn học chưa có tài liệu nào ở trạng thái sẵn sàng để tạo quiz. Hãy tải tệp PDF/TXT và chờ xử lý xong.",
            },
        )

    topic_label = canonicalize_topic_display(payload.topic or "")
    fallback_topic = topic_label or course.name or "Kiến thức tổng hợp"

    existing_topics_res = await db.execute(
        select(Question.topic, func.count(Question.id).label("cnt"))
        .join(Quiz, Question.quiz_id == Quiz.id)
        .where(Quiz.course_id == payload.course_id)
        .group_by(Question.topic)
        .order_by(func.count(Question.id).desc())
        .limit(15)
    )
    existing_topic_map: Dict[str, str] = {}
    existing_topics_list: List[str] = []
    for raw_t, _ in existing_topics_res.all():
        cleaned_t = canonicalize_topic_display(raw_t)
        if not cleaned_t:
            continue
        gk = normalize_topic_group_key(cleaned_t)
        if gk not in existing_topic_map:
            existing_topic_map[gk] = cleaned_t
            existing_topics_list.append(cleaned_t)

    context_blocks = []
    for idx, (chunk, doc_title) in enumerate(selected_chunks):
        page_label = f"Trang {chunk.page_number}" if chunk.page_number is not None else "Không phân trang"
        context_blocks.append(f"[ĐOẠN {idx}] Tài liệu: {doc_title} | {page_label}\n{chunk.text}")

    if topic_label:
        focus_instruction = (
            f"Chủ đề trọng tâm yêu cầu: '{topic_label}'. "
            f"Hãy gán ĐÚNG trường topic = '{topic_label}' cho toàn bộ {payload.question_count} câu hỏi."
        )
    else:
        existing_hint = (
            f" Các chủ đề đã có của môn học (ưu tiên dùng lại nếu cùng nội dung): {', '.join(existing_topics_list[:8])}."
            if existing_topics_list
            else ""
        )
        focus_instruction = (
            f"Phân bổ {payload.question_count} câu hỏi đều qua 2–3 chủ đề kiến thức lớn của tài liệu "
            f"(KHÔNG tạo mỗi câu 1 topic riêng).{existing_hint}"
        )

    user_prompt = (
        f"Môn học: {course.name}\n"
        f"{focus_instruction}\n"
        f"Số câu hỏi cần tạo: {payload.question_count} câu.\n\n"
        f"Nội dung tài liệu tham khảo:\n"
        + "\n\n---\n\n".join(context_blocks)
    )

    validated_questions: Optional[List[Dict]] = None
    last_validation_err: Optional[str] = None
    for attempt_idx in range(2):
        prompt_to_send = user_prompt
        if attempt_idx == 1 and last_validation_err:
            prompt_to_send += (
                f"\n\nLƯU Ý SỬA LỖI TỪ LẦN TRƯỚC ({last_validation_err}): "
                f"Bắt buộc trả về đủ {payload.question_count} câu hỏi và mỗi câu có đủ 4 lựa chọn A, B, C, D không trống."
            )

        raw_output = await generate_structured_json(
            prompt=prompt_to_send,
            system_instruction=QUIZ_SYSTEM_INSTRUCTION,
            schema_cls=_QuizLLMOutput,
        )
        try:
            validated_questions = _validate_and_normalize_questions(
                output=raw_output,
                expected_count=payload.question_count,
                fallback_topic=fallback_topic,
                preferred_topic=topic_label or None,
                existing_topic_map=existing_topic_map,
            )
            break
        except ValueError as exc:
            last_validation_err = str(exc)

    if validated_questions is None:
        raise AIServiceError(
            f"AI không sinh đủ bộ câu hỏi hợp lệ theo yêu cầu ({last_validation_err}). Vui lòng thử lại."
        )

    quiz_title = f"Quiz: {topic_label or 'Tổng hợp kiến thức môn học'}"[:255]
    quiz = Quiz(
        course_id=payload.course_id,
        title=quiz_title,
        total_questions=len(validated_questions),
    )
    db.add(quiz)
    await db.flush()

    created_questions: List[Question] = []
    for item in validated_questions:
        chunk_idx = item["source_chunk_index"]
        source_chunk_id = (
            selected_chunks[chunk_idx][0].id
            if isinstance(chunk_idx, int) and 0 <= chunk_idx < len(selected_chunks)
            else None
        )
        q = Question(
            quiz_id=quiz.id,
            prompt=item["prompt"],
            topic=item["topic"],
            options=item["options"],
            correct_option_id=item["correct_option_id"],
            explanation=item["explanation"],
            source_chunk_id=source_chunk_id,
        )
        db.add(q)
        created_questions.append(q)

    await db.commit()
    await db.refresh(quiz)
    for q in created_questions:
        await db.refresh(q)

    # Bảo mật: tuyệt đối không trả correct_option_id hoặc explanation trước khi nộp bài
    return QuizClientResponse(
        id=quiz.id,
        course_id=quiz.course_id,
        title=quiz.title,
        total_questions=quiz.total_questions,
        questions=[
            QuizQuestionClientResponse(
                id=q.id,
                prompt=q.prompt,
                topic=q.topic,
                options=[OptionSchema(**opt) for opt in q.options],
            )
            for q in created_questions
        ],
        created_at=quiz.created_at,
    )

