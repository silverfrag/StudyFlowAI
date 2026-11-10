package com.studyflow.ai.data.model

import com.google.gson.annotations.SerializedName

// --- Error Handling ---
data class ValidationErrorItem(
    val field: String? = null,
    val message: String
)

data class ErrorDetail(
    val code: String,
    val message: String,
    val field: String? = null,
    val errors: List<ValidationErrorItem>? = null
)

data class StandardErrorEnvelope(
    val detail: ErrorDetail
)

// --- Health ---
data class HealthResponse(
    val status: String,
    val database: String,
    val version: String
)

// --- Courses (UC01) ---
data class CourseResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerializedName("document_count") val documentCount: Int = 0,
    @SerializedName("quiz_count") val quizCount: Int = 0,
    @SerializedName("created_at") val createdAt: String
)

data class CourseCreateRequest(
    val name: String,
    val description: String? = null
)

// --- Documents (UC02) ---
data class DocumentResponse(
    val id: String,
    @SerializedName("course_id") val courseId: String,
    val title: String,
    @SerializedName("file_size") val fileSize: Long,
    @SerializedName("mime_type") val mimeType: String,
    val status: String, // "processing", "ready", "error"
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("created_at") val createdAt: String
)

data class DocumentStatusResponse(
    val id: String,
    val status: String,
    @SerializedName("error_message") val errorMessage: String? = null
)

// --- Assistant RAG (UC03) ---
data class AskRequest(
    @SerializedName("course_id") val courseId: String,
    @SerializedName("document_id") val documentId: String? = null,
    val question: String
)

data class Citation(
    @SerializedName("document_id") val documentId: String,
    @SerializedName("document_title") val documentTitle: String,
    @SerializedName("page_number") val pageNumber: Int? = null,
    @SerializedName("chunk_id") val chunkId: String,
    val snippet: String
)

data class AskResponse(
    val answer: String,
    @SerializedName("found_in_context") val foundInContext: Boolean,
    val citations: List<Citation> = emptyList()
)

// --- Quizzes (UC04, UC05, UC06) ---
data class QuizCreateRequest(
    @SerializedName("course_id") val courseId: String,
    @SerializedName("document_id") val documentId: String? = null,
    val topic: String? = null,
    @SerializedName("question_count") val questionCount: Int = 5
)

data class QuizOption(
    val id: String, // "A", "B", "C", "D"
    val text: String
)

data class QuizQuestion(
    val id: String,
    val prompt: String,
    val topic: String,
    val options: List<QuizOption>
)

data class QuizResponse(
    val id: String,
    @SerializedName("course_id") val courseId: String,
    val title: String,
    @SerializedName("total_questions") val totalQuestions: Int,
    val questions: List<QuizQuestion>,
    @SerializedName("created_at") val createdAt: String
)

data class QuizSummaryResponse(
    val id: String,
    @SerializedName("course_id") val courseId: String,
    val title: String,
    @SerializedName("total_questions") val totalQuestions: Int,
    @SerializedName("attempt_count") val attemptCount: Int = 0,
    @SerializedName("latest_score") val latestScore: Double? = null,
    @SerializedName("created_at") val createdAt: String
)

data class AnswerSubmission(
    @SerializedName("question_id") val questionId: String,
    @SerializedName("selected_option_id") val selectedOptionId: String?
)

data class QuizSubmitRequest(
    val answers: List<AnswerSubmission>
)

data class TopicStat(
    val topic: String,
    @SerializedName("total_questions") val totalQuestions: Int,
    @SerializedName("correct_count") val correctCount: Int,
    val accuracy: Double
)

data class SourceCitation(
    @SerializedName("document_title") val documentTitle: String,
    @SerializedName("page_number") val pageNumber: Int? = null
)

data class QuestionResultDetail(
    @SerializedName("question_id") val questionId: String,
    val prompt: String,
    @SerializedName("selected_option_id") val selectedOptionId: String?,
    @SerializedName("correct_option_id") val correctOptionId: String,
    @SerializedName("is_correct") val isCorrect: Boolean,
    val explanation: String,
    val citation: SourceCitation? = null
)

data class QuizSubmitResponse(
    @SerializedName("attempt_id") val attemptId: String,
    @SerializedName("quiz_id") val quizId: String,
    val score: Double,
    @SerializedName("total_questions") val totalQuestions: Int,
    @SerializedName("correct_count") val correctCount: Int,
    @SerializedName("incorrect_count") val incorrectCount: Int,
    @SerializedName("submitted_at") val submittedAt: String,
    @SerializedName("topic_stats") val topicStats: List<TopicStat>,
    val details: List<QuestionResultDetail>
)

// --- Progress & Recommendations (UC06, UC07) ---
data class RecentActivity(
    @SerializedName("course_id") val courseId: String,
    @SerializedName("course_name") val courseName: String,
    @SerializedName("last_quiz_title") val lastQuizTitle: String,
    @SerializedName("last_score") val lastScore: Double,
    @SerializedName("last_active_at") val lastActiveAt: String
)

data class ProgressSummaryResponse(
    @SerializedName("total_courses") val totalCourses: Int,
    @SerializedName("total_quizzes_completed") val totalQuizzesCompleted: Int,
    @SerializedName("average_score") val averageScore: Double,
    @SerializedName("recent_activity") val recentActivity: RecentActivity? = null
)

data class SuggestedDocument(
    val id: String,
    val title: String
)

data class RecommendationItem(
    val topic: String,
    @SerializedName("course_id") val courseId: String,
    @SerializedName("course_name") val courseName: String,
    @SerializedName("total_answered") val totalAnswered: Int,
    @SerializedName("correct_count") val correctCount: Int,
    val accuracy: Double,
    @SerializedName("recommended_action") val recommendedAction: String,
    @SerializedName("suggested_document") val suggestedDocument: SuggestedDocument? = null
)

