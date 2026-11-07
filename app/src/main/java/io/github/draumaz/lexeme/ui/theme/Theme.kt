package io.github.draumaz.lexeme.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.draumaz.lexeme.R

// Fallback for devices without dynamic color: ink-blue on paper.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4C56AF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFE0FF),
    onPrimaryContainer = Color(0xFF343D96),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E0F9),
    onSecondaryContainer = Color(0xFF434659),
    tertiary = Color(0xFF77536D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF5D3C55),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
    surfaceDim = Color(0xFFDBD9E0),
    surfaceBright = Color(0xFFFBF8FF),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC7C5D0),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2EFF7),
    inversePrimary = Color(0xFFBCC2FF),
    surfaceTint = Color(0xFF4C56AF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    onPrimary = Color(0xFF1A247E),
    primaryContainer = Color(0xFF343D96),
    onPrimaryContainer = Color(0xFFDFE0FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E0F9),
    tertiary = Color(0xFFE6BAD7),
    onTertiary = Color(0xFF44263D),
    tertiaryContainer = Color(0xFF5D3C55),
    onTertiaryContainer = Color(0xFFFFD7F1),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
    surfaceDim = Color(0xFF131318),
    surfaceBright = Color(0xFF39383F),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),
    inversePrimary = Color(0xFF4C56AF),
    surfaceTint = Color(0xFFBCC2FF),
)

/** Whether the active scheme is dark; images that need inverting read this. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Google Sans Flex, bundled as one variable font: every weight from Thin to Black, with real
 * italics from its slant axis rather than a synthesized skew. It covers Latin only; Greek,
 * Cyrillic, CJK and IPA fall back to the system fonts glyph by glyph.
 */
val GoogleSansFlex = FontFamily(
    (100..900 step 100).flatMap { w ->
        listOf(false, true).map { italic ->
            Font(
                R.font.google_sans_flex,
                FontWeight(w),
                if (italic) FontStyle.Italic else FontStyle.Normal,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(w),
                    FontVariation.slant(if (italic) -10f else 0f),
                ),
            )
        }
    }
)

/** The "Lexeme" wordmark alone keeps the bookish serif: Noto Serif Light, subset to just the letters it uses. */
val Wordmark = FontFamily(
    Font(
        R.font.noto_serif_wordmark,
        FontWeight.Light,
        variationSettings = FontVariation.Settings(FontVariation.weight(300)),
    )
)

private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
        displayLargeEmphasized = displayLargeEmphasized.f(), displayMediumEmphasized = displayMediumEmphasized.f(),
        displaySmallEmphasized = displaySmallEmphasized.f(), headlineLargeEmphasized = headlineLargeEmphasized.f(),
        headlineMediumEmphasized = headlineMediumEmphasized.f(), headlineSmallEmphasized = headlineSmallEmphasized.f(),
        titleLargeEmphasized = titleLargeEmphasized.f(), titleMediumEmphasized = titleMediumEmphasized.f(),
        titleSmallEmphasized = titleSmallEmphasized.f(), bodyLargeEmphasized = bodyLargeEmphasized.f(),
        bodyMediumEmphasized = bodyMediumEmphasized.f(), bodySmallEmphasized = bodySmallEmphasized.f(),
        labelLargeEmphasized = labelLargeEmphasized.f(), labelMediumEmphasized = labelMediumEmphasized.f(),
        labelSmallEmphasized = labelSmallEmphasized.f(),
    )
}

private val AppTypography = Typography().withFamily(GoogleSansFlex)

@Composable
fun LexemeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = AppTypography,
            content = content,
        )
    }
}

object LexemeTheme {
    val dark: Boolean @Composable get() = LocalDarkTheme.current
    val colors @Composable get() = MaterialTheme.colorScheme
}
