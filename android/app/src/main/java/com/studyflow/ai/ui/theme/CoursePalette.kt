package com.studyflow.ai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.abs

@Immutable
data class CourseVisualStyle(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val gradientEnd: Color,
    val bentoSurface: Color,
    val bentoBorder: Color,
    val tactileLip: Color,
    val pillSurface: Color,
    val onPillSurface: Color
)

private val LightCoursePalettes = listOf(
    // Indigo / Lavender Deck
    CourseVisualStyle(
        accent = Color(0xFF5B52F0),
        container = Color(0xFFE9E7FF),
        onContainer = Color(0xFF2E2978),
        gradientEnd = Color(0xFF7B74FF),
        bentoSurface = Color(0xFFE9E7FF),
        bentoBorder = Color(0xFFD2CEFC),
        tactileLip = Color(0xFFC7C2FA),
        pillSurface = Color(0xFFFFFFFF),
        onPillSurface = Color(0xFF14162E)
    ),
    // Emerald / Mint Deck
    CourseVisualStyle(
        accent = Color(0xFF059669),
        container = Color(0xFFDDFCEB),
        onContainer = Color(0xFF065F46),
        gradientEnd = Color(0xFF10B981),
        bentoSurface = Color(0xFFDDFCEB),
        bentoBorder = Color(0xFFBCEFD3),
        tactileLip = Color(0xFF9FE7C4),
        pillSurface = Color(0xFFFFFFFF),
        onPillSurface = Color(0xFF14162E)
    ),
    // Amber / Buttercream Deck
    CourseVisualStyle(
        accent = Color(0xFFD97706),
        container = Color(0xFFFEF2C7),
        onContainer = Color(0xFF78350F),
        gradientEnd = Color(0xFFF59E0B),
        bentoSurface = Color(0xFFFEF2C7),
        bentoBorder = Color(0xFFFAE29C),
        tactileLip = Color(0xFFF5CF74),
        pillSurface = Color(0xFFFFFFFF),
        onPillSurface = Color(0xFF14162E)
    ),
    // Sky / Powder Blue Deck
    CourseVisualStyle(
        accent = Color(0xFF0284C7),
        container = Color(0xFFDFF5FE),
        onContainer = Color(0xFF0C4A6E),
        gradientEnd = Color(0xFF38BDF8),
        bentoSurface = Color(0xFFDFF5FE),
        bentoBorder = Color(0xFFBCE6FB),
        tactileLip = Color(0xFF9FD8F5),
        pillSurface = Color(0xFFFFFFFF),
        onPillSurface = Color(0xFF14162E)
    ),
    // Violet / Orchid Deck
    CourseVisualStyle(
        accent = Color(0xFF7C3AED),
        container = Color(0xFFF2E8FF),
        onContainer = Color(0xFF4C1D95),
        gradientEnd = Color(0xFFA78BFA),
        bentoSurface = Color(0xFFF2E8FF),
        bentoBorder = Color(0xFFE0CDFC),
        tactileLip = Color(0xFFD2B4FA),
        pillSurface = Color(0xFFFFFFFF),
        onPillSurface = Color(0xFF14162E)
    ),
    // Rose / Blush Coral Deck
    CourseVisualStyle(
        accent = Color(0xFFE11D48),
        container = Color(0xFFFFE6E4),
        onContainer = Color(0xFF881337),
        gradientEnd = Color(0xFFF43F5E),
        bentoSurface = Color(0xFFFFE6E4),
        bentoBorder = Color(0xFFFCCDC9),
        tactileLip = Color(0xFFF6B8B3),
        pillSurface = Color(0xFFFFFFFF),
        onPillSurface = Color(0xFF14162E)
    )
)

private val DarkCoursePalettes = listOf(
    CourseVisualStyle(
        accent = Color(0xFF6B63FF),
        container = Color(0xFF252459),
        onContainer = Color(0xFFE9E7FF),
        gradientEnd = Color(0xFF8B84FF),
        bentoSurface = Color(0xFF1C2042),
        bentoBorder = Color(0xFF333A73),
        tactileLip = Color(0xFF0F122B),
        pillSurface = Color(0xFF12162E),
        onPillSurface = Color(0xFFE9E7FF)
    ),
    CourseVisualStyle(
        accent = Color(0xFF10B981),
        container = Color(0xFF0E3329),
        onContainer = Color(0xFFDDFCEB),
        gradientEnd = Color(0xFF34D399),
        bentoSurface = Color(0xFF112C25),
        bentoBorder = Color(0xFF1F5749),
        tactileLip = Color(0xFF081713),
        pillSurface = Color(0xFF0B1F1A),
        onPillSurface = Color(0xFFDDFCEB)
    ),
    CourseVisualStyle(
        accent = Color(0xFFF59E0B),
        container = Color(0xFF3A2910),
        onContainer = Color(0xFFFEF2C7),
        gradientEnd = Color(0xFFFBBF24),
        bentoSurface = Color(0xFF2B2111),
        bentoBorder = Color(0xFF5A421C),
        tactileLip = Color(0xFF171107),
        pillSurface = Color(0xFF1C160B),
        onPillSurface = Color(0xFFFEF2C7)
    ),
    CourseVisualStyle(
        accent = Color(0xFF0EA5E9),
        container = Color(0xFF112F47),
        onContainer = Color(0xFFDFF5FE),
        gradientEnd = Color(0xFF38BDF8),
        bentoSurface = Color(0xFF12273D),
        bentoBorder = Color(0xFF1E4970),
        tactileLip = Color(0xFF091522),
        pillSurface = Color(0xFF0C1A29),
        onPillSurface = Color(0xFFDFF5FE)
    ),
    CourseVisualStyle(
        accent = Color(0xFF8B5CF6),
        container = Color(0xFF2E1E59),
        onContainer = Color(0xFFF2E8FF),
        gradientEnd = Color(0xFFA78BFA),
        bentoSurface = Color(0xFF231A45),
        bentoBorder = Color(0xFF42317D),
        tactileLip = Color(0xFF120D29),
        pillSurface = Color(0xFF181131),
        onPillSurface = Color(0xFFF2E8FF)
    ),
    CourseVisualStyle(
        accent = Color(0xFFF43F5E),
        container = Color(0xFF401624),
        onContainer = Color(0xFFFFE6E4),
        gradientEnd = Color(0xFFFB7185),
        bentoSurface = Color(0xFF301621),
        bentoBorder = Color(0xFF5E293F),
        tactileLip = Color(0xFF1A0A11),
        pillSurface = Color(0xFF210E16),
        onPillSurface = Color(0xFFFFE6E4)
    )
)

@Composable
fun rememberCourseVisualStyle(
    seed: String,
    darkTheme: Boolean = LocalStudyFlowDarkTheme.current
): CourseVisualStyle {
    val palettes = if (darkTheme) DarkCoursePalettes else LightCoursePalettes
    val hash = seed.fold(0) { acc, ch -> (acc * 31) + ch.code }
    val index = abs(hash) % palettes.size
    return palettes[index]
}

fun extractCourseMonogram(courseName: String): String {
    val words = courseName.trim()
        .split(Regex("[\\s\\-_/+]+"))
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return "SF"
    val locale = Locale.forLanguageTag("vi-VN")
    return if (words.size >= 2) {
        val first = words[0].first()
        val second = words[1].first()
        "$first$second".uppercase(locale)
    } else {
        val word = words[0]
        if (word.length >= 2) {
            val lastChar = word.last()
            if (lastChar.isDigit()) {
                "${word.first()}$lastChar".uppercase(locale)
            } else {
                word.take(2).uppercase(locale)
            }
        } else {
            word.uppercase(locale)
        }
    }
}
