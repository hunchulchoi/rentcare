package com.rentcare.car.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.rentcare.core.ReportTone
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rentcare.core.DesignTokens
import com.rentcare.core.DesignTokens.Radius
import com.rentcare.core.DesignTokens.Type

private fun c(rgb: Int) = Color(0xFF000000 or rgb.toLong())

private fun DesignTokens.Palette.scheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(primary), onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        secondary = c(primary), onSecondary = c(onPrimary),
        secondaryContainer = c(primaryContainer), onSecondaryContainer = c(onPrimaryContainer),
        tertiary = c(primary), onTertiary = c(onPrimary),
        background = c(background), onBackground = c(textPrimary),
        surface = c(background), onSurface = c(textPrimary),
        surfaceVariant = c(surfaceHigh), onSurfaceVariant = c(textSecondary),
        surfaceContainerLowest = c(surface), surfaceContainerLow = c(surface),
        surfaceContainer = c(surface), surfaceContainerHigh = c(surface),
        surfaceContainerHighest = c(surfaceHigh),
        error = c(error), outline = c(outline), outlineVariant = c(outline),
    )
}

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** Pleos type scale (docs/pleos-design-guide.md). Nothing smaller than Label Medium 24 sp (≈3.5 mm). */
private val PleosTypography = Typography(
    displayLarge = style(60, 80, FontWeight.Bold),
    displayMedium = style(56, 72, FontWeight.Bold),
    displaySmall = style(48, 64, FontWeight.Bold),
    headlineLarge = style(Type.TITLE_LARGE_SP, Type.TITLE_LARGE_LH, FontWeight.Bold),
    headlineMedium = style(Type.TITLE_MEDIUM_SP, Type.TITLE_MEDIUM_LH, FontWeight.Bold),
    headlineSmall = style(Type.TITLE_SMALL_SP, Type.TITLE_SMALL_LH, FontWeight.Bold),
    titleLarge = style(Type.TITLE_SMALL_SP, Type.TITLE_SMALL_LH, FontWeight.Bold),
    titleMedium = style(Type.BODY_LARGE_SP, Type.BODY_LARGE_LH, FontWeight.Bold),
    titleSmall = style(Type.BODY_MEDIUM_SP, Type.BODY_MEDIUM_LH, FontWeight.Bold),
    bodyLarge = style(Type.BODY_MEDIUM_SP, Type.BODY_MEDIUM_LH),
    bodyMedium = style(Type.BODY_SMALL_SP, Type.BODY_SMALL_LH),
    bodySmall = style(Type.LABEL_MEDIUM_SP, Type.LABEL_MEDIUM_LH),
    labelLarge = style(Type.BODY_SMALL_SP, Type.BODY_SMALL_LH, FontWeight.Bold),
    labelMedium = style(Type.LABEL_MEDIUM_SP, Type.LABEL_MEDIUM_LH, FontWeight.Medium),
    labelSmall = style(Type.LABEL_MEDIUM_SP, Type.LABEL_MEDIUM_LH),
)

private val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.SMALL.dp),
    small = RoundedCornerShape(Radius.SMALL.dp),
    medium = RoundedCornerShape(Radius.MEDIUM.dp),
    large = RoundedCornerShape(Radius.LARGE.dp),
    extraLarge = RoundedCornerShape(Radius.EXTRA_LARGE.dp),
)

/** Pleos informative colors for status (always paired with a text label). */
data class StatusColors(val positive: Color, val active: Color, val negative: Color, val info: Color, val progress: Color)

val LocalStatusColors = staticCompositionLocalOf {
    DesignTokens.Day.let { StatusColors(c(it.positive), c(it.active), c(it.error), c(it.info), c(it.progress)) }
}

/** Icon/text color for the report state; null = default color. */
@Composable
fun toneColor(tone: ReportTone): Color? {
    val s = LocalStatusColors.current
    return when (tone) {
        ReportTone.NONE -> null
        ReportTone.NOT_SENT -> s.negative
        ReportTone.RECEIVED -> s.positive
        ReportTone.ACKNOWLEDGED -> s.info
        ReportTone.IN_PROGRESS -> s.progress
        ReportTone.RESOLVED -> s.active
    }
}

/** Day/night follows the system (the car switches it with the lights). */
@Composable
fun RentcareTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DesignTokens.Night else DesignTokens.Day
    CompositionLocalProvider(LocalStatusColors provides StatusColors(c(p.positive), c(p.active), c(p.error), c(p.info), c(p.progress))) {
        MaterialTheme(
            colorScheme = p.scheme(dark),
            typography = PleosTypography,
            shapes = SoftShapes,
            content = content,
        )
    }
}
