package com.wmm.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

enum class Glyph(source: String) {
    BOOK("M5 4h13a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1V5a1 1 0 0 1 1 -1z M8 4v16 M11 9h5 M11 13h5"),
    CHART("M4 20h16 M7 17v-5 M12 17V8 M17 17V4"),
    WALLET("M19 8V6a1 1 0 0 0 -1 -1H6a2 2 0 0 0 -2 2v11a1 1 0 0 0 1 1h14a1 1 0 0 0 1 -1V9a1 1 0 0 0 -1 -1H6a2 2 0 0 1 -2 -1 M20 12h-5a1.5 1.5 0 0 0 0 3h5"),
    PERSON("M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0 M4 21v-1a8 8 0 0 1 16 0v1"),
    PLUS("M12 5v14 M5 12h14"),
    BACK("M19 12H5m7 -7 -7 7 7 7"),
    LEFT("M14 6l-6 6 6 6"),
    RIGHT("M10 6l6 6 -6 6"),
    DOWN("M6 10l6 6 6 -6"),
    TAG("M4 5v7l7 7a1.4 1.4 0 0 0 2 0l6 -6a1.4 1.4 0 0 0 0 -2l-7 -7H5z M8 8h.1"),
    INCOME("M7 7v10h10 M7 17 17 7"),
    EXPENSE("M7 17 17 7 M7 7h10v10"),
    CALENDAR("M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4"),
    SPARK("M12 3l2.1 6.9L21 12l-6.9 2.1L12 21l-2.1 -6.9L3 12l6.9 -2.1z M19 4v4 M17 6h4"),
    BOX("M5 8h14v12H5z M4 4h16v4H4z M10 12h4"),
    EDIT("M4 20l5 -1 11 -11 -4 -4 -11 11z M13 7l4 4"),
    DELETE("M5 7h14 M9 7V4h6v3 M7 7l1 13h8l1 -13 M10 10v6 M14 10v6"),
    CHECK("M5 12l5 5L20 7"),
    EYE("M3 12s3-6 9-6 9 6 9 6-3 6-9 6-9-6-9-6z M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"),
    EYE_OFF("M4 4l16 16 M10 6h2c6 0 9 6 9 6s-1 2-3 4 M6 8c-2 2-3 4-3 4s3 6 9 6h2"),
    ;

    val path: Path = PathParser().parsePathString(source).toPath()
}

@Composable
fun LineIcon(glyph: Glyph, color: Color = MaterialTheme.colorScheme.onSurface,
    description: String? = null, modifier: Modifier = Modifier, size: Int = 22) {
    val semantics = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    Canvas(modifier.size(size.dp).then(semantics)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(glyph.path, color, style = Stroke(1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun IconBadge(glyph: Glyph, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.size(40.dp),
        shape = RoundedCornerShape(13.dp),
        color = if (emphasis) colors.primaryContainer else colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .40f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            LineIcon(glyph, if (emphasis) colors.primary else colors.onSurfaceVariant, size = 19)
        }
    }
}
