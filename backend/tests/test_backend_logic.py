import os
import re
import sys
import tempfile
import unittest
from pathlib import Path
from typing import List, Optional
from unittest.mock import AsyncMock, patch
from uuid import UUID, uuid4

import httpx
from google.genai import errors as genai_errors
from pydantic import BaseModel

# Đảm bảo import được app khi chạy từ thư mục backend hoặc thư mục gốc
BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.config import settings
from app.core.database import AsyncSessionLocal, engine, init_db
from app.main import app
from app.models.document import Document, DocumentChunk
from app.schemas.assistant import ChatHistoryMessage
from app.services import (
    document_service,
    gemini_service,
    quiz_service,
    rag_service,
    recommendation_service,
)


def _build_minimal_pdf_bytes(text: str = "Primary Key is unique and NOT NULL in relational databases.") -> bytes:
    """Tạo một tệp PDF 1 trang hợp lệ chuẩn để kiểm thử pypdf."""
    stream_data = f"BT /F1 12 Tf 72 720 Td ({text}) Tj ET".encode("latin-1")
    objects = [
        b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
        b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
        b"3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n",
        f"4 0 obj\n<< /Length {len(stream_data)} >>\nstream\n".encode("latin-1") + stream_data + b"\nendstream\nendobj\n",
        b"5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for obj in objects:
        offsets.append(len(out))
        out.extend(obj)
    xref_start = len(out)
    out.extend(f"xref\n0 {len(objects) + 1}\n".encode("latin-1"))
    out.extend(b"0000000000 65535 f \n")
    for off in offsets[1:]:
        out.extend(f"{off:010d} 00000 n \n".encode("latin-1"))
    out.extend(
        f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref_start}\n%%EOF\n".encode("latin-1")
    )
    return bytes(out)


def _deterministic_vec(seed: float = 0.25) -> List[float]:
    vec = [0.01] * 768
    vec[0] = seed
    vec[1] = 1.0 - abs(seed)
    return vec


class PureBackendUnitTests(unittest.TestCase):
    def test_extract_text_pages_txt_and_pdf(self):
        with tempfile.TemporaryDirectory() as tmp:
            txt_path = os.path.join(tmp, "notes.txt")
            with open(txt_path, "w", encoding="utf-8") as f:
                f.write("Khóa chính (Primary Key) không được phép chứa giá trị NULL.\n   Dòng thứ hai.")

            txt_pages = document_service.extract_text_pages(txt_path, "notes.txt", "text/plain")
            self.assertEqual(len(txt_pages), 1)
            self.assertIsNone(txt_pages[0][0])
            self.assertIn("Khóa chính", txt_pages[0][1])

            pdf_path = os.path.join(tmp, "lecture.pdf")
            with open(pdf_path, "wb") as f:
                f.write(_build_minimal_pdf_bytes("Relational database Primary Key constraint"))

            pdf_pages = document_service.extract_text_pages(pdf_path, "lecture.pdf", "application/pdf")
            self.assertEqual(len(pdf_pages), 1)
            self.assertEqual(pdf_pages[0][0], 1)
            self.assertIn("Primary Key", pdf_pages[0][1])

            empty_path = os.path.join(tmp, "empty.txt")
            with open(empty_path, "w", encoding="utf-8") as f:
                f.write("   \n\t ")
            with self.assertRaises(ValueError):
                document_service.extract_text_pages(empty_path, "empty.txt", "text/plain")

    def test_chunk_pages_overlap_and_page_numbers(self):
        words = [f"tu{i}" for i in range(45)]
        pages = [(1, " ".join(words)), (2, "noi dung trang hai ngan")]
        chunks = document_service.chunk_pages(pages, target_words=20, overlap_words=5, max_chunks=20)
        self.assertGreaterEqual(len(chunks), 4)
        self.assertEqual(chunks[0]["ordinal"], 0)
        self.assertEqual(chunks[0]["page_number"], 1)
        self.assertEqual(chunks[-1]["page_number"], 2)
        # Kiểm tra overlap 5 từ giữa chunk 0 và chunk 1
        c0_tail = chunks[0]["text"].split()[-5:]
        c1_head = chunks[1]["text"].split()[:5]
        self.assertEqual(c0_tail, c1_head)

    def test_inline_json_schema_and_model_mapping(self):
        class Child(BaseModel):
            id: str

        class Parent(BaseModel):
            items: List[Child]
            note: Optional[str] = None

        inlined = gemini_service.inline_json_schema(Parent.model_json_schema())
        self.assertNotIn("$defs", inlined)
        self.assertNotIn("$ref", inlined["properties"]["items"]["items"])
        self.assertEqual(inlined["properties"]["note"]["type"], ["string", "null"])

        with patch.object(settings, "GEMINI_MODEL", "gemini-1.5-flash"), \
             patch.object(settings, "GEMINI_EMBEDDING_MODEL", "text-embedding-004"), \
             patch.object(settings, "GEMINI_API_KEY", "your_gemini_api_key_here"):
            self.assertEqual(gemini_service.resolve_generation_model(), "gemini-3.5-flash-lite")
            self.assertEqual(gemini_service.resolve_embedding_model(), "gemini-embedding-001")
            self.assertFalse(gemini_service.is_gemini_configured())
            with self.assertRaises(gemini_service.AIServiceError):
                gemini_service.get_genai_client()

        with patch.object(settings, "GEMINI_MODEL", "gemini-3.8-flash"), \
             patch.object(settings, "GEMINI_FALLBACK_MODELS", "gemini-3.5-flash-lite,gemini-3.1-flash-lite"):
            self.assertEqual(
                gemini_service.resolve_generation_candidates(),
                ["gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"],
            )

    def test_quiz_output_validation(self):
        valid = quiz_service._QuizLLMOutput(
            questions=[
                quiz_service._QuizQuestionLLM(
                    prompt="Khóa chính có thể NULL không?",
                    topic="Ràng buộc toàn vẹn",
                    options=[
                        quiz_service._QuizOptionLLM(id="D", text="Chỉ khi bảng rỗng"),
                        quiz_service._QuizOptionLLM(id="A", text="Có thể"),
                        quiz_service._QuizOptionLLM(id="B", text="Tuyệt đối không"),
                        quiz_service._QuizOptionLLM(id="C", text="Tùy HQTCSDL"),
                    ],
                    correct_option_id="B",
                    explanation="Khóa chính luôn NOT NULL.",
                    source_chunk_index=0,
                )
            ]
        )
        normalized = quiz_service._validate_and_normalize_questions(valid, expected_count=1, fallback_topic="CSDL")
        self.assertEqual([o["id"] for o in normalized[0]["options"]], ["A", "B", "C", "D"])

        with self.assertRaises(ValueError):
            quiz_service._validate_and_normalize_questions(valid, expected_count=3, fallback_topic="CSDL")

        # Kiểm tra preferred_topic giữ nhất quán tên topic & gộp các topic đơn lẻ khi > 3 chủ đề
        fragmented = quiz_service._QuizLLMOutput(
            questions=[
                quiz_service._QuizQuestionLLM(
                    prompt=f"Câu {i}",
                    topic=t,
                    options=[
                        quiz_service._QuizOptionLLM(id="A", text="1"),
                        quiz_service._QuizOptionLLM(id="B", text="2"),
                        quiz_service._QuizOptionLLM(id="C", text="3"),
                        quiz_service._QuizOptionLLM(id="D", text="4"),
                    ],
                    correct_option_id="A",
                    explanation="Giải thích.",
                    source_chunk_index=0,
                )
                for i, t in enumerate(["STLC", "STLC", "Black-box", "DevTools", "Locator", "PageObject"])
            ]
        )
        locked = quiz_service._validate_and_normalize_questions(
            fragmented,
            expected_count=6,
            fallback_topic="Kiểm thử",
            preferred_topic="  Vòng đời lỗi (Bug Lifecycle)  ",
        )
        self.assertEqual({q["topic"] for q in locked}, {"Vòng đời lỗi (Bug Lifecycle)"})

        grouped = quiz_service._validate_and_normalize_questions(
            fragmented,
            expected_count=6,
            fallback_topic="Kiểm thử",
            existing_topic_map={"stlc": "Quy trình kiểm thử (STLC)"},
        )
        distinct_topics = {q["topic"] for q in grouped}
        self.assertLessEqual(len(distinct_topics), 3)
        self.assertIn("Quy trình kiểm thử (STLC)", distinct_topics)

    def test_topic_normalization_and_contextual_fallback(self):
        k1 = recommendation_service.normalize_topic_group_key("Quy trình kiểm thử (STLC)")
        k2 = recommendation_service.normalize_topic_group_key("  quy trình kiểm thử   STLC ")
        self.assertEqual(k1, k2)

        cand = recommendation_service._WeakTopicCandidate(
            course_id=UUID("00000000-0000-0000-0000-000000000001"),
            course_name="Kiểm thử phần mềm",
            topic="Vòng đời của lỗi",
            total_answered=2,
            correct_count=0,
            accuracy=0.0,
            wrong_evidences=[
                recommendation_service._WrongEvidence(
                    question_id=UUID("00000000-0000-0000-0000-000000000002"),
                    prompt="Trạng thái nào xuất hiện sau khi Dev sửa lỗi xong?",
                    explanation="Sau Fixed cần chuyển Retest/Verified để QA kiểm chứng lại trước khi Closed.",
                    document_id=UUID("00000000-0000-0000-0000-000000000003"),
                    document_title="Chuong2_STLC.pdf",
                    document_status="ready",
                    page_number=36,
                )
            ],
            suggested_doc=recommendation_service.SuggestedDocument(
                id=UUID("00000000-0000-0000-0000-000000000003"),
                title="Chuong2_STLC.pdf",
            ),
            pages=[36],
            cache_key=("test",),
        )
        msg = recommendation_service._build_contextual_fallback_action(cand, variant_idx=0)
        self.assertNotIn("Xem lại tài liệu liên quan đến chủ đề", msg)
        self.assertIn("Trang 36", msg)
        self.assertIn("Chuong2_STLC.pdf", msg)
        self.assertIn("Retest/Verified", msg)

    def test_rag_query_classification_and_text_helpers(self):
        self.assertTrue(rag_service._is_overview_query("Tóm tắt nội dung chính từng tài liệu"))
        self.assertTrue(rag_service._is_overview_query("Tổng hợp các khái niệm quan trọng nhất"))
        self.assertFalse(rag_service._is_overview_query("Độ phức tạp Cyclomatic là gì?"))

        self.assertTrue(rag_service._is_followup_question("Giải thích chi tiết hơn"))
        self.assertTrue(rag_service._is_followup_question("Cho ví dụ cụ thể từ phần trên"))
        self.assertFalse(
            rag_service._is_followup_question("Quy trình kiểm thử phần mềm STLC bao gồm những giai đoạn nào?")
        )

        history = [
            ChatHistoryMessage(role="user", content="Kỹ thuật phân vùng tương đương trong Chương 2"),
            ChatHistoryMessage(role="assistant", content="Là kỹ thuật chia miền đầu vào thành các lớp."),
        ]
        rewritten = rag_service._build_retrieval_query("Cho ví dụ cụ thể", history)
        self.assertIn("phân vùng tương đương", rewritten)
        self.assertIn("Cho ví dụ cụ thể", rewritten)

        self.assertIn("nguyennhangayraloiphanmem", rag_service._compact_alnum("Cácnguyênnhângâyralỗiphầnmềm"))

        doc3 = Document(id=uuid4(), course_id=uuid4(), title="Chương 3_Whitebox.pdf", file_path="x", file_size=1, mime_type="application/pdf")
        doc42 = Document(id=uuid4(), course_id=uuid4(), title="Chương 4-2_Định vị web.pdf", file_path="y", file_size=1, mime_type="application/pdf")
        self.assertEqual(rag_service._find_mentioned_doc_ids("Hỏi về Chương 3", [doc3, doc42]), {doc3.id})
        self.assertEqual(rag_service._find_mentioned_doc_ids("Trong chương 4-2 có gì?", [doc3, doc42]), {doc42.id})

        raw_ans = (
            "## Kết quả ([ĐOẠN 0, ĐOẠN 1])\n"
            "Công thức: $CC = E - N + 2P$ và $2 \\times 3 \\leq 6$.\n"
            "---\n"
            "Mẹo DevTools dùng `$0` vẫn giữ nguyên.\n"
            "```python\n"
            "    if x > 0:\n"
            "        print(x)\n"
            "```"
        )
        cleaned = rag_service._clean_rag_answer(raw_ans)
        self.assertNotIn("ĐOẠN", cleaned)
        self.assertNotIn("$CC", cleaned)
        self.assertIn("CC = E - N + 2P", cleaned)
        self.assertIn("2 × 3 ≤ 6", cleaned)
        self.assertIn("`$0`", cleaned)
        self.assertIn("    if x > 0:", cleaned)
        self.assertNotIn("\n---\n", cleaned)

        long_q = "   Giải thích chi tiết   sự khác biệt giữa   kiểm thử hộp đen và kiểm thử hộp trắng trong quy trình   "
        title = rag_service._build_session_title(long_q, max_len=40)
        self.assertLessEqual(len(title), 43)
        self.assertTrue(title.endswith("..."))
        self.assertEqual(rag_service._build_session_title("  Khóa chính là gì?  "), "Khóa chính là gì?")

        md_preview = rag_service._build_plain_preview(
            "## Tổng quan về **STLC**\n- Gồm `Planning` và Execution.",
            max_chars=120,
        )
        self.assertEqual(md_preview, "Tổng quan về STLC: Gồm Planning và Execution.")

    def test_quiz_filler_filtering_helper(self):
        filler_short = DocumentChunk(
            document_id=uuid4(),
            course_id=uuid4(),
            page_number=1,
            ordinal=0,
            text="Chương 2",
        )
        filler_thanks = DocumentChunk(
            document_id=uuid4(),
            course_id=uuid4(),
            page_number=40,
            ordinal=39,
            text="Cảm ơn các bạn đã lắng nghe! Q&A",
        )
        substantive = DocumentChunk(
            document_id=uuid4(),
            course_id=uuid4(),
            page_number=12,
            ordinal=11,
            text="Quy trình kiểm thử phần mềm STLC gồm lập kế hoạch, phân tích yêu cầu, thiết kế test case và báo cáo lỗi.",
        )
        filtered = [c for c in (filler_short, filler_thanks, substantive) if not rag_service._is_filler_slide(c.text)]
        self.assertEqual([c.page_number for c in filtered], [12])


class EndToEndBackendIntegrationTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        await engine.dispose()
        await init_db()
        self.created_course_ids: List[str] = []
        self.tmp_storage = tempfile.TemporaryDirectory()
        self.storage_patch = patch.object(settings, "STORAGE_DIR", self.tmp_storage.name)
        self.storage_patch.start()

    async def asyncTearDown(self):
        # Dọn dẹp toàn bộ course tạo trong lúc test để DB & thư mục luôn sạch sẽ
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://testserver",
        ) as client:
            for cid in self.created_course_ids:
                try:
                    await client.delete(f"/api/v1/courses/{cid}")
                except Exception:
                    pass
        self.storage_patch.stop()
        self.tmp_storage.cleanup()
        await engine.dispose()

    async def test_document_upload_rag_quiz_grading_and_recommendations_flow(self):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://testserver",
        ) as client:
            # 1. Tạo môn học test + kiểm tra chuẩn ISO UTC Z, chống trùng tên & xóa mô tả khi PATCH
            course_res = await client.post(
                "/api/v1/courses",
                json={"name": "__TEST_AUTO_CSDL__", "description": "Automated backend verification"},
            )
            self.assertEqual(course_res.status_code, 201, course_res.text)
            self.assertTrue(course_res.json()["created_at"].endswith("Z"))
            course_id = course_res.json()["id"]
            self.created_course_ids.append(course_id)

            dup_course = await client.post(
                "/api/v1/courses",
                json={"name": "  __test_auto_csdl__  "},
            )
            self.assertEqual(dup_course.status_code, 400)
            self.assertEqual(dup_course.json()["detail"]["code"], "VALIDATION_ERROR")

            clear_desc = await client.patch(
                f"/api/v1/courses/{course_id}",
                json={"name": "__TEST_AUTO_CSDL__", "description": ""},
            )
            self.assertEqual(clear_desc.status_code, 200)
            self.assertIsNone(clear_desc.json()["description"])

            # 2. Hỏi AI & Tạo quiz khi CHƯA có tài liệu sẵn sàng
            empty_ask = await client.post(
                "/api/v1/assistant/ask",
                json={"course_id": course_id, "document_id": None, "question": "Khóa chính là gì?"},
            )
            self.assertEqual(empty_ask.status_code, 200)
            self.assertFalse(empty_ask.json()["found_in_context"])
            self.assertEqual(empty_ask.json()["citations"], [])

            no_docs_quiz = await client.post(
                "/api/v1/quizzes",
                json={"course_id": course_id, "document_id": None, "topic": "Khóa chính", "question_count": 3},
            )
            self.assertEqual(no_docs_quiz.status_code, 400)
            self.assertEqual(no_docs_quiz.json()["detail"]["code"], "VALIDATION_ERROR")

            # 3. Upload file rỗng & sai định dạng phải bị từ chối
            empty_file_res = await client.post(
                f"/api/v1/courses/{course_id}/documents",
                files={"file": ("empty.txt", b"", "text/plain")},
            )
            self.assertEqual(empty_file_res.status_code, 400)
            self.assertEqual(empty_file_res.json()["detail"]["code"], "VALIDATION_ERROR")

            bad_ext_res = await client.post(
                f"/api/v1/courses/{course_id}/documents",
                files={"file": ("slides.docx", b"abc", "application/octet-stream")},
            )
            self.assertEqual(bad_ext_res.status_code, 400)
            self.assertEqual(bad_ext_res.json()["detail"]["code"], "UNSUPPORTED_FILE_TYPE")

            fake_pdf_res = await client.post(
                f"/api/v1/courses/{course_id}/documents",
                files={"file": ("fake.pdf", b"not a real pdf binary", "application/pdf")},
            )
            self.assertEqual(fake_pdf_res.status_code, 400)
            self.assertEqual(fake_pdf_res.json()["detail"]["code"], "UNSUPPORTED_FILE_TYPE")

            bad_mime_res = await client.post(
                f"/api/v1/courses/{course_id}/documents",
                files={"file": ("image.pdf", b"%PDF-1.4\n...", "image/png")},
            )
            self.assertEqual(bad_mime_res.status_code, 400)
            self.assertEqual(bad_mime_res.json()["detail"]["code"], "UNSUPPORTED_FILE_TYPE")

            # 4. Upload PDF hợp lệ với mocked embed_texts -> BackgroundTasks tự chuyển trạng thái ready
            pdf_bytes = _build_minimal_pdf_bytes("Primary Key cannot be NULL. Foreign Key links two relations.")
            with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.9)])):
                upload_res = await client.post(
                    f"/api/v1/courses/{course_id}/documents",
                    files={"file": ("Chuong1_CSDL.pdf", pdf_bytes, "application/pdf")},
                )
            self.assertEqual(upload_res.status_code, 202, upload_res.text)
            doc_id = upload_res.json()["id"]

            # Kiểm tra trạng thái ready và file đã được lưu trên đĩa
            status_res = await client.get(f"/api/v1/documents/{doc_id}/status")
            self.assertEqual(status_res.status_code, 200)
            self.assertEqual(status_res.json()["status"], "ready")
            self.assertEqual(len(os.listdir(self.tmp_storage.name)), 1)

            # Kiểm tra endpoint stream/download file tài liệu gốc cho in-app viewer
            file_res = await client.get(f"/api/v1/documents/{doc_id}/file")
            self.assertEqual(file_res.status_code, 200)
            self.assertTrue(file_res.headers["content-type"].startswith("application/pdf"))
            self.assertIn("inline", file_res.headers.get("content-disposition", "").lower())
            self.assertEqual(file_res.content, pdf_bytes)

            missing_doc_res = await client.get(f"/api/v1/documents/{uuid4()}/file")
            self.assertEqual(missing_doc_res.status_code, 404)
            self.assertEqual(missing_doc_res.json()["detail"]["code"], "RESOURCE_NOT_FOUND")

            # 5. RAG hỏi đáp có trích dẫn nguồn thật & lọc bỏ index bịa ([99])
            mock_rag_output = rag_service._RagLLMOutput(
                found_in_context=True,
                answer="Khóa chính không được nhận NULL và khóa ngoại dùng để liên kết hai bảng.",
                cited_indices=[0, 99],
            )
            with patch("app.services.rag_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.9)])), \
                 patch("app.services.rag_service.generate_structured_json", new=AsyncMock(return_value=mock_rag_output)):
                rag_res = await client.post(
                    "/api/v1/assistant/ask",
                    json={"course_id": course_id, "document_id": doc_id, "question": "Khóa chính là gì?"},
                )
            self.assertEqual(rag_res.status_code, 200, rag_res.text)
            rag_body = rag_res.json()
            self.assertTrue(rag_body["found_in_context"])
            self.assertEqual(len(rag_body["citations"]), 1)
            self.assertEqual(rag_body["citations"][0]["document_id"], doc_id)
            self.assertEqual(rag_body["citations"][0]["document_title"], "Chuong1_CSDL.pdf")
            self.assertEqual(rag_body["citations"][0]["page_number"], 1)
            UUID(rag_body["citations"][0]["chunk_id"])

            # Nếu AI nói found_in_context=True nhưng chỉ trả index không tồn tại -> hạ về False, không bịa citation
            hallucinated_rag = rag_service._RagLLMOutput(
                found_in_context=True,
                answer="Thông tin bịa",
                cited_indices=[42],
            )
            with patch("app.services.rag_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.9)])), \
                 patch("app.services.rag_service.generate_structured_json", new=AsyncMock(return_value=hallucinated_rag)):
                bad_cite_res = await client.post(
                    "/api/v1/assistant/ask",
                    json={"course_id": course_id, "document_id": None, "question": "Nội dung ngoài lề?"},
                )
            self.assertEqual(bad_cite_res.status_code, 200)
            self.assertFalse(bad_cite_res.json()["found_in_context"])
            self.assertEqual(bad_cite_res.json()["citations"], [])

            # Lỗi AI phải trả 503 AI_SERVICE_UNAVAILABLE
            with patch(
                "app.services.rag_service.embed_texts",
                new=AsyncMock(side_effect=gemini_service.AIServiceError("Gemini quota exhausted")),
            ):
                ai_err_res = await client.post(
                    "/api/v1/assistant/ask",
                    json={"course_id": course_id, "document_id": None, "question": "Test lỗi AI"},
                )
            self.assertEqual(ai_err_res.status_code, 503)
            self.assertEqual(ai_err_res.json()["detail"]["code"], "AI_SERVICE_UNAVAILABLE")

            # 6. Sinh Quiz bằng AI (mocked), kiểm tra giấu đáp án, nộp bài (1 đúng, 1 sai, 1 bỏ trống)
            mock_quiz_output = quiz_service._QuizLLMOutput(
                questions=[
                    quiz_service._QuizQuestionLLM(
                        prompt="Câu 1: Khóa chính có được NULL không?",
                        topic="__TEST_WEAK_TOPIC__",
                        options=[
                            quiz_service._QuizOptionLLM(id="A", text="Có thể"),
                            quiz_service._QuizOptionLLM(id="B", text="Không bao giờ"),
                            quiz_service._QuizOptionLLM(id="C", text="Tùy bảng"),
                            quiz_service._QuizOptionLLM(id="D", text="Khi chỉ mục tắt"),
                        ],
                        correct_option_id="B",
                        explanation="Theo toàn vẹn thực thể, khóa chính không thể NULL.",
                        source_chunk_index=0,
                    ),
                    quiz_service._QuizQuestionLLM(
                        prompt="Câu 2: Khóa ngoại dùng để làm gì?",
                        topic="__TEST_WEAK_TOPIC__",
                        options=[
                            quiz_service._QuizOptionLLM(id="A", text="Liên kết quan hệ"),
                            quiz_service._QuizOptionLLM(id="B", text="Xóa bảng"),
                            quiz_service._QuizOptionLLM(id="C", text="Đánh số dòng"),
                            quiz_service._QuizOptionLLM(id="D", text="Nén dữ liệu"),
                        ],
                        correct_option_id="A",
                        explanation="Khóa ngoại đảm bảo toàn vẹn tham chiếu giữa hai quan hệ.",
                        source_chunk_index=0,
                    ),
                    quiz_service._QuizQuestionLLM(
                        prompt="Câu 3: Một quan hệ có tối đa bao nhiêu khóa chính?",
                        topic="__TEST_WEAK_TOPIC__",
                        options=[
                            quiz_service._QuizOptionLLM(id="A", text="1 khóa chính"),
                            quiz_service._QuizOptionLLM(id="B", text="2 khóa chính"),
                            quiz_service._QuizOptionLLM(id="C", text="3 khóa chính"),
                            quiz_service._QuizOptionLLM(id="D", text="Không giới hạn"),
                        ],
                        correct_option_id="A",
                        explanation="Mỗi quan hệ có duy nhất một khóa chính.",
                        source_chunk_index=-1,
                    ),
                ]
            )
            with patch("app.services.quiz_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.9)])), \
                 patch("app.services.quiz_service.generate_structured_json", new=AsyncMock(return_value=mock_quiz_output)):
                quiz_res = await client.post(
                    "/api/v1/quizzes",
                    json={"course_id": course_id, "document_id": doc_id, "topic": "__TEST_WEAK_TOPIC__", "question_count": 3},
                )
            self.assertEqual(quiz_res.status_code, 201, quiz_res.text)
            quiz_body = quiz_res.json()
            quiz_id = quiz_body["id"]
            self.assertEqual(quiz_body["total_questions"], 3)
            for q in quiz_body["questions"]:
                self.assertNotIn("correct_option_id", q)
                self.assertNotIn("explanation", q)

            # Kiểm tra GET quiz cũng giấu đáp án & GET /courses/{id}/quizzes trước khi nộp
            list_quiz_before = await client.get(f"/api/v1/courses/{course_id}/quizzes")
            self.assertEqual(list_quiz_before.status_code, 200)
            self.assertEqual(len(list_quiz_before.json()), 1)
            self.assertEqual(list_quiz_before.json()[0]["attempt_count"], 0)
            self.assertIsNone(list_quiz_before.json()[0]["latest_score"])

            get_quiz_res = await client.get(f"/api/v1/quizzes/{quiz_id}")
            self.assertEqual(get_quiz_res.status_code, 200)
            for q in get_quiz_res.json()["questions"]:
                self.assertNotIn("correct_option_id", q)

            # Nộp bài: Q1 đúng (B), Q2 sai (C), Q3 bỏ trống (omitted) -> 1/3 đúng => score 3.3
            recommendation_service._RECOMMENDATION_CACHE.clear()
            recommendation_service._QUIZ_ADVICE_CACHE.clear()
            q_ids = [q["id"] for q in quiz_body["questions"]]
            with patch(
                "app.services.recommendation_service.generate_structured_json",
                new=AsyncMock(side_effect=gemini_service.AIServiceError("Offline mode fallback check")),
            ):
                submit_res = await client.post(
                    f"/api/v1/quizzes/{quiz_id}/submit",
                    json={
                        "answers": [
                            {"question_id": q_ids[0], "selected_option_id": "B"},
                            {"question_id": q_ids[1], "selected_option_id": "C"},
                        ]
                    },
                )
            self.assertEqual(submit_res.status_code, 200, submit_res.text)
            submit_body = submit_res.json()
            self.assertEqual(submit_body["score"], 3.3)
            self.assertEqual(submit_body["correct_count"], 1)
            self.assertEqual(submit_body["incorrect_count"], 2)
            self.assertIsNotNone(submit_body["study_advice"])
            self.assertIn("Chuong1_CSDL.pdf", submit_body["study_advice"])
            self.assertEqual(submit_body["details"][0]["citation"]["document_id"], doc_id)
            self.assertEqual(submit_body["details"][0]["citation"]["document_title"], "Chuong1_CSDL.pdf")
            self.assertEqual(submit_body["details"][0]["citation"]["page_number"], 1)
            self.assertIsNone(submit_body["details"][2]["selected_option_id"])
            self.assertIsNone(submit_body["details"][2]["citation"])

            list_quiz_after = await client.get(f"/api/v1/courses/{course_id}/quizzes")
            self.assertEqual(list_quiz_after.status_code, 200)
            self.assertEqual(list_quiz_after.json()[0]["attempt_count"], 1)
            self.assertEqual(list_quiz_after.json()[0]["latest_score"], 3.3)

            # 7. Kiểm tra recommendations phát hiện topic yếu (>= 2 câu & < 60%) và dùng lời khuyên LLM khi khả dụng
            async def _fake_rec_llm(*, prompt, system_instruction, schema_cls, timeout_seconds=None):
                indices = [int(m.group(1)) for m in re.finditer(r"\[CHỦ ĐỀ (\d+)\]", prompt)]
                return recommendation_service._RecommendationAdviceBatchOutput(
                    items=[
                        recommendation_service._TopicAdviceLLM(
                            topic_index=i,
                            advice=f"Gợi ý cá nhân hóa #{i}: xem kỹ trang nguồn và làm thêm 5 câu luyện tập.",
                        )
                        for i in indices
                    ]
                )

            with patch(
                "app.services.recommendation_service.generate_structured_json",
                new=AsyncMock(side_effect=_fake_rec_llm),
            ), patch("app.services.recommendation_service.is_gemini_configured", return_value=True):
                rec_res = await client.get("/api/v1/recommendations")
            self.assertEqual(rec_res.status_code, 200, rec_res.text)
            weak_items = [r for r in rec_res.json() if r["topic"] == "__TEST_WEAK_TOPIC__"]
            self.assertEqual(len(weak_items), 1)
            self.assertEqual(weak_items[0]["total_answered"], 3)
            self.assertEqual(weak_items[0]["correct_count"], 1)
            self.assertAlmostEqual(weak_items[0]["accuracy"], 0.33, places=2)
            self.assertEqual(weak_items[0]["suggested_document"]["id"], doc_id)
            self.assertEqual(weak_items[0]["suggested_pages"], [1])
            self.assertIn("Gợi ý cá nhân hóa", weak_items[0]["recommended_action"])

            # 8. Xóa môn học -> phải xóa cả file vật lý trong storage
            del_res = await client.delete(f"/api/v1/courses/{course_id}")
            self.assertEqual(del_res.status_code, 204)
            self.created_course_ids.remove(course_id)
            self.assertEqual(len(os.listdir(self.tmp_storage.name)), 0)

        # Kiểm tra upload thất bại khi thiếu API key chuyển status='error' thay vì treo 'processing'
        with patch.object(settings, "GEMINI_API_KEY", "your_gemini_api_key_here"):
            async with httpx.AsyncClient(
                transport=httpx.ASGITransport(app=app),
                base_url="http://testserver",
            ) as client:
                c2 = await client.post("/api/v1/courses", json={"name": "__TEST_ERR_COURSE__"})
                c2_id = c2.json()["id"]
                self.created_course_ids.append(c2_id)
                up2 = await client.post(
                    f"/api/v1/courses/{c2_id}/documents",
                    files={"file": ("fail.txt", b"Du lieu test khong co key", "text/plain")},
                )
                self.assertEqual(up2.status_code, 202)
                st2 = await client.get(f"/api/v1/documents/{up2.json()['id']}/status")
                self.assertEqual(st2.json()["status"], "error")
                self.assertIn("GEMINI_API_KEY", st2.json()["error_message"] or "")

                # Thử xử lý lại tài liệu ở trạng thái error khi đã có embedder hợp lệ -> chuyển sang ready
                doc2_id = up2.json()["id"]
                with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.7)])):
                    retry_res = await client.post(f"/api/v1/documents/{doc2_id}/retry")
                self.assertEqual(retry_res.status_code, 202, retry_res.text)
                st2_after = await client.get(f"/api/v1/documents/{doc2_id}/status")
                self.assertEqual(st2_after.json()["status"], "ready")
                self.assertIsNone(st2_after.json()["error_message"])

                txt_file_res = await client.get(f"/api/v1/documents/{doc2_id}/file")
                self.assertEqual(txt_file_res.status_code, 200)
                self.assertTrue(txt_file_res.headers["content-type"].startswith("text/plain"))
                self.assertEqual(txt_file_res.text, "Du lieu test khong co key")

                # Nếu file gốc trên đĩa bị mất, retry phải báo 400 VALIDATION_ERROR rõ ràng và /file phải trả 404
                for name in os.listdir(self.tmp_storage.name):
                    os.remove(os.path.join(self.tmp_storage.name, name))
                retry_missing = await client.post(f"/api/v1/documents/{doc2_id}/retry")
                self.assertEqual(retry_missing.status_code, 400)
                self.assertEqual(retry_missing.json()["detail"]["code"], "VALIDATION_ERROR")
                missing_disk_res = await client.get(f"/api/v1/documents/{doc2_id}/file")
                self.assertEqual(missing_disk_res.status_code, 404)
                self.assertEqual(missing_disk_res.json()["detail"]["code"], "RESOURCE_NOT_FOUND")

    async def test_gemini_structured_json_fallback_on_timeout(self):
        class _DummyInteraction:
            status = "completed"
            output_text = '{"found_in_context": true, "answer": "OK fallback", "cited_indices": [0]}'

        called_models: List[str] = []

        class _FakeInteractions:
            async def create(self, **kwargs):
                called_models.append(kwargs["model"])
                if kwargs["model"] == "gemini-3.8-flash":
                    raise TimeoutError("simulated timeout")
                return _DummyInteraction()

        class _FakeAio:
            interactions = _FakeInteractions()

        class _FakeClient:
            aio = _FakeAio()

        with patch.object(settings, "GEMINI_MODEL", "gemini-3.8-flash"), \
             patch.object(settings, "GEMINI_FALLBACK_MODELS", "gemini-3.5-flash-lite"), \
             patch("app.services.gemini_service.get_genai_client", return_value=_FakeClient()):
            res = await gemini_service.generate_structured_json(
                prompt="test prompt",
                system_instruction="test sys",
                schema_cls=rag_service._RagLLMOutput,
            )
        self.assertEqual(called_models, ["gemini-3.8-flash", "gemini-3.5-flash-lite"])
        self.assertEqual(res.answer, "OK fallback")

    async def test_embed_texts_batch_split_and_429_retry_recovery(self):
        class _DummyEmb:
            def __init__(self, val: float):
                self.values = _deterministic_vec(val)

        class _DummyResp:
            def __init__(self, n: int):
                self.embeddings = [_DummyEmb(0.1 * (i + 1)) for i in range(n)]

        calls: List[int] = []

        class _FakeModels:
            async def embed_content(self, **kwargs):
                batch_len = len(kwargs["contents"])
                calls.append(batch_len)
                # Mô phỏng batch 2 bị 429 lần đầu rồi thành công ở lần thử lại
                if len(calls) == 2:
                    raise genai_errors.ClientError(
                        429,
                        {"error": {"code": 429, "status": "RESOURCE_EXHAUSTED", "message": "Quota exceeded"}},
                    )
                return _DummyResp(batch_len)

        class _FakeAio:
            models = _FakeModels()

        class _FakeClient:
            aio = _FakeAio()

        sample_texts = [f"chunk {i}" for i in range(45)]
        with patch("app.services.gemini_service.get_genai_client", return_value=_FakeClient()):
            vectors = await gemini_service.embed_texts(
                sample_texts,
                batch_size=20,
                retry_delays=(0.01, 0.01),
                inter_batch_delay=0.0,
            )

        self.assertEqual(len(vectors), 45)
        self.assertEqual(calls, [20, 20, 20, 5])

        class _AuthFailModels:
            async def embed_content(self, **kwargs):
                raise genai_errors.ClientError(
                    403,
                    {"error": {"code": 403, "status": "PERMISSION_DENIED", "message": "Invalid key"}},
                )

        class _AuthFailAio:
            models = _AuthFailModels()

        class _AuthFailClient:
            aio = _AuthFailAio()

        with patch("app.services.gemini_service.get_genai_client", return_value=_AuthFailClient()):
            with self.assertRaises(gemini_service.AIServiceError) as ctx:
                await gemini_service.embed_texts(
                    ["single chunk"],
                    retry_delays=(0.01,),
                    inter_batch_delay=0.0,
                )
        self.assertIn("API key", str(ctx.exception))

    async def test_recover_orphaned_processing_documents_and_retry_guard(self):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://testserver",
        ) as client:
            c_res = await client.post("/api/v1/courses", json={"name": "__TEST_ORPHAN_DOC__"})
            self.assertEqual(c_res.status_code, 201, c_res.text)
            c_id = c_res.json()["id"]
            self.created_course_ids.append(c_id)

            pdf_bytes = _build_minimal_pdf_bytes("Stale processing recovery test.")
            with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.6)])):
                up_res = await client.post(
                    f"/api/v1/courses/{c_id}/documents",
                    files={"file": ("orphan_test.pdf", pdf_bytes, "application/pdf")},
                )
            self.assertEqual(up_res.status_code, 202, up_res.text)
            doc_id = UUID(up_res.json()["id"])

            # Giả lập tài liệu bị treo ở trạng thái 'processing' do server restart (không có worker active)
            async with AsyncSessionLocal() as db:
                db_doc = await db.get(Document, doc_id)
                self.assertIsNotNone(db_doc)
                db_doc.status = "processing"
                db_doc.error_message = None
                await db.commit()

            # Khi đánh dấu active -> retry phải giữ idempotent (không chạy lại nền)
            document_service.mark_document_queued(doc_id)
            try:
                with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.6)])) as mocked_embed:
                    idempotent_retry = await client.post(f"/api/v1/documents/{doc_id}/retry")
                self.assertEqual(idempotent_retry.status_code, 202)
                self.assertEqual(mocked_embed.await_count, 0)
            finally:
                document_service.clear_document_active(doc_id)

            # Khi không còn active trong bộ nhớ -> retry phải phục hồi & xử lý lại thành ready
            with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.6)])) as mocked_embed:
                recover_retry = await client.post(f"/api/v1/documents/{doc_id}/retry")
            self.assertEqual(recover_retry.status_code, 202)
            self.assertGreaterEqual(mocked_embed.await_count, 1)
            st_ready = await client.get(f"/api/v1/documents/{doc_id}/status")
            self.assertEqual(st_ready.json()["status"], "ready")

            # Kiểm tra hàm startup recovery dọn các dòng processing mồ côi sang error
            async with AsyncSessionLocal() as db:
                db_doc = await db.get(Document, doc_id)
                db_doc.status = "processing"
                db_doc.error_message = None
                await db.commit()
                recovered_count = await document_service.recover_orphaned_processing_documents(db)
                self.assertGreaterEqual(recovered_count, 1)
                await db.refresh(db_doc)
                self.assertEqual(db_doc.status, "error")
                self.assertIn("gián đoạn", db_doc.error_message or "")

    async def test_recommendations_threshold_sorting_normalization_and_fallback(self):
        recommendation_service._RECOMMENDATION_CACHE.clear()
        recommendation_service._QUIZ_ADVICE_CACHE.clear()
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://testserver",
        ) as client:
            c_res = await client.post("/api/v1/courses", json={"name": "__TEST_REC_LOGIC__"})
            self.assertEqual(c_res.status_code, 201, c_res.text)
            c_id = c_res.json()["id"]
            self.created_course_ids.append(c_id)

            pdf_bytes = _build_minimal_pdf_bytes("Black-box testing and Bug Lifecycle notes.")
            with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.4)])):
                up_res = await client.post(
                    f"/api/v1/courses/{c_id}/documents",
                    files={"file": ("Chuong2_Test.pdf", pdf_bytes, "application/pdf")},
                )
            self.assertEqual(up_res.status_code, 202, up_res.text)
            doc_id = up_res.json()["id"]

            def _q(prompt: str, topic: str, explanation: str):
                return quiz_service._QuizQuestionLLM(
                    prompt=prompt,
                    topic=topic,
                    options=[
                        quiz_service._QuizOptionLLM(id="A", text="Đáp án A"),
                        quiz_service._QuizOptionLLM(id="B", text="Đáp án B"),
                        quiz_service._QuizOptionLLM(id="C", text="Đáp án C"),
                        quiz_service._QuizOptionLLM(id="D", text="Đáp án D"),
                    ],
                    correct_option_id="A",
                    explanation=explanation,
                    source_chunk_index=0,
                )

            # Quiz 1 (5 câu, 3 nhóm topic -> không vượt giới hạn 3 nhóm):
            # - __REC_ZERO__ (0/2 -> 0%), __REC_VARIANT__ (1/2 -> 50%, gộp biến thể dấu ngoặc), __REC_SINGLE__ (0/1 -> bị loại do < 2 câu)
            mock_quiz_1 = quiz_service._QuizLLMOutput(
                questions=[
                    _q("Zero 1", "__REC_ZERO__", "Bug chuyển Retest sau khi sửa."),
                    _q("Zero 2", "__REC_ZERO__", "Closed khi QA xác nhận hết lỗi."),
                    _q("Var 1", "__REC_VARIANT__ (Alpha)", "Phân vùng tương đương giảm số ca test."),
                    _q("Var 2", "__REC_VARIANT__ Alpha", "Giá trị biên tập trung tại ngưỡng min/max."),
                    _q("Single 1", "__REC_SINGLE__", "Chỉ làm 1 câu chưa đủ ngưỡng thống kê."),
                ]
            )
            with patch("app.services.quiz_service.generate_structured_json", new=AsyncMock(return_value=mock_quiz_1)):
                q1_res = await client.post(
                    "/api/v1/quizzes",
                    json={"course_id": c_id, "document_id": doc_id, "question_count": 5},
                )
            self.assertEqual(q1_res.status_code, 201, q1_res.text)
            q1_ids = [q["id"] for q in q1_res.json()["questions"]]

            # Submit Quiz 1 với Gemini fallback lỗi -> vẫn phải 200 OK và có study_advice sát thực tế
            with patch(
                "app.services.recommendation_service.generate_structured_json",
                new=AsyncMock(side_effect=gemini_service.AIServiceError("Simulated 429")),
            ), patch("app.services.recommendation_service.is_gemini_configured", return_value=True):
                sub1_res = await client.post(
                    f"/api/v1/quizzes/{q1_res.json()['id']}/submit",
                    json={
                        "answers": [
                            {"question_id": q1_ids[0], "selected_option_id": "B"},  # Zero 1: sai
                            {"question_id": q1_ids[1], "selected_option_id": "B"},  # Zero 2: sai (0/2)
                            {"question_id": q1_ids[2], "selected_option_id": "A"},  # Var 1: đúng
                            {"question_id": q1_ids[3], "selected_option_id": "B"},  # Var 2: sai (1/2)
                            {"question_id": q1_ids[4], "selected_option_id": "B"},  # Single: sai (0/1)
                        ]
                    },
                )
            self.assertEqual(sub1_res.status_code, 200, sub1_res.text)
            sub1_body = sub1_res.json()
            self.assertIn("Chuong2_Test.pdf", sub1_body["study_advice"])
            # topic_stats trong kết quả phải gộp __REC_VARIANT__ (Alpha) và __REC_VARIANT__ Alpha
            variant_stats = [t for t in sub1_body["topic_stats"] if "__REC_VARIANT__" in t["topic"]]
            self.assertEqual(len(variant_stats), 1)
            self.assertEqual(variant_stats[0]["total_questions"], 2)

            # Quiz 2 (3 câu __REC_PASS__, đạt 2/3 -> 67% >= 60% -> phải bị loại khỏi recommendations)
            mock_quiz_2 = quiz_service._QuizLLMOutput(
                questions=[
                    _q("Pass 1", "__REC_PASS__", "Đạt 1."),
                    _q("Pass 2", "__REC_PASS__", "Đạt 2."),
                    _q("Pass 3", "__REC_PASS__", "Sai 3."),
                ]
            )
            with patch("app.services.quiz_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.4)])), \
                 patch("app.services.quiz_service.generate_structured_json", new=AsyncMock(return_value=mock_quiz_2)):
                q2_res = await client.post(
                    "/api/v1/quizzes",
                    json={"course_id": c_id, "document_id": doc_id, "topic": "__REC_PASS__", "question_count": 3},
                )
            self.assertEqual(q2_res.status_code, 201, q2_res.text)
            q2_ids = [q["id"] for q in q2_res.json()["questions"]]
            with patch(
                "app.services.recommendation_service.generate_structured_json",
                new=AsyncMock(side_effect=gemini_service.AIServiceError("Simulated 429")),
            ), patch("app.services.recommendation_service.is_gemini_configured", return_value=True):
                sub2_res = await client.post(
                    f"/api/v1/quizzes/{q2_res.json()['id']}/submit",
                    json={
                        "answers": [
                            {"question_id": q2_ids[0], "selected_option_id": "A"},
                            {"question_id": q2_ids[1], "selected_option_id": "A"},
                            {"question_id": q2_ids[2], "selected_option_id": "B"},
                        ]
                    },
                )
            self.assertEqual(sub2_res.status_code, 200, sub2_res.text)

            # Kiểm tra GET /recommendations ở chế độ fallback (LLM lỗi):
            # - Chỉ trả __REC_ZERO__ (0.0) rồi tới __REC_VARIANT__ (0.5)
            # - Loại __REC_SINGLE__ (< 2 câu) và __REC_PASS__ (>= 60%)
            with patch(
                "app.services.recommendation_service.generate_structured_json",
                new=AsyncMock(side_effect=gemini_service.AIServiceError("Simulated 429")),
            ), patch("app.services.recommendation_service.is_gemini_configured", return_value=True):
                rec_fallback = await client.get("/api/v1/recommendations")
            self.assertEqual(rec_fallback.status_code, 200, rec_fallback.text)
            scoped = [r for r in rec_fallback.json() if r["course_id"] == c_id]
            self.assertEqual(len(scoped), 2)
            self.assertEqual(scoped[0]["topic"], "__REC_ZERO__")
            self.assertAlmostEqual(scoped[0]["accuracy"], 0.0, places=2)
            self.assertIn("__REC_VARIANT__", scoped[1]["topic"])
            self.assertAlmostEqual(scoped[1]["accuracy"], 0.5, places=2)
            for item in scoped:
                self.assertNotIn("Xem lại tài liệu liên quan đến chủ đề", item["recommended_action"])
                self.assertIn("Chuong2_Test.pdf", item["recommended_action"])
                self.assertIn("Trang 1", item["recommended_action"])

            # Kiểm tra cache + Gemini wording khi LLM hoạt động
            async def _fake_rec_llm(*, prompt, system_instruction, schema_cls, timeout_seconds=None):
                indices = [int(m.group(1)) for m in re.finditer(r"\[CHỦ ĐỀ (\d+)\]", prompt)]
                return recommendation_service._RecommendationAdviceBatchOutput(
                    items=[
                        recommendation_service._TopicAdviceLLM(
                            topic_index=i,
                            advice=f"Lời khuyên AI #{i}: đọc lại phần giải thích và Trang 1 tài liệu Chuong2_Test.pdf.",
                        )
                        for i in indices
                    ]
                )

            recommendation_service._RECOMMENDATION_CACHE.clear()
            mock_llm = AsyncMock(side_effect=_fake_rec_llm)
            with patch("app.services.recommendation_service.generate_structured_json", new=mock_llm), \
                 patch("app.services.recommendation_service.is_gemini_configured", return_value=True):
                r1 = await client.get("/api/v1/recommendations")
                r2 = await client.get("/api/v1/recommendations")
            self.assertEqual(r1.status_code, 200)
            self.assertEqual(r2.status_code, 200)
            self.assertEqual(mock_llm.await_count, 1)  # Lần gọi thứ 2 phải dùng cache, không gọi lại LLM
            scoped_llm = [r for r in r1.json() if r["course_id"] == c_id]
            self.assertEqual(len(scoped_llm), 2)
            self.assertIn("Lời khuyên AI", scoped_llm[0]["recommended_action"])

    async def test_multi_document_overview_rag_and_followup_history(self):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://testserver",
        ) as client:
            c_res = await client.post("/api/v1/courses", json={"name": "__TEST_MULTI_RAG__"})
            self.assertEqual(c_res.status_code, 201, c_res.text)
            c_id = c_res.json()["id"]
            self.created_course_ids.append(c_id)

            pdf1 = _build_minimal_pdf_bytes("Chapter 1 Overview: Software Testing principles, bug severity and priority.")
            pdf2 = _build_minimal_pdf_bytes("Chapter 2 STLC: Planning, Test Design, Execution and Closure phases.")
            with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.2)])):
                u1 = await client.post(
                    f"/api/v1/courses/{c_id}/documents",
                    files={"file": ("Chuong1_TongQuan.pdf", pdf1, "application/pdf")},
                )
            with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.8)])):
                u2 = await client.post(
                    f"/api/v1/courses/{c_id}/documents",
                    files={"file": ("Chuong2_STLC.pdf", pdf2, "application/pdf")},
                )
            self.assertEqual(u1.status_code, 202, u1.text)
            self.assertEqual(u2.status_code, 202, u2.text)

            captured_prompts: List[str] = []

            async def _fake_rag_overview(*, prompt, system_instruction, schema_cls, timeout_seconds=None):
                captured_prompts.append(prompt)
                return rag_service._RagLLMOutput(
                    found_in_context=True,
                    answer="## Tổng hợp ([ĐOẠN 0, ĐOẠN 1])\n- Chương 1: Nguyên lý kiểm thử.\n- Chương 2: Quy trình STLC.",
                    cited_indices=[0, 1],
                )

            with patch("app.services.rag_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.2)])), \
                 patch("app.services.rag_service.generate_structured_json", new=AsyncMock(side_effect=_fake_rag_overview)):
                ov_res = await client.post(
                    "/api/v1/assistant/ask",
                    json={"course_id": c_id, "document_id": None, "question": "Tóm tắt nội dung chính từng tài liệu"},
                )
            self.assertEqual(ov_res.status_code, 200, ov_res.text)
            ov_body = ov_res.json()
            self.assertTrue(ov_body["found_in_context"])
            self.assertNotIn("[ĐOẠN", ov_body["answer"])
            self.assertEqual(len(ov_body["citations"]), 2)
            cited_titles = {c["document_title"] for c in ov_body["citations"]}
            self.assertEqual(cited_titles, {"Chuong1_TongQuan.pdf", "Chuong2_STLC.pdf"})
            self.assertIn("Chuong1_TongQuan.pdf", captured_prompts[-1])
            self.assertIn("Chuong2_STLC.pdf", captured_prompts[-1])

            # Kiểm tra câu hỏi nối tiếp kèm history -> phải rewrite query nhúng & truyền history vào prompt
            mock_embed = AsyncMock(return_value=[_deterministic_vec(0.8)])
            with patch("app.services.rag_service.embed_texts", new=mock_embed), \
                 patch("app.services.rag_service.generate_structured_json", new=AsyncMock(side_effect=_fake_rag_overview)):
                fu_res = await client.post(
                    "/api/v1/assistant/ask",
                    json={
                        "course_id": c_id,
                        "document_id": None,
                        "question": "Giải thích chi tiết hơn",
                        "history": [
                            {"role": "user", "content": "Quy trình STLC trong Chương 2 gồm những bước nào?"},
                            {"role": "assistant", "content": "STLC gồm các pha lập kế hoạch, thiết kế, thực thi và đóng."},
                        ],
                    },
                )
            self.assertEqual(fu_res.status_code, 200, fu_res.text)
            embedded_query_arg = mock_embed.call_args[0][0][0]
            self.assertIn("STLC", embedded_query_arg)
            self.assertIn("Ngữ cảnh hội thoại gần đây", captured_prompts[-1])

    async def test_multi_session_assistant_history_course_grouping_and_anti_drift(self):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://testserver",
        ) as client:
            c1_res = await client.post("/api/v1/courses", json={"name": "__TEST_CHAT_GRP_1__"})
            c2_res = await client.post("/api/v1/courses", json={"name": "__TEST_CHAT_GRP_2__"})
            self.assertEqual(c1_res.status_code, 201, c1_res.text)
            self.assertEqual(c2_res.status_code, 201, c2_res.text)
            c1_id = c1_res.json()["id"]
            c2_id = c2_res.json()["id"]
            self.created_course_ids.extend([c1_id, c2_id])

            pdf_1a = _build_minimal_pdf_bytes("Equivalence Partitioning splits input domains cleanly.")
            pdf_1b = _build_minimal_pdf_bytes("Whitebox Statement Coverage guarantees every line executes.")
            pdf_2 = _build_minimal_pdf_bytes("Normalization 3NF eliminates transitive functional dependencies.")

            with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.15)])):
                u1a = await client.post(
                    f"/api/v1/courses/{c1_id}/documents",
                    files={"file": ("Chuong2_Blackbox.pdf", pdf_1a, "application/pdf")},
                )
            with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.65)])):
                u1b = await client.post(
                    f"/api/v1/courses/{c1_id}/documents",
                    files={"file": ("Chuong3_Whitebox.pdf", pdf_1b, "application/pdf")},
                )
            with patch("app.services.document_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.85)])):
                u2 = await client.post(
                    f"/api/v1/courses/{c2_id}/documents",
                    files={"file": ("Chuong4_3NF.pdf", pdf_2, "application/pdf")},
                )
            self.assertEqual(u1a.status_code, 202, u1a.text)
            self.assertEqual(u1b.status_code, 202, u1b.text)
            self.assertEqual(u2.status_code, 202, u2.text)
            doc1a_id = u1a.json()["id"]
            doc1b_id = u1b.json()["id"]

            # Lỗi AI (503) tuyệt đối không được tạo phiên rác
            with patch(
                "app.services.rag_service.embed_texts",
                new=AsyncMock(side_effect=gemini_service.AIServiceError("Temporary outage")),
            ):
                fail_res = await client.post(
                    "/api/v1/assistant/ask",
                    json={"course_id": c1_id, "document_id": doc1a_id, "question": "Lỗi mạng thử nghiệm"},
                )
            self.assertEqual(fail_res.status_code, 503)
            empty_sessions = await client.get(f"/api/v1/courses/{c1_id}/assistant/sessions")
            self.assertEqual(empty_sessions.status_code, 200)
            self.assertEqual(empty_sessions.json(), [])

            captured_prompts: List[str] = []

            async def _fake_rag(*, prompt, system_instruction, schema_cls, timeout_seconds=None):
                captured_prompts.append(prompt)
                return rag_service._RagLLMOutput(
                    found_in_context=True,
                    answer="## Kết quả\n- Phân tích dựa trên **tài liệu** `được chọn` [ĐOẠN 0].",
                    cited_indices=[0],
                )

            # Lượt 1 ở C1 (phiên S1a trên doc1a)
            with patch("app.services.rag_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.15)])), \
                 patch("app.services.rag_service.generate_structured_json", new=AsyncMock(side_effect=_fake_rag)):
                t1 = await client.post(
                    "/api/v1/assistant/ask",
                    json={"course_id": c1_id, "document_id": doc1a_id, "question": "Phân vùng tương đương là gì?"},
                )
            self.assertEqual(t1.status_code, 200, t1.text)
            s1a_id = t1.json()["session_id"]
            self.assertIsNotNone(s1a_id)
            self.assertEqual(t1.json()["session_title"], "Phân vùng tương đương là gì?")
            self.assertEqual(len(t1.json()["citations"]), 1)

            # Không cho phép dùng session của C1 khi chọn course_id = C2
            cross_course = await client.post(
                "/api/v1/assistant/ask",
                json={"course_id": c2_id, "session_id": s1a_id, "question": "Hỏi chéo môn"},
            )
            self.assertEqual(cross_course.status_code, 400)
            self.assertEqual(cross_course.json()["detail"]["code"], "VALIDATION_ERROR")

            # Lượt 2 nối tiếp trên cùng phiên S1a & cùng doc1a -> phải nạp ngữ cảnh từ DB vào prompt
            with patch("app.services.rag_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.15)])), \
                 patch("app.services.rag_service.generate_structured_json", new=AsyncMock(side_effect=_fake_rag)):
                t2 = await client.post(
                    "/api/v1/assistant/ask",
                    json={"course_id": c1_id, "session_id": s1a_id, "document_id": doc1a_id, "question": "Giải thích chi tiết hơn"},
                )
            self.assertEqual(t2.status_code, 200, t2.text)
            self.assertIn("Ngữ cảnh hội thoại gần đây", captured_prompts[-1])
            self.assertIn("Phân vùng tương đương là gì?", captured_prompts[-1])

            # Lượt 3 đổi phạm vi sang doc1b trong cùng S1a -> chống trôi ngữ cảnh, không kéo prompt của doc1a
            mock_embed_switch = AsyncMock(return_value=[_deterministic_vec(0.65)])
            with patch("app.services.rag_service.embed_texts", new=mock_embed_switch), \
                 patch("app.services.rag_service.generate_structured_json", new=AsyncMock(side_effect=_fake_rag)):
                t3 = await client.post(
                    "/api/v1/assistant/ask",
                    json={
                        "course_id": c1_id,
                        "session_id": s1a_id,
                        "document_id": doc1b_id,
                        "question": "Giải thích chi tiết hơn",
                        "history": [{"role": "user", "content": "Phân vùng tương đương là gì?"}],
                    },
                )
            self.assertEqual(t3.status_code, 200, t3.text)
            self.assertEqual(mock_embed_switch.call_args[0][0][0], "Giải thích chi tiết hơn")
            self.assertNotIn("Ngữ cảnh hội thoại gần đây", captured_prompts[-1])
            self.assertEqual(t3.json()["citations"][0]["document_id"], doc1b_id)

            # Tạo thêm phiên chat S1b mới trong C1 & phiên S2 riêng trong C2
            with patch("app.services.rag_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.15)])), \
                 patch("app.services.rag_service.generate_structured_json", new=AsyncMock(side_effect=_fake_rag)):
                t_s1b = await client.post(
                    "/api/v1/assistant/ask",
                    json={"course_id": c1_id, "document_id": None, "question": "Phiên thứ hai môn KTPM"},
                )
            s1b_id = t_s1b.json()["session_id"]
            self.assertNotEqual(s1b_id, s1a_id)

            with patch("app.services.rag_service.embed_texts", new=AsyncMock(return_value=[_deterministic_vec(0.85)])), \
                 patch("app.services.rag_service.generate_structured_json", new=AsyncMock(side_effect=_fake_rag)):
                t_s2 = await client.post(
                    "/api/v1/assistant/ask",
                    json={"course_id": c2_id, "document_id": None, "question": "Chuẩn 3NF là gì?"},
                )
            s2_id = t_s2.json()["session_id"]

            # Kiểm tra group theo môn học: C1 chỉ thấy S1b, S1a; C2 chỉ thấy S2
            c1_sessions_res = await client.get(f"/api/v1/courses/{c1_id}/assistant/sessions")
            self.assertEqual(c1_sessions_res.status_code, 200)
            c1_sessions = c1_sessions_res.json()
            self.assertEqual([s["id"] for s in c1_sessions], [s1b_id, s1a_id])
            self.assertEqual(c1_sessions[1]["message_count"], 6)
            self.assertEqual(c1_sessions[1]["document_id"], doc1b_id)
            self.assertEqual(c1_sessions[1]["document_title"], "Chuong3_Whitebox.pdf")
            preview = c1_sessions[0]["last_message_preview"] or ""
            self.assertTrue(preview.startswith("Kết quả: Phân tích"))
            self.assertNotIn("##", preview)
            self.assertNotIn("**", preview)
            self.assertNotIn("`", preview)

            c2_sessions_res = await client.get(f"/api/v1/courses/{c2_id}/assistant/sessions")
            self.assertEqual(c2_sessions_res.status_code, 200)
            self.assertEqual([s["id"] for s in c2_sessions_res.json()], [s2_id])

            # Kiểm tra thứ tự tin nhắn & citations trong S1a
            msgs_res = await client.get(f"/api/v1/assistant/sessions/{s1a_id}/messages")
            self.assertEqual(msgs_res.status_code, 200)
            msgs = msgs_res.json()
            self.assertEqual(len(msgs), 6)
            self.assertEqual([m["role"] for m in msgs], ["user", "assistant", "user", "assistant", "user", "assistant"])
            self.assertEqual(msgs[0]["content"], "Phân vùng tương đương là gì?")
            self.assertEqual(len(msgs[1]["citations"]), 1)
            self.assertTrue(msgs[0]["created_at"].endswith("Z"))

            # Xóa riêng S1b -> không ảnh hưởng S1a
            del_s1b = await client.delete(f"/api/v1/assistant/sessions/{s1b_id}")
            self.assertEqual(del_s1b.status_code, 204)
            after_del_msgs = await client.get(f"/api/v1/assistant/sessions/{s1b_id}/messages")
            self.assertEqual(after_del_msgs.status_code, 404)
            remaining_c1 = await client.get(f"/api/v1/courses/{c1_id}/assistant/sessions")
            self.assertEqual([s["id"] for s in remaining_c1.json()], [s1a_id])


if __name__ == "__main__":
    unittest.main()

