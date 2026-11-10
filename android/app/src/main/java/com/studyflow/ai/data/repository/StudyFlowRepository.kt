package com.studyflow.ai.data.repository

import com.studyflow.ai.BuildConfig
import com.studyflow.ai.data.api.RetrofitClient
import com.studyflow.ai.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException

class StudyFlowRepository {
    private val api = RetrofitClient.apiService

    private fun extractError(e: Exception): String {
        return when (e) {
            is HttpException -> RetrofitClient.parseError(e.response()?.errorBody()?.string())
            is ConnectException, is SocketTimeoutException ->
                "Không thể kết nối tới Backend (${BuildConfig.API_BASE_URL}). Hãy đảm bảo Docker Postgres và uvicorn đang chạy."
            else -> e.localizedMessage ?: "Không thể kết nối đến máy chủ."
        }
    }

    suspend fun getProgressSummary(): Result<ProgressSummaryResponse> = try {
        Result.success(api.getProgressSummary())
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun getCourses(): Result<List<CourseResponse>> = try {
        Result.success(api.getCourses())
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun createCourse(name: String, description: String?): Result<CourseResponse> = try {
        Result.success(api.createCourse(CourseCreateRequest(name, description)))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun getCourse(courseId: String): Result<CourseResponse> = try {
        Result.success(api.getCourse(courseId))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun updateCourse(courseId: String, name: String, description: String?): Result<CourseResponse> = try {
        Result.success(api.updateCourse(courseId, CourseCreateRequest(name, description)))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun deleteCourse(courseId: String): Result<Unit> = try {
        val res = api.deleteCourse(courseId)
        if (res.isSuccessful) Result.success(Unit)
        else Result.failure(Exception("Không thể xóa môn học."))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun getCourseDocuments(courseId: String): Result<List<DocumentResponse>> = try {
        Result.success(api.getCourseDocuments(courseId))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun getDocumentStatus(documentId: String): Result<DocumentStatusResponse> = try {
        Result.success(api.getDocumentStatus(documentId))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun uploadDocument(
        courseId: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream
    ): Result<DocumentResponse> = try {
        val bytes = inputStream.readBytes()
        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
        Result.success(api.uploadDocument(courseId, part))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun deleteDocument(documentId: String): Result<Unit> = try {
        val res = api.deleteDocument(documentId)
        if (res.isSuccessful) Result.success(Unit)
        else Result.failure(Exception("Không thể xóa tài liệu."))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun askAssistant(courseId: String, documentId: String?, question: String): Result<AskResponse> = try {
        Result.success(api.askAssistant(AskRequest(courseId, documentId, question)))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun createQuiz(courseId: String, documentId: String?, topic: String?, count: Int): Result<QuizResponse> = try {
        Result.success(api.createQuiz(QuizCreateRequest(courseId, documentId, topic, count)))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun getQuiz(quizId: String): Result<QuizResponse> = try {
        Result.success(api.getQuiz(quizId))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun submitQuiz(quizId: String, answers: List<AnswerSubmission>): Result<QuizSubmitResponse> = try {
        Result.success(api.submitQuiz(quizId, QuizSubmitRequest(answers)))
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }

    suspend fun getRecommendations(): Result<List<RecommendationItem>> = try {
        Result.success(api.getRecommendations())
    } catch (e: Exception) {
        Result.failure(Exception(extractError(e)))
    }
}

