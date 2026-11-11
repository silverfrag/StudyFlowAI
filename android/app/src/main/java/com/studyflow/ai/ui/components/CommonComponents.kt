package com.studyflow.ai.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.extractCourseMonogram
import com.studyflow.ai.ui.theme.rememberCourseVisualStyle
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
import java.util.Locale

@Composable
fun TactileSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    lipColor: Color = studyFlowSemanticColors().tactileDefaultLip,
    borderColor: Color? = studyFlowSemanticColors().cardBorder,
    shape: Shape = RoundedCornerShape(20.dp),
    lipHeight: Dp = 4.dp,
    fillWidth: Boolean = true,
    fillHeight: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffsetPx = with(LocalDensity.current) {
        if (onClick != null && enabled && isPressed) (lipHeight * 0.6f).toPx() else 0f
    }
    val innerModifier = Modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
        .padding(bottom = lipHeight)
        .graphicsLayer { translationY = pressOffsetPx }

    Box(modifier = modifier) {
        if (lipHeight > 0.dp && lipColor.alpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = lipHeight)
                    .clip(shape)
                    .background(lipColor)
            )
        }
        if (onClick != null) {
            Surface(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                modifier = innerModifier,
                shape = shape,
                color = containerColor,
                border = borderColor?.let { BorderStroke(1.dp, it) }
            ) {
                Box(content = content)
            }
        } else {
            Surface(
                modifier = innerModifier,
                shape = shape,
                color = containerColor,
                border = borderColor?.let { BorderStroke(1.dp, it) }
            ) {
                Box(content = content)
            }
        }
    }
}

@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    fillHeight: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    lipColor: Color = studyFlowSemanticColors().primaryLip,
    borderColor: Color? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    lipHeight: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val mutedSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    val resolvedBg = if (enabled) {
        containerColor
    } else {
        lerp(containerColor, mutedSurface, 0.62f).copy(alpha = 1f)
    }
    val resolvedLip = if (enabled) {
        lipColor
    } else {
        lerp(lipColor, mutedSurface, 0.55f).copy(alpha = 1f)
    }
    val resolvedBorder = borderColor?.let { bc ->
        if (enabled) bc else lerp(bc, mutedSurface, 0.5f).copy(alpha = 1f)
    }
    val resolvedFg = if (enabled) contentColor else contentColor.copy(alpha = 0.45f)

    TactileSurface(
        modifier = modifier,
        containerColor = resolvedBg,
        lipColor = resolvedLip,
        borderColor = resolvedBorder,
        shape = shape,
        lipHeight = lipHeight,
        fillWidth = fillWidth,
        fillHeight = fillHeight,
        onClick = onClick,
        enabled = enabled
    ) {
        ProvideTextStyle(
            value = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                color = resolvedFg
            )
        ) {
            CompositionLocalProvider(LocalContentColor provides resolvedFg) {
                Row(
                    modifier = Modifier
                        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                        .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

@Composable
fun StudyHeroArtwork(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(86.dp)) {
        val w = size.width
        val h = size.height

        // Ambient soft circular halo behind stacked book
        drawCircle(
            color = Color(0xFF5B52F0).copy(alpha = 0.24f),
            radius = w * 0.44f,
            center = Offset(w * 0.52f, h * 0.52f)
        )

        // Back card tilted
        rotate(degrees = 10f, pivot = Offset(w * 0.54f, h * 0.55f)) {
            drawRoundRect(
                color = Color(0xFF343B6E),
                topLeft = Offset(w * 0.19f, h * 0.22f),
                size = Size(w * 0.62f, h * 0.64f),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            )
        }

        // Cream page deck sheet
        rotate(degrees = 4f, pivot = Offset(w * 0.52f, h * 0.54f)) {
            drawRoundRect(
                color = Color(0xFFFEF2C7),
                topLeft = Offset(w * 0.16f, h * 0.21f),
                size = Size(w * 0.62f, h * 0.64f),
                cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx())
            )
        }

        // Main front Indigo cover
        rotate(degrees = -5f, pivot = Offset(w * 0.48f, h * 0.56f)) {
            val left = w * 0.14f
            val top = h * 0.22f
            val coverW = w * 0.62f
            val coverH = h * 0.64f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF5B52F0), Color(0xFF4439D8)),
                    start = Offset(left, top),
                    end = Offset(left + coverW, top + coverH)
                ),
                topLeft = Offset(left, top),
                size = Size(coverW, coverH),
                cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx())
            )
            // Book spine band
            drawRoundRect(
                color = Color(0xFF817BFF),
                topLeft = Offset(left + 4.dp.toPx(), top + 5.dp.toPx()),
                size = Size(6.dp.toPx(), coverH - 10.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            // Text lines on cover
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(left + 18.dp.toPx(), top + 18.dp.toPx()),
                end = Offset(left + coverW - 12.dp.toPx(), top + 18.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(left + 18.dp.toPx(), top + 27.dp.toPx()),
                end = Offset(left + coverW - 20.dp.toPx(), top + 27.dp.toPx()),
                strokeWidth = 2.6.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Coral bookmark ribbon notch at top-right
            val ribbonPath = Path().apply {
                moveTo(left + coverW - 18.dp.toPx(), top)
                lineTo(left + coverW - 10.dp.toPx(), top)
                lineTo(left + coverW - 10.dp.toPx(), top + 16.dp.toPx())
                lineTo(left + coverW - 14.dp.toPx(), top + 12.dp.toPx())
                lineTo(left + coverW - 18.dp.toPx(), top + 16.dp.toPx())
                close()
            }
            drawPath(ribbonPath, color = Color(0xFFF59E0B))
        }

        // Top-right Emerald sparkle + badge
        val badgeCenter = Offset(w * 0.80f, h * 0.24f)
        drawCircle(
            color = Color(0xFF10B981),
            radius = 10.dp.toPx(),
            center = badgeCenter
        )
        drawLine(
            color = Color.White,
            start = Offset(badgeCenter.x - 4.dp.toPx(), badgeCenter.y),
            end = Offset(badgeCenter.x + 4.dp.toPx(), badgeCenter.y),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(badgeCenter.x, badgeCenter.y - 4.dp.toPx()),
            end = Offset(badgeCenter.x, badgeCenter.y + 4.dp.toPx()),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Accent sparkle dots
        drawCircle(color = Color(0xFFFBBF24), radius = 3.dp.toPx(), center = Offset(w * 0.14f, h * 0.20f))
        drawCircle(color = Color(0xFF38BDF8), radius = 2.4.dp.toPx(), center = Offset(w * 0.88f, h * 0.72f))
    }
}

@Composable
fun CelebrationConfettiCanvas(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pieces = listOf(
            Triple(Offset(w * 0.12f, h * 0.18f), 5.dp, Color(0xFFFDE68A).copy(alpha = 0.55f)),
            Triple(Offset(w * 0.84f, h * 0.16f), 6.dp, Color(0xFFA7F3D0).copy(alpha = 0.55f)),
            Triple(Offset(w * 0.90f, h * 0.42f), 4.dp, Color(0xFFFDA4AF).copy(alpha = 0.65f)),
            Triple(Offset(w * 0.08f, h * 0.62f), 4.5.dp, Color(0xFFC4B5FD).copy(alpha = 0.6f)),
            Triple(Offset(w * 0.76f, h * 0.78f), 5.dp, Color.White.copy(alpha = 0.35f)),
            Triple(Offset(w * 0.24f, h * 0.84f), 3.5.dp, Color(0xFFFBBF24).copy(alpha = 0.55f))
        )
        pieces.forEach { (center, radiusDp, color) ->
            drawCircle(color = color, radius = radiusDp.toPx(), center = center)
        }
        rotate(22f, pivot = Offset(w * 0.18f, h * 0.32f)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(w * 0.15f, h * 0.30f),
                size = Size(14.dp.toPx(), 5.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
        rotate(-28f, pivot = Offset(w * 0.88f, h * 0.66f)) {
            drawRoundRect(
                color = Color(0xFFFDE68A).copy(alpha = 0.45f),
                topLeft = Offset(w * 0.85f, h * 0.64f),
                size = Size(16.dp.toPx(), 5.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
    }
}

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translateAnim - 280f, translateAnim - 280f),
        end = Offset(translateAnim, translateAnim)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    brush: Brush = rememberShimmerBrush()
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun HomeSkeletonView() {
    val brush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(176.dp), shape = RoundedCornerShape(26.dp), brush = brush)
        Row(
            modifier = Modifier.fillMaxWidth().height(168.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(modifier = Modifier.weight(0.92f).fillMaxHeight(), shape = RoundedCornerShape(22.dp), brush = brush)
            Column(
                modifier = Modifier.weight(1.08f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(20.dp), brush = brush)
                ShimmerBox(modifier = Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(20.dp), brush = brush)
            }
        }
        ShimmerBox(modifier = Modifier.width(140.dp).height(20.dp), shape = RoundedCornerShape(8.dp), brush = brush)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                ShimmerBox(modifier = Modifier.weight(1f).height(106.dp), shape = RoundedCornerShape(20.dp), brush = brush)
            }
        }
    }
}

@Composable
fun ListSkeletonView(itemCount: Int = 4, showSearchSkeleton: Boolean = true) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showSearchSkeleton) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp), brush = brush)
        }
        repeat(itemCount) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(126.dp), shape = RoundedCornerShape(22.dp), brush = brush)
        }
    }
}

@Composable
fun DetailSkeletonView() {
    val brush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(156.dp), shape = RoundedCornerShape(24.dp), brush = brush)
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(22.dp), brush = brush)
        repeat(3) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(92.dp), shape = RoundedCornerShape(18.dp), brush = brush)
        }
    }
}

@Composable
fun LoadingStateView(message: String = "Đang tải dữ liệu...") {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.2.dp,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    description: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.Inbox
) {
    val semanticColors = studyFlowSemanticColors()
    Box(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        TactileSurface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            lipColor = semanticColors.tactileDefaultLip,
            borderColor = semanticColors.cardBorder,
            shape = RoundedCornerShape(26.dp),
            lipHeight = 4.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = semanticColors.lavenderBento,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (actionLabel != null && onActionClick != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    TactileButton(
                        onClick = onActionClick,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    Box(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        TactileSurface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp),
            containerColor = semanticColors.coralBento,
            lipColor = semanticColors.coralLip,
            borderColor = semanticColors.coralBorder,
            shape = RoundedCornerShape(26.dp),
            lipHeight = 4.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = semanticColors.error.copy(alpha = 0.14f),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = semanticColors.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Đã xảy ra lỗi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = semanticColors.coralContent
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.coralContent.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                TactileButton(
                    onClick = onRetry,
                    containerColor = semanticColors.midnightInk,
                    contentColor = semanticColors.midnightOnInk,
                    lipColor = semanticColors.midnightInkLip,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Thử lại")
                }
            }
        }
    }
}

@Composable
fun CourseMonogramBadge(
    courseName: String,
    courseSeed: String,
    size: Dp = 46.dp,
    solidAccent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val style = rememberCourseVisualStyle(seed = courseSeed.ifBlank { courseName })
    val monogram = extractCourseMonogram(courseName)
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(14.dp),
        color = if (solidAccent) style.accent else style.container,
        border = if (solidAccent) null else BorderStroke(1.dp, style.accent.copy(alpha = 0.30f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (solidAccent) {
                        Modifier.background(
                            Brush.linearGradient(listOf(style.accent, style.gradientEnd))
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = (size.value * 0.34f).sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = if (solidAccent) Color.White else style.onContainer
            )
        }
    }
}

@Composable
fun StatusPillBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
    isPulsing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by if (isPulsing) {
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(750),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotPulse"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(contentColor)
            )
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ScoreRingGauge(
    scoreOutOfTen: Double,
    size: Dp = 96.dp,
    strokeWidth: Dp = 9.dp,
    ringColor: Color? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    primaryTextColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showSubLabel: Boolean = true,
    subLabelText: String = "/ 10",
    modifier: Modifier = Modifier
) {
    val semanticColors = studyFlowSemanticColors()
    val clamped = scoreOutOfTen.coerceIn(0.0, 10.0)
    val ratio by animateFloatAsState(
        targetValue = (clamped / 10.0).toFloat(),
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "scoreRing"
    )
    val resolvedColor = ringColor ?: when {
        clamped >= 8.0 -> semanticColors.success
        clamped >= 6.0 -> MaterialTheme.colorScheme.primary
        clamped >= 4.0 -> semanticColors.warning
        else -> semanticColors.error
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width, this.size.height)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                size = arcSize
            )
            drawArc(
                color = resolvedColor,
                startAngle = -90f,
                sweepAngle = 360f * ratio,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                size = arcSize
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.US, "%.1f", clamped),
                style = if (size >= 90.dp) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.ExtraBold,
                color = primaryTextColor
            )
            if (showSubLabel) {
                Text(
                    text = subLabelText,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor
                )
            }
        }
    }
}

@Composable
fun MiniAccuracyRing(
    accuracy: Double,
    size: Dp = 50.dp,
    strokeWidth: Dp = 5.dp,
    ringColorOverride: Color? = null,
    trackColorOverride: Color? = null,
    modifier: Modifier = Modifier
) {
    val semanticColors = studyFlowSemanticColors()
    val clamped = accuracy.coerceIn(0.0, 1.0)
    val animatedRatio by animateFloatAsState(
        targetValue = clamped.toFloat(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "accuracyRing"
    )
    val color = ringColorOverride ?: when {
        clamped >= 0.75 -> semanticColors.success
        clamped >= 0.60 -> MaterialTheme.colorScheme.primary
        clamped >= 0.35 -> semanticColors.warning
        else -> semanticColors.error
    }
    val track = trackColorOverride ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val percent = (clamped * 100).toInt()

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val strokePx = strokeWidth.toPx()
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedRatio,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyStatePreview() {
    StudyFlowTheme {
        EmptyStateView(
            title = "Chưa có môn học nào",
            description = "Hãy tạo môn học đầu tiên và tải tài liệu lên để AI đồng hành cùng bạn.",
            actionLabel = "Tạo môn học ngay",
            onActionClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
    StudyFlowTheme {
        ErrorStateView(
            message = "Không thể kết nối đến máy chủ.",
            onRetry = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GaugesPreview() {
    StudyFlowTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CourseMonogramBadge(courseName = "Cơ sở dữ liệu", courseSeed = "c1", solidAccent = true)
            ScoreRingGauge(scoreOutOfTen = 8.4)
            MiniAccuracyRing(accuracy = 0.80)
            StudyHeroArtwork()
        }
    }
}
