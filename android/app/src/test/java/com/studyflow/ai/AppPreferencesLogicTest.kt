package com.studyflow.ai

import com.studyflow.ai.data.local.AppThemeMode
import com.studyflow.ai.data.local.StudentProfile
import com.studyflow.ai.data.local.extractProfileInitials
import com.studyflow.ai.data.local.sanitizeStudentProfile
import com.studyflow.ai.ui.theme.extractCourseMonogram
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesLogicTest {

    @Test
    fun themeModeRoundTripAndFallback() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey("unknown"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey("system"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStorageKey("light"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStorageKey("dark"))
    }

    @Test
    fun extractProfileInitialsHandlesVietnameseAndEdgeCases() {
        assertEquals("SF", extractProfileInitials(""))
        assertEquals("SF", extractProfileInitials("   "))
        assertEquals("NA", extractProfileInitials("Nguyễn Minh Anh"))
        assertEquals("MA", extractProfileInitials("  minh   anh "))
        assertEquals("KH", extractProfileInitials("Khoa"))
        assertEquals("A", extractProfileInitials("A"))
    }

    @Test
    fun sanitizeStudentProfileClampsAndFallsBack() {
        val defaults = sanitizeStudentProfile("   ", "", 0)
        assertEquals(StudentProfile.DEFAULT_DISPLAY_NAME, defaults.displayName)
        assertEquals(StudentProfile.DEFAULT_STUDY_GOAL, defaults.studyGoal)
        assertEquals(StudentProfile.MIN_DAILY_MINUTES, defaults.dailyTargetMinutes)

        val capped = sanitizeStudentProfile(
            displayName = "  " + "A".repeat(60),
            studyGoal = "  " + "B".repeat(120),
            dailyTargetMinutes = 999
        )
        assertEquals(40, capped.displayName.length)
        assertEquals(80, capped.studyGoal.length)
        assertEquals(StudentProfile.MAX_DAILY_MINUTES, capped.dailyTargetMinutes)
    }

    @Test
    fun extractCourseMonogramFormatsCleanly() {
        assertEquals("SF", extractCourseMonogram(""))
        assertEquals("CS", extractCourseMonogram("Cơ sở dữ liệu"))
        assertEquals("KT", extractCourseMonogram("KTPM"))
        assertEquals("C2", extractCourseMonogram("CS2"))
    }
}

