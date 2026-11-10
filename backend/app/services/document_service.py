import asyncio
import logging
import os
import re
from typing import Dict, List, Optional, Set, Tuple
from uuid import UUID

from pypdf import PdfReader
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import AsyncSessionLocal
from app.models.document import Document, DocumentChunk
from app.services.gemini_service import AIServiceError, embed_texts

logger = logging.getLogger(__name__)

EMBEDDING_DIMENSIONS = 768
CHUNK_TARGET_WORDS = 350
CHUNK_OVERLAP_WORDS = 60
MAX_CHUNKS_PER_DOC = 400

_ACTIVE_PROCESSING_IDS: Set[UUID] = set()


def is_document_actively_processing(document_id: UUID) -> bool:
    """Kiểm tra tài liệu có đang nằm trong hàng đợi hoặc đang được worker xử lý trong tiến trình hiện tại."""
    return document_id in _ACTIVE_PROCESSING_IDS


def mark_document_queued(document_id: UUID) -> None:
    """Đánh dấu tài liệu đã được xếp lịch xử lý nền để tránh kích hoạt trùng lặp."""
    _ACTIVE_PROCESSING_IDS.add(document_id)


def clear_document_active(document_id: UUID) -> None:
    _ACTIVE_PROCESSING_IDS.discard(document_id)


async def recover_orphaned_processing_documents(db: AsyncSession) -> int:
    """Chuyển các tài liệu bị kẹt trạng thái 'processing' sau khi server khởi động lại sang 'error' để người dùng thử lại."""
    result = await db.execute(select(Document).where(Document.status == "processing"))
    orphaned_docs = [
        doc for doc in result.scalars().all()
        if not is_document_actively_processing(doc.id)
    ]
    if not orphaned_docs:
        return 0

    for doc in orphaned_docs:
        doc.status = "error"
        doc.error_message = "Quá trình xử lý trước đó bị gián đoạn trước khi hoàn tất. Vui lòng nhấn thử lại."
    await db.commit()
    return len(orphaned_docs)


def _normalize_whitespace(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _read_txt_with_fallback(file_path: str) -> str:
    with open(file_path, "rb") as f:
        raw = f.read()
    for encoding in ("utf-8-sig", "utf-8", "cp1258", "latin-1"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def extract_text_pages(file_path: str, title: str, mime_type: str = "") -> List[Tuple[Optional[int], str]]:
    """Trích xuất văn bản theo từng trang (PDF) hoặc nguyên tệp (TXT)."""
    if not os.path.exists(file_path):
        raise ValueError("Không tìm thấy tệp tài liệu đã lưu trên máy chủ.")

    ext = os.path.splitext(title or file_path)[1].lower()
    is_pdf = ext == ".pdf" or "pdf" in (mime_type or "").lower()

    pages: List[Tuple[Optional[int], str]] = []
    if is_pdf:
        try:
            reader = PdfReader(file_path)
            if reader.is_encrypted:
                try:
                    reader.decrypt("")
                except Exception as exc:
                    raise ValueError("Tệp PDF được đặt mật khẩu bảo vệ, không thể trích xuất.") from exc

            for page_idx, page in enumerate(reader.pages, start=1):
                extracted = _normalize_whitespace(page.extract_text() or "")
                if extracted:
                    pages.append((page_idx, extracted))
        except ValueError:
            raise
        except Exception as exc:
            logger.warning("Lỗi đọc PDF %s: %s", title, type(exc).__name__)
            raise ValueError("Tệp PDF bị hỏng hoặc không đúng định dạng chuẩn.") from exc
    else:
        content = _normalize_whitespace(_read_txt_with_fallback(file_path))
        if content:
            pages.append((None, content))

    if not pages:
        raise ValueError(
            "Không trích xuất được văn bản từ tệp (tệp trống hoặc PDF dạng ảnh scan chưa OCR)."
        )
    return pages


def chunk_pages(
    pages: List[Tuple[Optional[int], str]],
    target_words: int = CHUNK_TARGET_WORDS,
    overlap_words: int = CHUNK_OVERLAP_WORDS,
    max_chunks: int = MAX_CHUNKS_PER_DOC,
) -> List[Dict]:
    """Chia văn bản theo từng trang thành các chunk có overlap và giữ page_number."""
    step = max(1, target_words - overlap_words)
    chunks: List[Dict] = []
    ordinal = 0

    for page_number, page_text in pages:
        words = page_text.split()
        if not words:
            continue

        if len(words) <= target_words:
            chunks.append(
                {
                    "ordinal": ordinal,
                    "page_number": page_number,
                    "text": " ".join(words),
                }
            )
            ordinal += 1
            if ordinal >= max_chunks:
                break
            continue

        idx = 0
        while idx < len(words) and ordinal < max_chunks:
            window = words[idx : idx + target_words]
            chunks.append(
                {
                    "ordinal": ordinal,
                    "page_number": page_number,
                    "text": " ".join(window),
                }
            )
            ordinal += 1
            if idx + target_words >= len(words):
                break
            idx += step

        if ordinal >= max_chunks:
            break

    return chunks


async def process_document(document_id: UUID, db: AsyncSession) -> None:
    """Trích xuất nội dung, chunking, tạo vector embedding và cập nhật trạng thái tài liệu."""
    mark_document_queued(document_id)
    try:
        doc = await db.get(Document, document_id)
        if not doc:
            return

        pages = await asyncio.to_thread(extract_text_pages, doc.file_path, doc.title, doc.mime_type)
        chunks_data = await asyncio.to_thread(chunk_pages, pages)
        if not chunks_data:
            raise ValueError("Không tạo được đoạn văn bản hợp lệ từ tài liệu.")

        vectors = await embed_texts(
            [item["text"] for item in chunks_data],
            task_type="RETRIEVAL_DOCUMENT",
            dimensions=EMBEDDING_DIMENSIONS,
        )

        # Kiểm tra tài liệu có bị xóa trong lúc đang gọi Gemini hay không
        doc = await db.get(Document, document_id)
        if not doc:
            return

        await db.execute(delete(DocumentChunk).where(DocumentChunk.document_id == document_id))
        for item, vec in zip(chunks_data, vectors):
            db.add(
                DocumentChunk(
                    document_id=doc.id,
                    course_id=doc.course_id,
                    text=item["text"],
                    page_number=item["page_number"],
                    ordinal=item["ordinal"],
                    embedding=vec,
                )
            )

        doc.status = "ready"
        doc.error_message = None
        await db.commit()
    except asyncio.CancelledError:
        logger.warning("Tác vụ xử lý tài liệu %s bị hủy giữa chừng.", document_id)
        try:
            await asyncio.shield(db.rollback())
            doc = await db.get(Document, document_id)
            if doc and doc.status == "processing":
                doc.status = "error"
                doc.error_message = "Quá trình xử lý bị gián đoạn trước khi hoàn tất. Vui lòng nhấn thử lại."
                await asyncio.shield(db.commit())
        except BaseException:
            pass
        raise
    except (ValueError, AIServiceError) as exc:
        await db.rollback()
        doc = await db.get(Document, document_id)
        if doc:
            doc.status = "error"
            doc.error_message = str(exc)[:500]
            await db.commit()
    except Exception as exc:
        logger.exception("Lỗi không xác định khi xử lý tài liệu %s: %s", document_id, type(exc).__name__)
        await db.rollback()
        doc = await db.get(Document, document_id)
        if doc:
            doc.status = "error"
            doc.error_message = "Xử lý tài liệu thất bại do lỗi hệ thống nội bộ."
            await db.commit()
    finally:
        clear_document_active(document_id)


async def process_document_background(document_id: UUID) -> None:
    """Task chạy nền cho FastAPI BackgroundTasks với AsyncSession riêng biệt."""
    async with AsyncSessionLocal() as db:
        await process_document(document_id, db)

