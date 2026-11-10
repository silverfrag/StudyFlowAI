package com.studyflow.ai

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.studyflow.ai.data.api.RetrofitClient
import com.studyflow.ai.data.model.AnswerSubmission
import com.studyflow.ai.data.model.AskRequest
import com.studyflow.ai.data.model.AskResponse
import com.studyflow.ai.data.model.ChatHistoryItem
import com.studyflow.ai.data.model.CourseCreateRequest
import com.studyflow.ai.data.model.CourseResponse
import com.studyflow.ai.data.model.DocumentResponse
import com.studyflow.ai.data.model.DocumentStatusResponse
import com.studyflow.ai.data.model.HealthResponse
import com.studyflow.ai.data.model.ProgressSummaryResponse
import com.studyflow.ai.data.model.QuizCreateRequest
import com.studyflow.ai.data.model.QuizResponse
import com.studyflow.ai.data.model.QuizSubmitRequest
import com.studyflow.ai.data.model.QuizSubmitResponse
import com.studyflow.ai.data.model.QuizSummaryResponse
import com.studyflow.ai.data.model.RecommendationItem
import com.studyflow.ai.data.model.StandardErrorEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra tương thích DTO Android với toàn bộ JSON mẫu trong docs/api-contract.md (v1.1).
 */
class ApiContractParsingTest {
    private val gson = Gson()

    @Test
    fun parseValidationAndStandardErrorEnvelope() {
        val json = """
            {
              "detail": {
                "code": "VALIDATION_ERROR",
                "message": "Dữ liệu gửi lên không hợp lệ.",
                "field": "body.question_count",
                "errors": [
                  {
                    "field": "body.question_count",
                    "message": "Giá trị phải nhỏ hơn hoặc bằng 10."
                  }
                ]
              }
            }
        """.trimIndent()

        val envelope = gson.fromJson(json, StandardErrorEnvelope::class.java)
        assertEquals("VALIDATION_ERROR", envelope.detail.code)
        assertEquals("Dữ liệu gửi lên không hợp lệ.", envelope.detail.message)
        assertEquals("body.question_count", envelope.detail.field)
        assertEquals(1, envelope.detail.errors?.size)
        assertEquals("Dữ liệu gửi lên không hợp lệ.", RetrofitClient.parseError(json))
    }

    @Test
    fun parseHealthCourseAndDocumentResponses() {
        val healthJson = """{"status":"healthy","database":"connected","version":"1.0.0"}"""
        val health = gson.fromJson(healthJson, HealthResponse::class.java)
        assertEquals("healthy", health.status)
        assertEquals("connected", health.database)

        val coursesJson = """
            [
              {
                "id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
                "name": "Cơ sở dữ liệu",
                "description": "Mô hình quan hệ, SQL, chuẩn hóa dữ liệu",
                "document_count": 2,
                "quiz_count": 1,
                "created_at": "2026-11-08T09:00:00Z"
              }
            ]
        """.trimIndent()
        val courseListType = object : TypeToken<List<CourseResponse>>() {}.type
        val courses: List<CourseResponse> = gson.fromJson(coursesJson, courseListType)
        assertEquals(1, courses.size)
        assertEquals(2, courses[0].documentCount)
        assertEquals(1, courses[0].quizCount)

        val docJson = """
            {
              "id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
              "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
              "title": "Giao_trinh_CSDL_Chuong1.pdf",
              "file_size": 1048576,
              "mime_type": "application/pdf",
              "status": "processing",
              "error_message": null,
              "created_at": "2026-11-08T09:10:00Z"
            }
        """.trimIndent()
        val doc = gson.fromJson(docJson, DocumentResponse::class.java)
        assertEquals("processing", doc.status)
        assertNull(doc.errorMessage)
        assertEquals(1048576L, doc.fileSize)

        val statusJson = """{"id":"d3a2e104-5555-4666-8777-1a2b3c4d5e6f","status":"ready","error_message":null}"""
        val docStatus = gson.fromJson(statusJson, DocumentStatusResponse::class.java)
        assertEquals("ready", docStatus.status)
    }

    @Test
    fun parseAskResponseWithAndWithoutContext() {
        val groundedJson = """
            {
              "answer": "Khóa chính dùng để phân biệt duy nhất mỗi bộ.",
              "found_in_context": true,
              "citations": [
                {
                  "document_id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
                  "document_title": "Giao_trinh_CSDL_Chuong1.pdf",
                  "page_number": 12,
                  "chunk_id": "a9c8b765-2222-4333-8444-010203040506",
                  "snippet": "...Khóa chính là thuộc tính duy nhất..."
                }
              ]
            }
        """.trimIndent()
        val grounded = gson.fromJson(groundedJson, AskResponse::class.java)
        assertTrue(grounded.foundInContext)
        assertEquals(1, grounded.citations.size)
        assertEquals(12, grounded.citations[0].pageNumber)

        val missingJson = """
            {
              "answer": "Nội dung này chưa được tìm thấy trong tài liệu đã nạp của môn học.",
              "found_in_context": false,
              "citations": []
            }
        """.trimIndent()
        val missing = gson.fromJson(missingJson, AskResponse::class.java)
        assertFalse(missing.foundInContext)
        assertTrue(missing.citations.isEmpty())
    }

    @Test
    fun parseQuizAndSubmitResponse() {
        val quizJson = """
            {
              "id": "q9f8e7d6-3333-4444-8555-666677778888",
              "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
              "title": "Quiz: Khóa chính và Ràng buộc toàn vẹn",
              "total_questions": 3,
              "questions": [
                {
                  "id": "e1111111-1111-4111-8111-111111111111",
                  "prompt": "Thuộc tính khóa chính có thể nhận giá trị NULL không?",
                  "topic": "Ràng buộc toàn vẹn",
                  "options": [
                    { "id": "A", "text": "Có thể nhận NULL" },
                    { "id": "B", "text": "Tuyệt đối không được phép nhận NULL" }
                  ]
                }
              ],
              "created_at": "2026-11-08T09:20:00Z"
            }
        """.trimIndent()
        val quiz = gson.fromJson(quizJson, QuizResponse::class.java)
        assertEquals(3, quiz.totalQuestions)
        assertEquals("B", quiz.questions[0].options[1].id)

        val submitReq = QuizSubmitRequest(
            answers = listOf(
                AnswerSubmission("e111", "B"),
                AnswerSubmission("e333", null)
            )
        )
        val reqJson = gson.toJson(submitReq)
        assertTrue(reqJson.contains("e111"))
        assertTrue(reqJson.contains("e333"))

        val submitResJson = """
            {
              "attempt_id": "att-88888888-9999-4aaa-bbbb-ccccdddd0001",
              "quiz_id": "q9f8e7d6-3333-4444-8555-666677778888",
              "score": 3.3,
              "total_questions": 3,
              "correct_count": 1,
              "incorrect_count": 2,
              "submitted_at": "2026-11-08T09:25:00Z",
              "topic_stats": [
                { "topic": "Ràng buộc toàn vẹn", "total_questions": 1, "correct_count": 1, "accuracy": 1.0 },
                { "topic": "Khóa chính", "total_questions": 1, "correct_count": 0, "accuracy": 0.0 }
              ],
              "study_advice": "Bạn vừa nhầm ở Khóa chính và Ràng buộc tham chiếu. Xem lại Giao_trinh_CSDL_Chuong1.pdf (Trang 20).",
              "details": [
                {
                  "question_id": "e3333333-3333-4333-8333-333333333333",
                  "prompt": "Khóa ngoại dùng để làm gì?",
                  "selected_option_id": null,
                  "correct_option_id": "B",
                  "is_correct": false,
                  "explanation": "Khóa ngoại đảm bảo toàn vẹn tham chiếu.",
                  "citation": { "document_title": "Giao_trinh_CSDL_Chuong1.pdf", "page_number": 20 }
                }
              ]
            }
        """.trimIndent()
        val submitRes = gson.fromJson(submitResJson, QuizSubmitResponse::class.java)
        assertEquals(3.3, submitRes.score, 0.001)
        assertEquals(1, submitRes.correctCount)
        assertNotNull(submitRes.studyAdvice)
        assertTrue(submitRes.studyAdvice!!.contains("Trang 20"))
        assertNull(submitRes.details[0].selectedOptionId)
        assertEquals("B", submitRes.details[0].correctOptionId)
        assertNotNull(submitRes.details[0].citation)
    }

    @Test
    fun parseProgressSummaryAndRecommendations() {
        val summaryJson = """
            {
              "total_courses": 4,
              "total_quizzes_completed": 12,
              "average_score": 7.5,
              "recent_activity": {
                "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
                "course_name": "Cơ sở dữ liệu",
                "last_quiz_title": "Quiz: Khóa chính",
                "last_score": 8.0,
                "last_active_at": "2026-11-08T09:25:00Z"
              }
            }
        """.trimIndent()
        val summary = gson.fromJson(summaryJson, ProgressSummaryResponse::class.java)
        assertEquals(4, summary.totalCourses)
        assertEquals(7.5, summary.averageScore, 0.001)
        assertNotNull(summary.recentActivity)

        val recJson = """
            [
              {
                "topic": "Phép JOIN trong quan hệ",
                "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
                "course_name": "Cơ sở dữ liệu",
                "total_answered": 6,
                "correct_count": 2,
                "accuracy": 0.33,
                "recommended_action": "Ôn lại phép nối",
                "suggested_document": {
                  "id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
                  "title": "Giao_trinh_CSDL_Chuong2_JOIN.pdf"
                }
              }
            ]
        """.trimIndent()
        val recType = object : TypeToken<List<RecommendationItem>>() {}.type
        val recs: List<RecommendationItem> = gson.fromJson(recJson, recType)
        assertEquals(1, recs.size)
        assertEquals(0.33, recs[0].accuracy, 0.001)
        assertEquals("Giao_trinh_CSDL_Chuong2_JOIN.pdf", recs[0].suggestedDocument?.title)
    }

    @Test
    fun serializeRequestsAndParseErrorFallbacks() {
        val courseReq = CourseCreateRequest(name = "Hệ điều hành", description = "Tiến trình và bộ nhớ")
        val courseReqJson = gson.toJson(courseReq)
        assertTrue(courseReqJson.contains("Hệ điều hành"))

        val askReq = AskRequest(
            courseId = "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
            documentId = "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
            question = "Khóa chính là gì?"
        )
        val askReqJson = gson.toJson(askReq)
        assertTrue(askReqJson.contains("\"course_id\""))
        assertTrue(askReqJson.contains("\"document_id\""))
        assertTrue(askReqJson.contains("\"question\""))

        val askWithHistoryReq = AskRequest(
            courseId = "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
            documentId = null,
            question = "Cho ví dụ cụ thể hơn",
            history = listOf(
                ChatHistoryItem(role = "user", content = "Khóa chính là gì?"),
                ChatHistoryItem(role = "assistant", content = "Khóa chính dùng để định danh duy nhất mỗi bộ.")
            )
        )
        val askWithHistoryJson = gson.toJson(askWithHistoryReq)
        assertTrue(askWithHistoryJson.contains("\"history\""))
        assertTrue(askWithHistoryJson.contains("\"role\":\"user\""))
        assertTrue(askWithHistoryJson.contains("\"role\":\"assistant\""))

        val quizReq = QuizCreateRequest(
            courseId = "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
            documentId = null,
            topic = "SQL",
            questionCount = 5
        )
        val quizReqJson = gson.toJson(quizReq)
        assertTrue(quizReqJson.contains("\"course_id\""))
        assertTrue(quizReqJson.contains("\"question_count\":5"))

        val fallbackNull = RetrofitClient.parseError(null)
        assertTrue(fallbackNull.isNotBlank())
        val fallbackInvalid = RetrofitClient.parseError("Internal Server Error 500")
        assertTrue(fallbackInvalid.isNotBlank())
    }

    @Test
    fun parseCourseQuizSummaries() {
        val summariesJson = """
            [
              {
                "id": "q9f8e7d6-3333-4444-8555-666677778888",
                "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
                "title": "Quiz: Quy trình kiểm thử",
                "total_questions": 5,
                "attempt_count": 0,
                "latest_score": null,
                "created_at": "2026-11-10T08:00:00Z"
              },
              {
                "id": "q2222222-3333-4444-8555-666677778888",
                "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
                "title": "Quiz: Test Case & Kỹ thuật hộp đen",
                "total_questions": 10,
                "attempt_count": 2,
                "latest_score": 8.5,
                "created_at": "2026-11-10T08:30:00Z"
              }
            ]
        """.trimIndent()
        val listType = object : TypeToken<List<QuizSummaryResponse>>() {}.type
        val summaries: List<QuizSummaryResponse> = gson.fromJson(summariesJson, listType)
        assertEquals(2, summaries.size)
        assertEquals(5, summaries[0].totalQuestions)
        assertEquals(0, summaries[0].attemptCount)
        assertNull(summaries[0].latestScore)
        assertEquals(10, summaries[1].totalQuestions)
        assertEquals(2, summaries[1].attemptCount)
        assertNotNull(summaries[1].latestScore)
        assertEquals(8.5, summaries[1].latestScore!!, 0.001)
    }
}
