from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from uuid import UUID
from typing import List
import os

from app.core.database import get_db
from app.models.course import Course
from app.models.document import Document
from app.models.quiz import Quiz
from app.schemas.course import CourseCreate, CourseUpdate, CourseResponse

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
    if payload.name is not None:
        course.name = payload.name
    if payload.description is not None:
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

