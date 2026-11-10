from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, UploadFile, File, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from uuid import UUID
from typing import List
import os
import uuid
from datetime import datetime

from app.core.database import get_db
from app.core.config import settings
from app.models.course import Course
from app.models.document import Document
from app.schemas.document import DocumentResponse, DocumentStatusResponse
from app.services.document_service import process_document_background

router = APIRouter(tags=["Documents"])


@router.post("/courses/{course_id}/documents", response_model=DocumentResponse, status_code=status.HTTP_202_ACCEPTED)
async def upload_document(
    course_id: UUID,
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
):
    course = await db.get(Course, course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )

    # Validate file extension & làm sạch tên file chống path traversal
    raw_name = os.path.basename(file.filename or "unknown.pdf").strip() or "unknown.pdf"
    stem, ext_raw = os.path.splitext(raw_name)
    ext = ext_raw.lower()
    if ext not in [".pdf", ".txt"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "UNSUPPORTED_FILE_TYPE", "message": "Chỉ hỗ trợ file định dạng .pdf hoặc .txt."},
        )
    filename = f"{stem[:180]}{ext_raw}"

    # Đọc nội dung & kiểm tra dung lượng
    content = await file.read()
    file_size = len(content)
    if file_size == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "VALIDATION_ERROR", "message": "Tệp tải lên đang trống (0 byte).", "field": "file"},
        )
    max_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    if file_size > max_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "FILE_TOO_LARGE", "message": f"Dung lượng file vượt quá giới hạn {settings.MAX_UPLOAD_SIZE_MB}MB."},
        )

    # Lưu file vật lý vào thư mục storage
    os.makedirs(settings.STORAGE_DIR, exist_ok=True)
    stored_name = f"{uuid.uuid4()}_{filename}"
    file_path = os.path.join(settings.STORAGE_DIR, stored_name)
    with open(file_path, "wb") as f:
        f.write(content)

    mime_type = file.content_type or ("application/pdf" if ext == ".pdf" else "text/plain")

    # Tạo bản ghi Document với status "processing"
    doc = Document(
        course_id=course_id,
        title=filename,
        file_path=file_path,
        file_size=file_size,
        mime_type=mime_type,
        status="processing",
    )
    db.add(doc)
    await db.commit()
    await db.refresh(doc)

    # Kích hoạt background task trích xuất văn bản, chia chunk và tạo embedding
    background_tasks.add_task(process_document_background, doc.id)

    return DocumentResponse(
        id=doc.id,
        course_id=doc.course_id,
        title=doc.title,
        file_size=doc.file_size,
        mime_type=doc.mime_type,
        status=doc.status,
        error_message=doc.error_message,
        created_at=doc.created_at,
    )


@router.get("/courses/{course_id}/documents", response_model=List[DocumentResponse])
async def list_course_documents(course_id: UUID, db: AsyncSession = Depends(get_db)):
    course = await db.get(Course, course_id)
    if not course:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy môn học."},
        )

    result = await db.execute(select(Document).where(Document.course_id == course_id).order_by(Document.created_at.desc()))
    docs = result.scalars().all()
    return [
        DocumentResponse(
            id=d.id,
            course_id=d.course_id,
            title=d.title,
            file_size=d.file_size,
            mime_type=d.mime_type,
            status=d.status,
            error_message=d.error_message,
            created_at=d.created_at,
        )
        for d in docs
    ]


@router.get("/documents/{document_id}/status", response_model=DocumentStatusResponse)
async def get_document_status(document_id: UUID, db: AsyncSession = Depends(get_db)):
    doc = await db.get(Document, document_id)
    if not doc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy tài liệu."},
        )
    return DocumentStatusResponse(id=doc.id, status=doc.status, error_message=doc.error_message)


@router.post("/documents/{document_id}/retry", response_model=DocumentResponse, status_code=status.HTTP_202_ACCEPTED)
async def retry_document_processing(
    document_id: UUID,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
):
    """Thử xử lý lại tài liệu ở trạng thái error nếu file gốc vẫn còn trên server."""
    doc = await db.get(Document, document_id)
    if not doc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy tài liệu."},
        )
    if not doc.file_path or not os.path.exists(doc.file_path):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "code": "VALIDATION_ERROR",
                "message": "Không tìm thấy tệp gốc trên máy chủ. Vui lòng tải lại tệp mới.",
            },
        )

    doc.status = "processing"
    doc.error_message = None
    await db.commit()
    await db.refresh(doc)

    background_tasks.add_task(process_document_background, doc.id)

    return DocumentResponse(
        id=doc.id,
        course_id=doc.course_id,
        title=doc.title,
        file_size=doc.file_size,
        mime_type=doc.mime_type,
        status=doc.status,
        error_message=doc.error_message,
        created_at=doc.created_at,
    )


@router.delete("/documents/{document_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_document(document_id: UUID, db: AsyncSession = Depends(get_db)):
    doc = await db.get(Document, document_id)
    if not doc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "Không tìm thấy tài liệu."},
        )
    # Xóa file vật lý nếu tồn tại
    if os.path.exists(doc.file_path):
        try:
            os.remove(doc.file_path)
        except OSError:
            pass

    await db.delete(doc)
    await db.commit()
    return None

