from typing import List

from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.course import Course
from app.models.quiz import Quiz, QuizAttempt
from app.schemas.progress import (
    ProgressSummaryResponse,
    RecentActivity,
    RecommendationItem,
)
from app.services.recommendation_service import build_recommendations

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
    """Gợi ý ôn tập theo Topic yếu (Ngưỡng đề xuất: accuracy < 0.60 và số câu làm >= 2)."""
    return await build_recommendations(db)
