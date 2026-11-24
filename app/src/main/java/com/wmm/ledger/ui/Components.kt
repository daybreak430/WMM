package com.wmm.ledger.ui

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmm.ledger.R
import com.wmm.ledger.data.FIRST_DAY
import com.wmm.ledger.data.Profile
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val DateLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.SIMPLIFIED_CHINESE)

@Composable
fun PageHeader(title: String, back: (() -> Unit)? = null, mark: Glyph? = null, centered: Boolean = false,
    end: @Composable () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (LocalLedgerRefinement.current) 64.dp else 68.dp)
            .padding(horizontal = LedgerPageInset(), vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) PageBackButton(back)
        if (mark != null) {
            Surface(
                modifier = Modifier.size(38.dp), shape = FieldShape, color = colors.surface,
            ) {
                Box(contentAlignment = Alignment.Center) { LucideIcon(mark, color = colors.primary, size = 21) }
            }
        }
        Text(title, Modifier.weight(1f),
            style = if (back == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (centered) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { end() } else end()
    }
}

@Composable
internal fun PageBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            Box(contentAlignment = Alignment.Center) { LucideIcon(Glyph.BACK, description = "返回", size = 19) }
        }
    }
}

@Composable
internal fun PageCloseButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            Box(contentAlignment = Alignment.Center) { LucideIcon(Glyph.CLOSE, description = "关闭", size = 19) }
        }
    }
}

@Composable
fun UserAvatar(profile: Profile, onClick: () -> Unit, size: Int = 44, description: String = "个人设置") {
    val image = remember(profile.avatar) {
        profile.avatar?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val colors = MaterialTheme.colorScheme
    IconButton(onClick = onClick, modifier = Modifier.size(maxOf(size, 48).dp)) {
        Surface(
            shape = CircleShape,
            color = colors.surface,
            modifier = Modifier.size(size.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (image == null) LucideIcon(Glyph.PERSON, colors.primary, description, size = 21)
                else Image(image, description, Modifier.size(size.dp), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
fun AmountText(amount: String, color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier, style: TextStyle = RowNumber) {
    BasicText(
        text = amount,
        modifier = modifier.ledgerContentChange(amount),
        style = style.copy(color = ledgerColor(color)),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = when {
            style.fontSize.value <= 30 -> 12.sp
            LocalLedgerRefinement.current -> 30.sp
            else -> 23.sp
        },
            maxFontSize = style.fontSize, stepSize = 1.sp),
    )
}

@Composable
fun ledgerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, end: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).ledgerContentChange(title), style = MaterialTheme.typography.titleLarge)
        end()
    }
}

@Composable
fun EmptyPanel(title: String, detail: String? = null, glyph: Glyph = Glyph.BOOK) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconBadge(glyph)
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun SettingsRow(title: String, description: String? = null, icon: Glyph? = null, enabled: Boolean = true,
    end: String? = null, endContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (leadingContent != null) leadingContent() else if (icon != null) IconBadge(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (endContent != null) {
            Box(Modifier.weight(1.15f), contentAlignment = Alignment.CenterEnd) {
                endContent()
            }
        } else if (end != null) {
            Box(Modifier.weight(.52f), contentAlignment = Alignment.CenterEnd) {
                Text(end, modifier = Modifier.ledgerContentChange(end), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LucideIcon(Glyph.RIGHT, colors.onSurfaceVariant, size = 17)
    }
}

@Composable
fun Hairline(inset: Int = 0) = HorizontalDivider(
    Modifier.padding(horizontal = inset.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .78f),
)

@Composable
internal fun SettingsSwitchRow(title: String, checked: Boolean, icon: Glyph? = null, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onToggle)
        .padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) IconBadge(icon)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    LedgerAlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun CalendarAction(day: LocalDate, limit: LocalDate = LocalDate.now(), earliest: LocalDate = FIRST_DAY,
    onSelect: (LocalDate) -> Unit): () -> Unit {
    val context = LocalContext.current
    val theme = if (LocalLedgerDark.current) R.style.LedgerPickerDark else R.style.LedgerPickerLight
    return {
        DatePickerDialog(context, theme, { _, year, month, date -> onSelect(LocalDate.of(year, month + 1, date)) },
            day.year, day.monthValue - 1, day.dayOfMonth).apply {
            val zone = ZoneId.systemDefault()
            datePicker.maxDate = limit.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            datePicker.minDate = earliest.atStartOfDay(zone).toInstant().toEpochMilli()
        }.show()
    }
}
