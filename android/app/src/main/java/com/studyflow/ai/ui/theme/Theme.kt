package com.studyflow.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// UI Kit v2 — Pinterest Pastel Bento + Tactile 3D Depth
val IndigoPrimary = Color(0xFF5B52F0)
val IndigoPrimaryLip = Color(0xFF3F36C9)
val IndigoPrimaryLight = Color(0xFFE9E7FF)
val IndigoSecondary = Color(0xFF7C3AED)
val TealTertiary = Color(0xFF0D9488)
val WarmIvoryBackground = Color(0xFFF7F6F2)
val MidnightInk = Color(0xFF14162E)
val NeutralSurface = Color(0xFFFFFFFF)
val NeutralTextPrimary = MidnightInk
val NeutralTextSecondary = Color(0xFF5D637A)
val SuccessGreen = Color(0xFF059669)
val SuccessGreenBg = Color(0xFFDDFCEB)
val WarningAmber = Color(0xFFD97706)
val WarningAmberBg = Color(0xFFFEF2C7)
val ErrorRed = Color(0xFFE11D48)
val ErrorRedBg = Color(0xFFFFE6E4)

@Immutable
data class StudyFlowSemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val cardBorder: Color,
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    // v2 Bento + Tactile 3D Tokens
    val midnightInk: Color,
    val midnightInkLip: Color,
    val midnightOnInk: Color,
    val midnightSubtle: Color,
    val lavenderBento: Color,
    val lavenderLip: Color,
    val lavenderBorder: Color,
    val lavenderContent: Color,
    val mintBento: Color,
    val mintLip: Color,
    val mintBorder: Color,
    val mintContent: Color,
    val butterBento: Color,
    val butterLip: Color,
    val butterBorder: Color,
    val butterContent: Color,
    val coralBento: Color,
    val coralLip: Color,
    val coralBorder: Color,
    val coralContent: Color,
    val skyBento: Color,
    val skyLip: Color,
    val skyBorder: Color,
    val skyContent: Color,
    val tactileDefaultLip: Color,
    val floatingDockContainer: Color,
    val floatingDockBorder: Color,
    val floatingDockActivePill: Color,
    val primaryLip: Color,
    val selectedPill: Color,
    val selectedPillLip: Color,
    val onSelectedPill: Color
)

private val DarkSemanticColors = StudyFlowSemanticColors(
    success = Color(0xFF34D399),
    successContainer = Color(0xFF103129),
    onSuccessContainer = Color(0xFFBCF7DC),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF332612),
    onWarningContainer = Color(0xFFFEF2C7),
    error = Color(0xFFFB7185),
    errorContainer = Color(0xFF3A1622),
    onErrorContainer = Color(0xFFFFE4E6),
    info = Color(0xFF38BDF8),
    infoContainer = Color(0xFF122C42),
    onInfoContainer = Color(0xFFDFF5FE),
    cardBorder = Color(0xFF2B3656),
    heroGradientStart = Color(0xFF4338CA),
    heroGradientEnd = Color(0xFF6D28D9),
    midnightInk = Color(0xFF151B2E),
    midnightInkLip = Color(0xFF070A14),
    midnightOnInk = Color(0xFFF5F7FF),
    midnightSubtle = Color(0xFFA7B0CF),
    lavenderBento = Color(0xFF1E2246),
    lavenderLip = Color(0xFF10132C),
    lavenderBorder = Color(0xFF353D73),
    lavenderContent = Color(0xFFD9D6FF),
    mintBento = Color(0xFF112D26),
    mintLip = Color(0xFF081914),
    mintBorder = Color(0xFF1E5748),
    mintContent = Color(0xFFB3F8D8),
    butterBento = Color(0xFF2E2312),
    butterLip = Color(0xFF191208),
    butterBorder = Color(0xFF5A421C),
    butterContent = Color(0xFFFDE69B),
    coralBento = Color(0xFF331823),
    coralLip = Color(0xFF1B0B11),
    coralBorder = Color(0xFF612B40),
    coralContent = Color(0xFFFFCDD2),
    skyBento = Color(0xFF122940),
    skyLip = Color(0xFF091624),
    skyBorder = Color(0xFF1F4C75),
    skyContent = Color(0xFFC2EBFF),
    tactileDefaultLip = Color(0xFF0A0E1B),
    floatingDockContainer = Color(0xFF161D33),
    floatingDockBorder = Color(0xFF2E3859),
    floatingDockActivePill = Color(0xFF5B52F0),
    primaryLip = Color(0xFF3A32B8),
    selectedPill = Color(0xFF5B52F0),
    selectedPillLip = Color(0xFF3A32B8),
    onSelectedPill = Color.White
)

private val LightSemanticColors = StudyFlowSemanticColors(
    success = SuccessGreen,
    successContainer = SuccessGreenBg,
    onSuccessContainer = Color(0xFF065F46),
    warning = WarningAmber,
    warningContainer = WarningAmberBg,
    onWarningContainer = Color(0xFF78350F),
    error = ErrorRed,
    errorContainer = ErrorRedBg,
    onErrorContainer = Color(0xFF881337),
    info = Color(0xFF0284C7),
    infoContainer = Color(0xFFDFF5FE),
    onInfoContainer = Color(0xFF0C4A6E),
    cardBorder = Color(0xFFE4E0D4),
    heroGradientStart = Color(0xFF5B52F0),
    heroGradientEnd = Color(0xFF7C3AED),
    midnightInk = MidnightInk,
    midnightInkLip = Color(0xFF080A18),
    midnightOnInk = Color.White,
    midnightSubtle = Color(0xFFC5CBE6),
    lavenderBento = Color(0xFFE9E7FF),
    lavenderLip = Color(0xFFC7C2FA),
    lavenderBorder = Color(0xFFD4D0FC),
    lavenderContent = Color(0xFF2E2978),
    mintBento = Color(0xFFDDFCEB),
    mintLip = Color(0xFF9FE7C4),
    mintBorder = Color(0xFFBDF2D5),
    mintContent = Color(0xFF065F46),
    butterBento = Color(0xFFFEF2C7),
    butterLip = Color(0xFFF5CF74),
    butterBorder = Color(0xFFFAE29C),
    butterContent = Color(0xFF78350F),
    coralBento = Color(0xFFFFE6E4),
    coralLip = Color(0xFFF6B8B3),
    coralBorder = Color(0xFFFCD0CD),
    coralContent = Color(0xFF881337),
    skyBento = Color(0xFFDFF5FE),
    skyLip = Color(0xFF9FD8F5),
    skyBorder = Color(0xFFBCE6FB),
    skyContent = Color(0xFF0C4A6E),
    tactileDefaultLip = Color(0xFFD5D1C3),
    floatingDockContainer = MidnightInk,
    floatingDockBorder = Color(0xFF282D54),
    floatingDockActivePill = IndigoPrimary,
    primaryLip = IndigoPrimaryLip,
    selectedPill = MidnightInk,
    selectedPillLip = Color(0xFF080A18),
    onSelectedPill = Color.White
)

val LocalStudyFlowDarkTheme = staticCompositionLocalOf { false }
val LocalStudyFlowSemanticColors = staticCompositionLocalOf { LightSemanticColors }

@Composable
fun studyFlowSemanticColors(
    darkTheme: Boolean = LocalStudyFlowDarkTheme.current
): StudyFlowSemanticColors {
    return if (darkTheme) DarkSemanticColors else LightSemanticColors
}

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoPrimaryLight,
    onPrimaryContainer = MidnightInk,
    secondary = IndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEAFF),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = TealTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SuccessGreenBg,
    onTertiaryContainer = Color(0xFF065F46),
    background = WarmIvoryBackground,
    onBackground = NeutralTextPrimary,
    surface = NeutralSurface,
    onSurface = NeutralTextPrimary,
    surfaceVariant = Color(0xFFF1EFE8),
    onSurfaceVariant = NeutralTextSecondary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBF8),
    surfaceContainer = Color(0xFFF0EDE4),
    surfaceContainerHigh = Color(0xFFE6E3D8),
    surfaceContainerHighest = Color(0xFFDBD7CA),
    outline = Color(0xFF8E95AC),
    outlineVariant = Color(0xFFE6E3D8),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedBg,
    onErrorContainer = Color(0xFF881337)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7B74FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF252459),
    onPrimaryContainer = Color(0xFFE9E7FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF3E2278),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = Color(0xFF103831),
    onTertiaryContainer = Color(0xFFDDFCEB),
    background = Color(0xFF0B0E18),
    onBackground = Color(0xFFF5F7FF),
    surface = Color(0xFF151B2E),
    onSurface = Color(0xFFF5F7FF),
    surfaceVariant = Color(0xFF1F2740),
    onSurfaceVariant = Color(0xFF9BA3BE),
    surfaceContainerLowest = Color(0xFF0F1424),
    surfaceContainerLow = Color(0xFF14192B),
    surfaceContainer = Color(0xFF1A2138),
    surfaceContainerHigh = Color(0xFF222B47),
    surfaceContainerHighest = Color(0xFF2C3658),
    outline = Color(0xFF647094),
    outlineVariant = Color(0xFF26304C),
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF3A1622),
    onErrorContainer = Color(0xFFFFE4E6)
)

@Composable
fun StudyFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalStudyFlowDarkTheme provides darkTheme,
        LocalStudyFlowSemanticColors provides semanticColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudyFlowTypography,
            shapes = StudyFlowShapes,
            content = content
        )
    }
}
