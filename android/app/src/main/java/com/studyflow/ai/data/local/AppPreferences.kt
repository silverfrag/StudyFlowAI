package com.studyflow.ai.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppThemeMode(val storageKey: String, val label: String) {
    SYSTEM("system", "Hệ thống"),
    LIGHT("light", "Sáng"),
    DARK("dark", "Tối");

    companion object {
        fun fromStorageKey(key: String?): AppThemeMode {
            return entries.firstOrNull { it.storageKey == key } ?: SYSTEM
        }
    }
}

data class StudentProfile(
    val displayName: String = DEFAULT_DISPLAY_NAME,
    val studyGoal: String = DEFAULT_STUDY_GOAL,
    val dailyTargetMinutes: Int = DEFAULT_DAILY_MINUTES
) {
    companion object {
        const val DEFAULT_DISPLAY_NAME = "Học viên StudyFlow"
        const val DEFAULT_STUDY_GOAL = "Ôn tập & Luyện thi cùng AI"
        const val DEFAULT_DAILY_MINUTES = 45
        const val MIN_DAILY_MINUTES = 15
        const val MAX_DAILY_MINUTES = 360
    }
}

fun extractProfileInitials(displayName: String): String {
    val words = displayName.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return "SF"
    val locale = Locale.forLanguageTag("vi-VN")
    return if (words.size >= 2) {
        val first = words.first().first()
        val last = words.last().first()
        "$first$last".uppercase(locale)
    } else {
        val word = words.first()
        if (word.length >= 2) {
            word.take(2).uppercase(locale)
        } else {
            word.uppercase(locale)
        }
    }
}

fun sanitizeStudentProfile(
    displayName: String,
    studyGoal: String,
    dailyTargetMinutes: Int
): StudentProfile {
    val cleanName = displayName.trim().ifBlank { StudentProfile.DEFAULT_DISPLAY_NAME }.take(40)
    val cleanGoal = studyGoal.trim().ifBlank { StudentProfile.DEFAULT_STUDY_GOAL }.take(80)
    val cleanMinutes = dailyTargetMinutes.coerceIn(
        StudentProfile.MIN_DAILY_MINUTES,
        StudentProfile.MAX_DAILY_MINUTES
    )
    return StudentProfile(
        displayName = cleanName,
        studyGoal = cleanGoal,
        dailyTargetMinutes = cleanMinutes
    )
}

class AppPreferences private constructor(
    private val prefs: SharedPreferences
) {
    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<StudentProfile> = _profile.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.storageKey).apply()
        _themeMode.value = mode
    }

    fun toggleLightDark(currentlyDark: Boolean) {
        setThemeMode(if (currentlyDark) AppThemeMode.LIGHT else AppThemeMode.DARK)
    }

    fun updateProfile(
        displayName: String,
        studyGoal: String,
        dailyTargetMinutes: Int
    ) {
        val sanitized = sanitizeStudentProfile(displayName, studyGoal, dailyTargetMinutes)
        prefs.edit()
            .putString(KEY_PROFILE_NAME, sanitized.displayName)
            .putString(KEY_PROFILE_GOAL, sanitized.studyGoal)
            .putInt(KEY_PROFILE_DAILY_MINUTES, sanitized.dailyTargetMinutes)
            .apply()
        _profile.value = sanitized
    }

    private fun readThemeMode(): AppThemeMode {
        return AppThemeMode.fromStorageKey(prefs.getString(KEY_THEME_MODE, null))
    }

    private fun readProfile(): StudentProfile {
        val name = prefs.getString(KEY_PROFILE_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: StudentProfile.DEFAULT_DISPLAY_NAME
        val goal = prefs.getString(KEY_PROFILE_GOAL, null)
            ?.takeIf { it.isNotBlank() }
            ?: StudentProfile.DEFAULT_STUDY_GOAL
        val minutes = prefs.getInt(KEY_PROFILE_DAILY_MINUTES, StudentProfile.DEFAULT_DAILY_MINUTES)
        return sanitizeStudentProfile(name, goal, minutes)
    }

    companion object {
        private const val PREFS_NAME = "studyflow_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PROFILE_NAME = "profile_display_name"
        private const val KEY_PROFILE_GOAL = "profile_study_goal"
        private const val KEY_PROFILE_DAILY_MINUTES = "profile_daily_minutes"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}

