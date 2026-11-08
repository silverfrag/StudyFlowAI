from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from uuid import UUID
from datetime import datetime

from app.core.database import get_db
from app.models.course import Course
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

router = APIRouter(prefix="/quizzes", tags=["Quizzes"])


@router.post("", response_model=QuizClientResponse, status_code=status.HTTP_201_CREATED)
async def create_quiz(payload: QuizCreateRequest, db: AsyncSession = Depends(get_db)):
    """Tạo bài kiểm tra MCQ từ tài liệu của môn học (gọi Gemini, BẢO MẬT: giấu đáp án đúng với client)."""
    course = await db.get(Course, payload.course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )

    # TODO Thành viên 2:
    # 1. Lấy chunks liên quan từ document_chunks.
    # 2. Gọi Gemini để sinh đúng `payload.question_count` câu hỏi trắc nghiệm MCQ (A, B, C, D) có structured JSON.
    # 3. Lưu Quiz & Questions vào Database (lưu correct_option_id vào bảng Question).
    # 4. Trả về QuizClientResponse (không chứa correct_option_id).

    # Stub tạo Quiz mẫu để client có thể test luồng làm bài
    quiz = Quiz(
        course_id=payload.course_id,
        title=f"Quiz: {payload.topic or 'Tổng hợp kiến thức môn học'}",
        total_questions=payload.question_count,
    )
    db.add(quiz)
    await db.commit()
    await db.refresh(quiz)

    # Tạo câu hỏi mẫu để test
    questions_response = []
    for i in range(payload.question_count):
        q = Question(
            quiz_id=quiz.id,
            prompt=f"Câu hỏi trắc nghiệm số {i + 1} về chủ đề: {payload.topic or course.name}?",
            topic=payload.topic or "Kiến thức chung",
            options=[
                {"id": "A", "text": "Phương án lựa chọn A"},
                {"id": "B", "text": "Phương án lựa chọn B (Đáp án đúng)"},
                {"id": "C", "text": "Phương án lựa chọn C"},
                {"id": "D", "text": "Phương án lựa chọn D"},
            ],
            correct_option_id="B",
            explanation=f"Giải thích chi tiết cho câu hỏi số {i + 1}.",
        )
        db.add(q)
        await db.commit()
        await db.refresh(q)

        questions_response.append(
            QuizQuestionClientResponse(
                id=q.id,
                prompt=q.prompt,
                topic=q.topic,
                options=[OptionSchema(**opt) for opt in q.options],
            )
        )

    return QuizClientResponse(
        id=quiz.id,
        course_id=quiz.course_id,
        title=quiz.title,
        total_questions=quiz.total_questions,
        questions=questions_response,
        created_at=quiz.created_at,
    )


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

    # Map submission theo question_id
    answers_map = {ans.question_id: ans.selected_option_id for ans in payload.answers}

    correct_count = 0
    details = []
    topic_map = {}

    for q in questions:
        selected_opt = answers_map.get(q.id)
        # So khớp chuỗi xác định
        is_correct = (selected_opt is not None) and (selected_opt.upper() == q.correct_option_id.upper())
        if is_correct:
            correct_count += 1

        # Cập nhật topic stat
        if q.topic not in topic_map:
            topic_map[q.topic] = {"total": 0, "correct": 0}
        topic_map[q.topic]["total"] += 1
        if is_correct:
            topic_map[q.topic]["correct"] += 1

        details.append(
            QuestionResultDetail(
                question_id=q.id,
                prompt=q.prompt,
                selected_option_id=selected_opt,
                correct_option_id=q.correct_option_id,
                is_correct=is_correct,
                explanation=q.explanation,
                citation=SourceCitation(document_title="Tài liệu môn học", page_number=1),
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
            topic=t,
            total_questions=vals["total"],
            correct_count=vals["correct"],
            accuracy=round(vals["correct"] / vals["total"], 2),
        )
        for t, vals in topic_map.items()
    ]

    return QuizSubmitResponse(
        attempt_id=attempt.id,
        quiz_id=quiz.id,
        score=attempt.score,
        total_questions=attempt.total_questions,
        correct_count=attempt.correct_count,
        incorrect_count=attempt.incorrect_count,
        submitted_at=attempt.submitted_at,
        topic_stats=topic_stats,
        details=details,
    )
