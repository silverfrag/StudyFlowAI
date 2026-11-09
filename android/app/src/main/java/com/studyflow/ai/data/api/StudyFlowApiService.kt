package com.studyflow.ai.data.api

import com.google.gson.Gson
import com.studyflow.ai.BuildConfig
import com.studyflow.ai.data.model.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface StudyFlowApiService {

    @GET("health")
    suspend fun getHealth(): HealthResponse

    // --- Courses (UC01) ---
    @GET("courses")
    suspend fun getCourses(): List<CourseResponse>

    @POST("courses")
    suspend fun createCourse(@Body request: CourseCreateRequest): CourseResponse

    @GET("courses/{courseId}")
    suspend fun getCourse(@Path("courseId") courseId: String): CourseResponse

    @PATCH("courses/{courseId}")
    suspend fun updateCourse(
        @Path("courseId") courseId: String,
        @Body request: CourseCreateRequest
    ): CourseResponse

    @DELETE("courses/{courseId}")
    suspend fun deleteCourse(@Path("courseId") courseId: String): Response<Unit>

    // --- Documents (UC02) ---
    @GET("courses/{courseId}/documents")
    suspend fun getCourseDocuments(@Path("courseId") courseId: String): List<DocumentResponse>

    @Multipart
    @POST("courses/{courseId}/documents")
    suspend fun uploadDocument(
        @Path("courseId") courseId: String,
        @Part file: MultipartBody.Part
    ): DocumentResponse

    @DELETE("documents/{documentId}")
    suspend fun deleteDocument(@Path("documentId") documentId: String): Response<Unit>

    // --- Assistant RAG (UC03) ---
    @POST("assistant/ask")
    suspend fun askAssistant(@Body request: AskRequest): AskResponse

    // --- Quizzes (UC04, UC05, UC06) ---
    @POST("quizzes")
    suspend fun createQuiz(@Body request: QuizCreateRequest): QuizResponse

    @GET("quizzes/{quizId}")
    suspend fun getQuiz(@Path("quizId") quizId: String): QuizResponse

    @POST("quizzes/{quizId}/submit")
    suspend fun submitQuiz(
        @Path("quizId") quizId: String,
        @Body request: QuizSubmitRequest
    ): QuizSubmitResponse

    // --- Progress & Recommendations (UC06, UC07) ---
    @GET("progress/summary")
    suspend fun getProgressSummary(): ProgressSummaryResponse

    @GET("recommendations")
    suspend fun getRecommendations(): List<RecommendationItem>
}

object RetrofitClient {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: StudyFlowApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StudyFlowApiService::class.java)
    }

    fun parseError(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Đã xảy ra lỗi kết nối."
        return try {
            val envelope = Gson().fromJson(errorBody, StandardErrorEnvelope::class.java)
            envelope.detail.message
        } catch (e: Exception) {
            "Lỗi hệ thống. Vui lòng thử lại sau."
        }
    }
}

