package com.studyflow.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Indigo & Neutral Colors theo docs/ui-wireframes.md
val IndigoPrimary = Color(0xFF4F46E5)
val IndigoPrimaryLight = Color(0xFFEEF2FF)
val IndigoSecondary = Color(0xFF6366F1)
val NeutralBackground = Color(0xFFF8FAFC)
val NeutralSurface = Color(0xFFFFFFFF)
val NeutralTextPrimary = Color(0xFF0F172A)
val NeutralTextSecondary = Color(0xFF64748B)
val SuccessGreen = Color(0xFF16A34A)
val SuccessGreenBg = Color(0xFFDCFCE7)
val WarningAmber = Color(0xFFD97706)
val WarningAmberBg = Color(0xFFFEF3C7)
val ErrorRed = Color(0xFFDC2626)
val ErrorRedBg = Color(0xFFFEE2E2)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoPrimaryLight,
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = IndigoSecondary,
    onSecondary = Color.White,
    background = NeutralBackground,
    onBackground = NeutralTextPrimary,
    surface = NeutralSurface,
    onSurface = NeutralTextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = NeutralTextSecondary,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFA5B4FC),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D)
)

@Composable
fun StudyFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

