from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from uuid import UUID
from datetime import datetime
from typing import Dict, List, Optional, Tuple

from app.core.database import get_db
from app.models.document import Document, DocumentChunk
from app.models.quiz import Quiz, Question, QuizAttempt, Answer
from app.schemas.quiz import (
    QuizCreateRequest,
    QuizClientResponse,
    QuizSubmitRequest,
    QuizSubmitResponse,
    QuizQuestionClientResponse,
    OptionSchema,
    TopicStat,
    QuestionResultDetail,
    SourceCitation,
)
from app.services.gemini_service import AIServiceError
from app.services.quiz_service import generate_quiz_with_ai
from app.services.recommendation_service import (
    build_quiz_submit_study_advice,
    canonicalize_topic_display,
    normalize_topic_group_key,
)

router = APIRouter(prefix="/quizzes", tags=["Quizzes"])


@router.post("", response_model=QuizClientResponse, status_code=status.HTTP_201_CREATED)
async def create_quiz(payload: QuizCreateRequest, db: AsyncSession = Depends(get_db)):
    """Tạo bài kiểm tra MCQ từ tài liệu của môn học (gọi Gemini, BẢO MẬT: giấu đáp án đúng với client)."""
    try:
        return await generate_quiz_with_ai(payload=payload, db=db)
    except AIServiceError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "AI_SERVICE_UNAVAILABLE", "message": str(exc)},
        ) from exc


@router.get("/{quiz_id}", response_model=QuizClientResponse)
async def get_quiz(quiz_id: UUID, db: AsyncSession = Depends(get_db)):
    quiz = await db.get(Quiz, quiz_id)
    if not quiz:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy bài quiz."},
        )

    result = await db.execute(select(Question).where(Question.quiz_id == quiz_id))
    questions = result.scalars().all()

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
            for q in questions
        ],
        created_at=quiz.created_at,
    )


@router.post("/{quiz_id}/submit", response_model=QuizSubmitResponse)
async def submit_quiz(quiz_id: UUID, payload: QuizSubmitRequest, db: AsyncSession = Depends(get_db)):
    """Nộp bài & Chấm điểm xác định (Deterministic Grading). Tuyệt đối không dùng LLM để chấm."""
    quiz = await db.get(Quiz, quiz_id)
    if not quiz:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy bài quiz."},
        )

    result = await db.execute(select(Question).where(Question.quiz_id == quiz_id))
    questions = result.scalars().all()
    if not questions:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "VALIDATION_ERROR", "message": "Bài quiz không có câu hỏi nào."},
        )

    # Truy vấn thông tin nguồn thật từ DocumentChunk & Document cho từng câu hỏi
    chunk_ids = [q.source_chunk_id for q in questions if q.source_chunk_id is not None]
    citation_map: Dict[UUID, Tuple[UUID, str, Optional[int]]] = {}
    if chunk_ids:
        cite_res = await db.execute(
            select(DocumentChunk.id, Document.id, Document.title, DocumentChunk.page_number)
            .join(Document, DocumentChunk.document_id == Document.id)
            .where(DocumentChunk.id.in_(chunk_ids))
        )
        for c_id, doc_id, doc_title, page_num in cite_res.all():
            citation_map[c_id] = (doc_id, doc_title, page_num)

    # Map submission theo question_id
    answers_map = {ans.question_id: ans.selected_option_id for ans in payload.answers}

    correct_count = 0
    details: List[QuestionResultDetail] = []
    topic_map: Dict[str, Dict] = {}
    wrong_items: List[Dict] = []

    for q in questions:
        selected_opt = answers_map.get(q.id)
        # So khớp chuỗi xác định
        is_correct = (selected_opt is not None) and (selected_opt.upper() == q.correct_option_id.upper())
        if is_correct:
            correct_count += 1

        # Cập nhật topic stat (gom các biến thể dấu/khoảng trắng về 1 nhóm nhất quán)
        display_topic = canonicalize_topic_display(q.topic) or "Kiến thức chung"
        topic_group_key = normalize_topic_group_key(display_topic)
        if topic_group_key not in topic_map:
            topic_map[topic_group_key] = {"display": display_topic, "total": 0, "correct": 0}
        topic_map[topic_group_key]["total"] += 1
        if is_correct:
            topic_map[topic_group_key]["correct"] += 1

        source_info = citation_map.get(q.source_chunk_id) if q.source_chunk_id else None
        if not is_correct:
            wrong_items.append(
                {
                    "topic": topic_map[topic_group_key]["display"],
                    "prompt": q.prompt,
                    "explanation": q.explanation,
                    "doc_title": source_info[1] if source_info else None,
                    "page_number": source_info[2] if source_info else None,
                }
            )

        details.append(
            QuestionResultDetail(
                question_id=q.id,
                prompt=q.prompt,
                selected_option_id=selected_opt,
                correct_option_id=q.correct_option_id,
                is_correct=is_correct,
                explanation=q.explanation,
                citation=(
                    SourceCitation(
                        document_id=source_info[0],
                        document_title=source_info[1],
                        page_number=source_info[2],
                    )
                    if source_info
                    else None
                ),
            )
        )

    total_questions = len(questions)
    incorrect_count = total_questions - correct_count
    # Công thức tính điểm chuẩn: round((correct / total) * 10.0, 1)
    score = round((correct_count / total_questions) * 10.0, 1)

    # Lưu lượt làm QuizAttempt
    attempt = QuizAttempt(
        quiz_id=quiz.id,
        score=score,
        total_questions=total_questions,
        correct_count=correct_count,
        incorrect_count=incorrect_count,
        submitted_at=datetime.utcnow(),
    )
    db.add(attempt)
    await db.commit()
    await db.refresh(attempt)

    # Lưu chi tiết Answer từng câu
    for d in details:
        ans_record = Answer(
            attempt_id=attempt.id,
            question_id=d.question_id,
            selected_option_id=d.selected_option_id,
            is_correct=d.is_correct,
        )
        db.add(ans_record)
    await db.commit()

    topic_stats = [
        TopicStat(
            topic=vals["display"],
            total_questions=vals["total"],
            correct_count=vals["correct"],
            accuracy=round(vals["correct"] / vals["total"], 2),
        )
        for vals in topic_map.values()
    ]

    try:
        study_advice = await build_quiz_submit_study_advice(
            quiz_title=quiz.title,
            score=score,
            total_questions=total_questions,
            wrong_items=wrong_items,
        )
    except Exception:
        study_advice = None

    return QuizSubmitResponse(
        attempt_id=attempt.id,
        quiz_id=quiz.id,
        score=attempt.score,
        total_questions=attempt.total_questions,
        correct_count=attempt.correct_count,
        incorrect_count=attempt.incorrect_count,
        submitted_at=attempt.submitted_at,
        topic_stats=topic_stats,
        study_advice=study_advice,
        details=details,
    )
