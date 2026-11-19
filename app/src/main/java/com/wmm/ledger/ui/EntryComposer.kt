package com.wmm.ledger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmm.ledger.data.MAX_CENTS
import com.wmm.ledger.data.currency
import com.wmm.ledger.data.editableAmount

// 竖屏固定底部输入区；横屏将分类与输入分列，避免数字键盘挤掉分类和主操作。
@Composable
internal fun EntryEditorLayout(
    header: @Composable () -> Unit,
    content: @Composable (Boolean) -> Unit,
    composer: @Composable (Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) {
                    header()
                    Box(Modifier.weight(1f)) { content(true) }
                }
                Box(Modifier.weight(1f)) { composer(true) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header()
                Box(Modifier.weight(1f)) { content(false) }
                composer(false)
            }
        }
    }
}

@Composable
internal fun EntryComposer(
    expression: String, onKey: (String) -> Unit, saveLabel: String, saveEnabled: Boolean,
    saving: Boolean, compact: Boolean, onSave: () -> Unit,
    account: (@Composable () -> Unit)? = null, metadata: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val result = amountResult(expression)
    val calculated = expression.any { it in "+-" }
    val displayedResult = result ?: amountResult(expression.trimEnd('+', '-'))
    val invalid = result != null && result !in 0..MAX_CENTS
    BoxWithConstraints {
        // 小屏收紧金额卡片与键盘间距，账户单独成行后仍给分类列表留下可滚动空间。
        val dense = compact || maxHeight < 600.dp
        Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (dense) 2.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 6.dp)) {
                if (account != null) account()
                Surface(color = colors.surface, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (dense) 0.dp else 6.dp)) {
                        Column(Modifier.fillMaxWidth().height(if (dense) 46.dp else 64.dp).testTag("entry-amount")
                            .semantics { contentDescription = "金额 ${displayedResult?.let(::currency) ?: expression.ifEmpty { "0.00" }}" },
                            verticalArrangement = Arrangement.Center) {
                            if (calculated) Text(expression, color = colors.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            Text("¥ ${if (calculated) displayedResult?.let(::editableAmount) ?: "—" else expression.ifEmpty { "0.00" }}",
                                color = if (invalid) colors.error else colors.primary,
                                style = LedgerBalanceNumber.copy(fontSize = if (dense || calculated) 28.sp else 36.sp,
                                    lineHeight = if (dense || calculated) 32.sp else 42.sp), maxLines = 1,
                                modifier = Modifier.horizontalScroll(rememberScrollState()))
                        }
                        Hairline()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically, content = metadata)
                    }
                }
                val rows = listOf(listOf("1", "2", "3", "backspace"), listOf("4", "5", "6", "+"),
                    listOf("7", "8", "9", "-"), listOf("clear", "0", ".", "save"))
                Column(Modifier.fillMaxWidth().testTag("amount-keypad"),
                    verticalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 6.dp)) {
                    rows.forEach { keys ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            keys.forEach { key ->
                                val action = key == "save"
                                val enabled = !saving && (!action || saveEnabled)
                                val label = when (key) {
                                    "backspace" -> "⌫"
                                    "clear" -> "清空"
                                    "save" -> if (saving) "正在保存" else saveLabel
                                    "-" -> "−"
                                    else -> key
                                }
                                val description = when (key) {
                                    "backspace" -> "删除一位"
                                    "clear" -> "清空金额"
                                    "+" -> "加"
                                    "-" -> "减"
                                    else -> label
                                }
                                Surface(onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    // 由草稿持有者读取最新状态，连续快速点按不依赖上一帧的显示值。
                                    if (action) onSave() else onKey(key)
                                }, enabled = enabled, shape = RoundedCornerShape(12.dp),
                                    color = if (action) {
                                        if (enabled) colors.primary else colors.onSurface.copy(alpha = .10f)
                                    } else colors.surface,
                                    modifier = Modifier.weight(1f).height(if (dense) 48.dp else 52.dp)
                                        .testTag("amount-key-$key").semantics {
                                            contentDescription = description
                                            role = Role.Button
                                        }) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(label, color = when {
                                            action && enabled -> colors.onPrimary
                                            !enabled -> colors.onSurface.copy(alpha = .38f)
                                            else -> colors.onSurface
                                        }, style = if (action || key == "clear") MaterialTheme.typography.labelLarge
                                        else MaterialTheme.typography.headlineSmall,
                                            fontWeight = if (action) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = if (action) 2 else 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EntryMetaButton(label: String, value: String, icon: Glyph,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = colors.surface,
        modifier = modifier.heightIn(min = 40.dp).semantics { role = Role.Button; contentDescription = "$label，$value" }) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LucideIcon(icon, colors.onSurfaceVariant, size = 15)
            Text(value, modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
