package com.wmm.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

private val Paper = Color(0xFFF8F7F3)
private val Ink = Color(0xFF202722)
val Cover = Color(0xFF183D34)
val CoverInk = Color(0xFFF4F6EA)
val Lime = Color(0xFFDFEDAE)
val Clay = Color(0xFFB96C48)

private val Light = lightColorScheme(
    primary = Color(0xFF30584A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EDDE),
    onPrimaryContainer = Color(0xFF243C2F),
    secondary = Color(0xFF4D6658),
    secondaryContainer = Color(0xFFE6EBE1),
    onSecondaryContainer = Color(0xFF333E35),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECEDE5),
    onSurfaceVariant = Color(0xFF5F6A62),
    surfaceContainer = Color(0xFFF0F0E8),
    surfaceContainerLow = Color(0xFFF4F3ED),
    surfaceContainerHigh = Color(0xFFEBECE3),
    surfaceContainerHighest = Color(0xFFE6E7DE),
    outline = Color(0xFF737E75),
    outlineVariant = Color(0xFFDEE0D6),
    error = Color(0xFF9E4232),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB2D5C0),
    onPrimary = Color(0xFF193127),
    primaryContainer = Color(0xFF29463A),
    onPrimaryContainer = Color(0xFFD5EDDD),
    secondaryContainer = Color(0xFF2D3931),
    onSecondaryContainer = Color(0xFFD5E2D7),
    background = Color(0xFF161C18),
    onBackground = Color(0xFFE8EDE3),
    surface = Color(0xFF161C18),
    onSurface = Color(0xFFE8EDE3),
    surfaceVariant = Color(0xFF2D3730),
    onSurfaceVariant = Color(0xFFB3C0B5),
    surfaceContainer = Color(0xFF242C25),
    surfaceContainerLow = Color(0xFF1B241E),
    surfaceContainerHigh = Color(0xFF2C372F),
    surfaceContainerHighest = Color(0xFF333F35),
    outline = Color(0xFF9DAA9F),
    outlineVariant = Color(0xFF38463C),
    error = Color(0xFFFFB4A2),
)

private val LedgerType = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

val HeroNumber = TextStyle(fontSize = 38.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold,
    fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", letterSpacing = (-1.6).sp)
val RowNumber = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
    fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum")

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = LedgerType, content = content)
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
    Box(modifier.size(42.dp).background(if (emphasis) colors.primaryContainer else colors.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center) {
        LineIcon(glyph, if (emphasis) colors.onPrimaryContainer else colors.onSurfaceVariant, size = 19)
    }
}
