from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from uuid import UUID
from typing import List
import os

from app.core.database import get_db
from app.models.course import Course
from app.models.document import Document
from app.models.quiz import Quiz, QuizAttempt
from app.schemas.course import CourseCreate, CourseUpdate, CourseResponse
from app.schemas.quiz import QuizSummaryResponse

router = APIRouter(prefix="/courses", tags=["Courses"])


@router.get("", response_model=List[CourseResponse])
async def list_courses(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Course).order_by(Course.created_at.desc()))
    courses = result.scalars().all()

    response = []
    for c in courses:
        # Đếm số lượng tài liệu và quiz
        doc_count_res = await db.execute(select(func.count(Document.id)).where(Document.course_id == c.id))
        quiz_count_res = await db.execute(select(func.count(Quiz.id)).where(Quiz.course_id == c.id))
        
        response.append(
            CourseResponse(
                id=c.id,
                name=c.name,
                description=c.description,
                document_count=doc_count_res.scalar_one(),
                quiz_count=quiz_count_res.scalar_one(),
                created_at=c.created_at,
            )
        )
    return response


@router.post("", response_model=CourseResponse, status_code=status.HTTP_201_CREATED)
async def create_course(payload: CourseCreate, db: AsyncSession = Depends(get_db)):
    existing = await db.execute(
        select(Course).where(func.lower(func.trim(Course.name)) == payload.name.lower())
    )
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "code": "VALIDATION_ERROR",
                "message": "Môn học có tên này đã tồn tại.",
                "field": "name",
            },
        )

    course = Course(name=payload.name, description=payload.description)
    db.add(course)
    await db.commit()
    await db.refresh(course)
    return CourseResponse(
        id=course.id,
        name=course.name,
        description=course.description,
        document_count=0,
        quiz_count=0,
        created_at=course.created_at,
    )


@router.get("/{course_id}", response_model=CourseResponse)
async def get_course(course_id: UUID, db: AsyncSession = Depends(get_db)):
    course = await db.get(Course, course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )
    doc_count_res = await db.execute(select(func.count(Document.id)).where(Document.course_id == course.id))
    quiz_count_res = await db.execute(select(func.count(Quiz.id)).where(Quiz.course_id == course.id))

    return CourseResponse(
        id=course.id,
        name=course.name,
        description=course.description,
        document_count=doc_count_res.scalar_one(),
        quiz_count=quiz_count_res.scalar_one(),
        created_at=course.created_at,
    )


@router.patch("/{course_id}", response_model=CourseResponse)
async def update_course(course_id: UUID, payload: CourseUpdate, db: AsyncSession = Depends(get_db)):
    course = await db.get(Course, course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )
    if "name" in payload.model_fields_set and payload.name is not None:
        dup = await db.execute(
            select(Course).where(
                func.lower(func.trim(Course.name)) == payload.name.lower(),
                Course.id != course.id,
            )
        )
        if dup.scalar_one_or_none() is not None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={
                    "code": "VALIDATION_ERROR",
                    "message": "Môn học có tên này đã tồn tại.",
                    "field": "name",
                },
            )
        course.name = payload.name
    if "description" in payload.model_fields_set:
        course.description = payload.description

    await db.commit()
    await db.refresh(course)

    doc_count_res = await db.execute(select(func.count(Document.id)).where(Document.course_id == course.id))
    quiz_count_res = await db.execute(select(func.count(Quiz.id)).where(Quiz.course_id == course.id))

    return CourseResponse(
        id=course.id,
        name=course.name,
        description=course.description,
        document_count=doc_count_res.scalar_one(),
        quiz_count=quiz_count_res.scalar_one(),
        created_at=course.created_at,
    )


@router.delete("/{course_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_course(course_id: UUID, db: AsyncSession = Depends(get_db)):
    course = await db.get(Course, course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )

    # Dọn dẹp file vật lý trước khi cascade xóa dữ liệu DB
    doc_paths_res = await db.execute(select(Document.file_path).where(Document.course_id == course_id))
    file_paths = [row[0] for row in doc_paths_res.all() if row[0]]
    for file_path in file_paths:
        if os.path.exists(file_path):
            try:
                os.remove(file_path)
            except OSError:
                pass

    await db.delete(course)
    await db.commit()
    return None


@router.get("/{course_id}/quizzes", response_model=List[QuizSummaryResponse])
async def list_course_quizzes(course_id: UUID, db: AsyncSession = Depends(get_db)):
    """Liệt kê danh sách bài quiz đã tạo của môn học kèm số lượt làm và điểm gần nhất."""
    course = await db.get(Course, course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )

    quiz_res = await db.execute(
        select(Quiz).where(Quiz.course_id == course_id).order_by(Quiz.created_at.desc())
    )
    quizzes = quiz_res.scalars().all()
    if not quizzes:
        return []

    quiz_ids = [q.id for q in quizzes]
    attempts_res = await db.execute(
        select(QuizAttempt.quiz_id, QuizAttempt.score, QuizAttempt.submitted_at)
        .where(QuizAttempt.quiz_id.in_(quiz_ids))
        .order_by(QuizAttempt.submitted_at.desc())
    )
    attempt_counts: dict = {}
    latest_scores: dict = {}
    for q_id, score, _submitted_at in attempts_res.all():
        attempt_counts[q_id] = attempt_counts.get(q_id, 0) + 1
        if q_id not in latest_scores:
            latest_scores[q_id] = score

    return [
        QuizSummaryResponse(
            id=q.id,
            course_id=q.course_id,
            title=q.title,
            total_questions=q.total_questions,
            attempt_count=attempt_counts.get(q.id, 0),
            latest_score=latest_scores.get(q.id),
            created_at=q.created_at,
        )
        for q in quizzes
    ]

