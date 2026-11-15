package com.wmm.ledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

val PagePadding = 20.dp
val CardShape = RoundedCornerShape(22.dp)
val FieldShape = RoundedCornerShape(14.dp)
private val ActionShape = RoundedCornerShape(17.dp)
private val SummaryShape = RoundedCornerShape(27.dp)

@Composable
internal fun LedgerScreenFrame(refined: Boolean, content: @Composable () -> Unit) {
    LedgerScreenTheme(refined) {
        Box(Modifier.fillMaxSize()) {
            LedgerBackdrop()
            content()
        }
    }
}

@Composable
private fun LedgerBackdrop() {
    val colors = MaterialTheme.colorScheme
    val dark = LocalLedgerDark.current
    val refined = LocalLedgerRefinement.current
    Canvas(Modifier.fillMaxSize()) {
        val distance = (if (refined) 280.dp else 330.dp).toPx()
        // 顶部的色彩自然融入页面底色，向下滚动的内容仍在中性背景上。
        drawRect(Brush.verticalGradient(
            colorStops = arrayOf(
                0f to when {
                    refined && dark -> Color(0xFF1C3654)
                    refined -> Color(0xFFD3E7FC)
                    dark -> Color(0xFF1C304A)
                    else -> Color(0xFFC8E2FB)
                },
                .52f to when {
                    refined && dark -> Color(0xFF18273F)
                    refined -> Color(0xFFE4EFFB)
                    dark -> Color(0xFF162236)
                    else -> Color(0xFFE6EFFA)
                },
                1f to colors.background,
            ),
            endY = distance,
        ))
        drawRect(Brush.radialGradient(
            colors = listOf(when {
                refined && dark -> Color(0xFF314860).copy(alpha = .50f)
                refined -> Color(0xFFF4FAFF).copy(alpha = .36f)
                dark -> Color(0xFF7BABED).copy(alpha = .10f)
                else -> Color.White.copy(alpha = .58f)
            }, Color.Transparent),
            center = Offset(size.width * .83f, 4.dp.toPx()),
            radius = 218.dp.toPx(),
        ))
    }
}

@Composable
fun BalancePanel(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalLedgerDark.current
    val refined = LocalLedgerRefinement.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (refined) RoundedCornerShape(24.dp) else SummaryShape,
        color = if (dark) Color(0xFF1E2E45) else Color(0xFFECF5FF),
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, if (dark) Color(if (refined) 0xFF49617F else 0xFF415977) else Color.White),
        shadowElevation = if (refined) 2.dp else 1.dp,
    ) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                val start = if (dark) Color(if (refined) 0xFF233850 else 0xFF263C58) else if (refined) Color.White else Color(0xFFFCFEFF)
                val end = if (dark) Color(if (refined) 0xFF1B2B41 else 0xFF1A273B) else Color(if (refined) 0xFFE7F2FF else 0xFFD9EBFF)
                val endPoint = Offset(size.width, size.height)
                drawRect(if (refined) Brush.linearGradient(.28f to start, 1f to end, end = endPoint)
                    else Brush.linearGradient(listOf(start, end), end = endPoint))
                drawRect(Brush.radialGradient(
                    colors = listOf(
                        if (dark) Color(0xFF709AD7).copy(alpha = .17f) else Color.White.copy(alpha = .88f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * .89f, size.height * .14f),
                    radius = size.width * .48f,
                ))
            }
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}

@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    val refined = LocalLedgerRefinement.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (refined) RoundedCornerShape(18.dp) else CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (refined) 1f else .70f)),
    ) {
        Column(content = content)
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    glyph: Glyph? = null, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val refined = LocalLedgerRefinement.current
    val dark = LocalLedgerDark.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = if (refined) 50.dp else 53.dp),
        enabled = enabled,
        shape = if (refined) FieldShape else ActionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (refined && pressed) Color(if (dark) 0xFF71A6F5 else 0xFF1956B8) else colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.surfaceContainerHighest,
            disabledContentColor = colors.onSurfaceVariant,
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) LucideIcon(glyph, if (enabled) colors.onPrimary else colors.onSurfaceVariant, size = 20)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun <T> LedgerSegments(options: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val refined = LocalLedgerRefinement.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FieldShape,
        color = colors.surface.copy(alpha = .75f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .80f)),
    ) {
        Row(
            Modifier.padding(3.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (option in options) {
                val active = selected == option
                val shape = RoundedCornerShape(11.dp)
                Box(
                    Modifier.weight(1f).heightIn(min = if (refined) 44.dp else 48.dp).clip(shape)
                        .background(if (active) colors.primaryContainer else Color.Transparent)
                        .selectable(active, role = Role.RadioButton) { onSelect(option) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title(option), color = if (active) colors.primary else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
