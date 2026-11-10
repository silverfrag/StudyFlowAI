from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, Integer
from typing import List

from app.core.database import get_db
from app.models.course import Course
from app.models.quiz import QuizAttempt, Quiz, Question, Answer
from app.models.document import Document, DocumentChunk
from app.schemas.progress import (
    ProgressSummaryResponse,
    RecentActivity,
    RecommendationItem,
    SuggestedDocument,
)

router = APIRouter(tags=["Progress"])


@router.get("/progress/summary", response_model=ProgressSummaryResponse)
async def get_progress_summary(db: AsyncSession = Depends(get_db)):
    """Tổng quan tiến độ học tập cho Màn hình 01 (Trang chủ)."""
    courses_count_res = await db.execute(select(func.count(Course.id)))
    total_courses = courses_count_res.scalar_one()

    quizzes_count_res = await db.execute(select(func.count(QuizAttempt.id)))
    total_quizzes = quizzes_count_res.scalar_one()

    avg_score_res = await db.execute(select(func.avg(QuizAttempt.score)))
    avg_score = avg_score_res.scalar() or 0.0

    recent_activity = None
    last_attempt_res = await db.execute(
        select(QuizAttempt).order_by(QuizAttempt.submitted_at.desc()).limit(1)
    )
    last_attempt = last_attempt_res.scalar_one_or_none()
    if last_attempt:
        quiz = await db.get(Quiz, last_attempt.quiz_id)
        if quiz:
            course = await db.get(Course, quiz.course_id)
            if course:
                recent_activity = RecentActivity(
                    course_id=course.id,
                    course_name=course.name,
                    last_quiz_title=quiz.title,
                    last_score=last_attempt.score,
                    last_active_at=last_attempt.submitted_at,
                )

    return ProgressSummaryResponse(
        total_courses=total_courses,
        total_quizzes_completed=total_quizzes,
        average_score=round(float(avg_score), 1),
        recent_activity=recent_activity,
    )


@router.get("/recommendations", response_model=List[RecommendationItem])
async def get_recommendations(db: AsyncSession = Depends(get_db)):
    """Gợi ý ôn tập theo Topic yếu (Ngưỡng đề xuất: accuracy < 0.60 và số câu làm >= 3)."""
    # Lấy thống kê theo topic từ các answers
    query = (
        select(
            Question.topic,
            Quiz.course_id,
            func.count(Answer.id).label("total_answered"),
            func.sum(func.cast(Answer.is_correct, Integer)).label("correct_count"),
        )
        .join(Question, Answer.question_id == Question.id)
        .join(Quiz, Question.quiz_id == Quiz.id)
        .group_by(Question.topic, Quiz.course_id)
    )
    result = await db.execute(query)
    rows = result.all()

    recommendations = []
    for row in rows:
        topic = row.topic
        course_id = row.course_id
        total_answered = row.total_answered or 0
        correct_count = row.correct_count or 0

        # Áp dụng ngưỡng: total >= 3 và accuracy < 0.60
        if total_answered >= 3:
            accuracy = round(float(correct_count) / float(total_answered), 2)
            if accuracy < 0.60:
                course = await db.get(Course, course_id)
                course_name = course.name if course else "Môn học"

                # Ưu tiên tài liệu 'ready' gắn với các câu hỏi của topic, hoặc tài liệu 'ready' mới nhất của môn
                linked_doc_res = await db.execute(
                    select(Document)
                    .join(DocumentChunk, DocumentChunk.document_id == Document.id)
                    .join(Question, Question.source_chunk_id == DocumentChunk.id)
                    .join(Quiz, Question.quiz_id == Quiz.id)
                    .where(
                        Quiz.course_id == course_id,
                        Question.topic == topic,
                        Document.status == "ready",
                    )
                    .limit(1)
                )
                first_doc = linked_doc_res.scalar_one_or_none()
                if first_doc is None:
                    doc_res = await db.execute(
                        select(Document)
                        .where(Document.course_id == course_id)
                        .order_by((Document.status == "ready").desc(), Document.created_at.desc())
                        .limit(1)
                    )
                    first_doc = doc_res.scalar_one_or_none()

                recommendations.append(
                    RecommendationItem(
                        topic=topic,
                        course_id=course_id,
                        course_name=course_name,
                        total_answered=total_answered,
                        correct_count=correct_count,
                        accuracy=accuracy,
                        recommended_action=f"Xem lại tài liệu liên quan đến chủ đề '{topic}' và làm bài quiz luyện tập củng cố.",
                        suggested_document=(
                            SuggestedDocument(id=first_doc.id, title=first_doc.title)
                            if first_doc
                            else None
                        ),
                    )
                )

    return recommendations
