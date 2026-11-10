import os
import sys
import tempfile
import unittest
from pathlib import Path
from typing import List, Optional
from unittest.mock import AsyncMock, patch
from uuid import UUID

import httpx
from google.genai import errors as genai_errors
from pydantic import BaseModel

# Đảm bảo import được app khi chạy từ thư mục backend hoặc thư mục gốc
BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.config import settings
from app.core.database import engine
from app.main import app
from app.services import document_service, gemini_service, quiz_service, rag_service


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


class EndToEndBackendIntegrationTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        await engine.dispose()
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
            q_ids = [q["id"] for q in quiz_body["questions"]]
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
            self.assertEqual(submit_body["details"][0]["citation"]["document_title"], "Chuong1_CSDL.pdf")
            self.assertEqual(submit_body["details"][0]["citation"]["page_number"], 1)
            self.assertIsNone(submit_body["details"][2]["selected_option_id"])
            self.assertIsNone(submit_body["details"][2]["citation"])

            list_quiz_after = await client.get(f"/api/v1/courses/{course_id}/quizzes")
            self.assertEqual(list_quiz_after.status_code, 200)
            self.assertEqual(list_quiz_after.json()[0]["attempt_count"], 1)
            self.assertEqual(list_quiz_after.json()[0]["latest_score"], 3.3)

            # 7. Kiểm tra recommendations phát hiện topic yếu (>= 3 câu & < 60%)
            rec_res = await client.get("/api/v1/recommendations")
            self.assertEqual(rec_res.status_code, 200, rec_res.text)
            weak_items = [r for r in rec_res.json() if r["topic"] == "__TEST_WEAK_TOPIC__"]
            self.assertEqual(len(weak_items), 1)
            self.assertEqual(weak_items[0]["total_answered"], 3)
            self.assertEqual(weak_items[0]["correct_count"], 1)
            self.assertAlmostEqual(weak_items[0]["accuracy"], 0.33, places=2)
            self.assertEqual(weak_items[0]["suggested_document"]["id"], doc_id)

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

                # Nếu file gốc trên đĩa bị mất, retry phải báo 400 VALIDATION_ERROR rõ ràng
                for name in os.listdir(self.tmp_storage.name):
                    os.remove(os.path.join(self.tmp_storage.name, name))
                retry_missing = await client.post(f"/api/v1/documents/{doc2_id}/retry")
                self.assertEqual(retry_missing.status_code, 400)
                self.assertEqual(retry_missing.json()["detail"]["code"], "VALIDATION_ERROR")

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


if __name__ == "__main__":
    unittest.main()

