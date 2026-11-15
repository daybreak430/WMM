package com.wmm.ledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF101B2E)
private val Paper = Color(0xFFF3F5F8)
private val DarkPaper = Color(0xFF101722)

private val Light = lightColorScheme(
    primary = Color(0xFF245FA6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFECFC),
    onPrimaryContainer = Color(0xFF173E70),
    inversePrimary = Color(0xFFABCDFF),
    secondary = Color(0xFF4D607B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EDF6),
    onSecondaryContainer = Color(0xFF263D59),
    tertiary = Color(0xFF715573),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8EAF8),
    onTertiaryContainer = Color(0xFF533857),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAF0F7),
    onSurfaceVariant = Color(0xFF566478),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFEDF2F8),
    surfaceContainerHigh = Color(0xFFE6EDF5),
    surfaceContainerHighest = Color(0xFFDDE6F1),
    outline = Color(0xFF738197),
    outlineVariant = Color(0xFFDCE4EE),
    inverseSurface = Ink,
    inverseOnSurface = Color(0xFFEFF4FB),
    error = Color(0xFFAE3747),
    errorContainer = Color(0xFFFFE7EA),
    onErrorContainer = Color(0xFF701B2A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFABCDFF),
    onPrimary = Color(0xFF102C55),
    primaryContainer = Color(0xFF233951),
    onPrimaryContainer = Color(0xFFCEE1FF),
    inversePrimary = Color(0xFF245FA6),
    secondary = Color(0xFFBDCFE7),
    onSecondary = Color(0xFF283A50),
    secondaryContainer = Color(0xFF253246),
    onSecondaryContainer = Color(0xFFDDE8F8),
    tertiary = Color(0xFFDFC0E3),
    onTertiary = Color(0xFF402742),
    tertiaryContainer = Color(0xFF583B5B),
    onTertiaryContainer = Color(0xFFFBE3FB),
    background = DarkPaper,
    onBackground = Color(0xFFEFF4FD),
    surface = Color(0xFF1A2331),
    onSurface = Color(0xFFEFF4FD),
    surfaceVariant = Color(0xFF263447),
    onSurfaceVariant = Color(0xFFADBCD0),
    surfaceContainerLowest = Color(0xFF101722),
    surfaceContainerLow = Color(0xFF182233),
    surfaceContainer = Color(0xFF1D2838),
    surfaceContainerHigh = Color(0xFF243144),
    surfaceContainerHighest = Color(0xFF2B394D),
    outline = Color(0xFF8798B0),
    outlineVariant = Color(0xFF34455B),
    inverseSurface = Color(0xFFF0F5FD),
    inverseOnSurface = Ink,
    error = Color(0xFFFFADB7),
    errorContainer = Color(0xFF701B2A),
    onErrorContainer = Color(0xFFFFD9DE),
)

internal val LocalLedgerDark = staticCompositionLocalOf { false }

private val LedgerType = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

val HeroNumber = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", letterSpacing = (-1.9).sp)
val RowNumber = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", letterSpacing = (-0.3).sp)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalLedgerDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = LedgerType,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
