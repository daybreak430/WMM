package com.wmm.ledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmm.ledger.data.MAX_CENTS
import com.wmm.ledger.data.MAX_NOTE
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryComposer(
    expression: String, onKey: (String) -> Unit, saveLabel: String, saveEnabled: Boolean,
    saving: Boolean, compact: Boolean, onSave: () -> Unit,
    note: String, onNoteChange: (String) -> Unit,
    account: (@Composable () -> Unit)? = null, date: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // 窄窗口／大字号下保存文案可能占两行，整排按同一高度预留，避免状态文字变化挤动键盘。
    val keyHeight = maxOf(48.dp, with(LocalDensity.current) { MaterialTheme.typography.labelLarge.lineHeight.toDp() * 2 + 4.dp })
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var noteFocused by remember { mutableStateOf(false) }
    var noteImeSeen by remember { mutableStateOf(false) }
    fun finishNote() {
        focus.clearFocus()
        keyboard?.hide()
    }
    LaunchedEffect(noteFocused, imeVisible) {
        // 系统返回先收起文字键盘；随后清除备注焦点，让自定义数字键盘恢复。
        when {
            !noteFocused -> noteImeSeen = false
            imeVisible -> noteImeSeen = true
            noteImeSeen -> { focus.clearFocus(); noteImeSeen = false }
        }
    }
    val result = amountResult(expression)
    val calculated = expression.any { it in "+-" }
    val displayedResult = result ?: amountResult(expression.trimEnd('+', '-'))
    val invalid = result != null && result !in 0..MAX_CENTS
    BoxWithConstraints(Modifier.imePadding()) {
        // 小屏收紧金额卡片与键盘间距，账户单独成行后仍给分类列表留下可滚动空间。
        val dense = compact || maxHeight < 600.dp
        Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (dense) 2.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (account != null) account()
                Surface(color = colors.surface, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 0.dp)) {
                        Column(Modifier.fillMaxWidth().height(if (dense) 46.dp else 52.dp).clickable(onClick = ::finishNote).testTag("entry-amount")
                            .semantics { contentDescription = "金额 ${displayedResult?.let(::currency) ?: expression.ifEmpty { "0.00" }}" },
                            verticalArrangement = Arrangement.Center) {
                            if (calculated) Text(expression, color = colors.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            Text("¥ ${if (calculated) displayedResult?.let(::editableAmount) ?: "—" else expression.ifEmpty { "0.00" }}",
                                color = if (invalid) colors.error else colors.primary,
                                style = LedgerBalanceNumber.copy(fontSize = if (dense) 24.sp else if (calculated) 28.sp else 36.sp,
                                    lineHeight = if (dense) 28.sp else if (calculated) 32.sp else 42.sp), maxLines = 1,
                                modifier = Modifier.horizontalScroll(rememberScrollState()))
                        }
                        Hairline()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            date()
                            BasicTextField(
                                value = note,
                                onValueChange = { onNoteChange(it.replace('\n', ' ').replace('\r', ' ').take(MAX_NOTE)) },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                    .onFocusChanged { noteFocused = it.isFocused }.testTag("entry-note")
                                    .semantics { contentDescription = "备注" },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                                singleLine = true, cursorBrush = SolidColor(colors.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { finishNote() }),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                                        if (note.isEmpty()) Text("添加备注", color = colors.onSurfaceVariant.copy(alpha = .65f),
                                            style = MaterialTheme.typography.bodyMedium)
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                }
                if (!noteFocused && !imeVisible) {
                    val rows = listOf(listOf("1", "2", "3", "backspace"), listOf("4", "5", "6", "+"),
                        listOf("7", "8", "9", "-"), listOf("clear", "0", ".", "save"))
                    Column(Modifier.fillMaxWidth().testTag("amount-keypad"),
                        verticalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 4.dp)) {
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
                                    val interaction = remember { MutableInteractionSource() }
                                    val pressed by interaction.collectIsPressedAsState()
                                    Surface(shape = RoundedCornerShape(12.dp),
                                        color = ledgerColor(if (action) {
                                            if (enabled) colors.primary else colors.onSurface.copy(alpha = .10f)
                                        } else if (pressed) colors.surfaceContainerHighest else colors.surface),
                                        modifier = Modifier.weight(1f).height(keyHeight)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                // 由草稿持有者读取最新状态，连续快速点按不依赖上一帧的显示值。
                                                if (action) onSave() else onKey(key)
                                            }
                                            .testTag("amount-key-$key").semantics {
                                                contentDescription = description
                                                role = Role.Button
                                            }) {
                                        Box(Modifier.ledgerPress(interaction), contentAlignment = Alignment.Center) {
                                            Text(label, color = when {
                                                action && enabled -> colors.onPrimary
                                                !enabled -> colors.onSurface.copy(alpha = .38f)
                                                else -> colors.onSurface
                                            }, style = if (action || key == "clear") MaterialTheme.typography.labelLarge
                                            else MaterialTheme.typography.headlineSmall,
                                                textAlign = TextAlign.Center,
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
}

@Composable
internal fun EntryMetaButton(label: String, value: String, icon: Glyph,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // 胶囊视觉高度独立于触控区，保持紧凑外观和至少 44dp 的可点范围。
    Box(modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "$label，$value" }, contentAlignment = Alignment.CenterStart) {
        Surface(shape = RoundedCornerShape(50), color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .42f))) {
            Row(Modifier.heightIn(min = 30.dp).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LucideIcon(icon, colors.primary, size = 14)
                Text(value, modifier = Modifier.weight(1f, fill = false).ledgerContentChange(value),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
