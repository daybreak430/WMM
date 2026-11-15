package com.wmm.ledger.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmm.ledger.data.FIRST_DAY
import com.wmm.ledger.data.Profile
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val PagePadding = 20.dp
val CardShape = RoundedCornerShape(23.dp)
val DateLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.SIMPLIFIED_CHINESE)
val TimeLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun PageHeader(title: String, back: (() -> Unit)? = null, end: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(62.dp).padding(start = if (back == null) PagePadding else 8.dp, end = PagePadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconButton(onClick = back) { LineIcon(Glyph.BACK, description = "返回") }
        Text(title, Modifier.weight(1f), style = if (back == null) MaterialTheme.typography.headlineLarge
            else MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        end()
    }
}

@Composable
fun UserAvatar(profile: Profile, onClick: () -> Unit, size: Int = 44, description: String = "个人设置") {
    val image = remember(profile.avatar) {
        profile.avatar?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (image == null) LineIcon(Glyph.PERSON, colors.onPrimaryContainer, description)
            else Image(image, description, Modifier.size(size.dp), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun InkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = Cover, contentColor = CoverInk) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                // 低对比度的同心线让账本封面在大块纯色中留有一点质感。
                for (ring in 0..5) {
                    drawCircle(Lime.copy(alpha = 0.075f), size.width * (0.20f + ring * 0.055f),
                        Offset(size.width * .97f, size.height * .76f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
        }
    }
}

@Composable
fun AmountText(amount: String, color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier, style: TextStyle = RowNumber) {
    val displayStyle = if (amount.length > 13 && style == HeroNumber) style.copy(fontSize = 28.sp, lineHeight = 38.sp) else style
    Text(amount, modifier, style = displayStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, end: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        end()
    }
}

@Composable
fun EmptyPanel(title: String, detail: String? = null, glyph: Glyph = Glyph.BOOK) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 29.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconBadge(glyph)
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun SettingsRow(title: String, description: String? = null, icon: Glyph? = null, enabled: Boolean = true,
    end: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) IconBadge(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (end != null) Text(end, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(.45f, fill = false))
        LineIcon(Glyph.RIGHT, colors.onSurfaceVariant, size = 17)
    }
}

@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
fun Hairline(inset: Int = 0) = HorizontalDivider(
    Modifier.padding(horizontal = inset.dp), color = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
fun ConfirmDialog(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun CalendarAction(day: LocalDate, limit: LocalDate = LocalDate.now(), onSelect: (LocalDate) -> Unit): () -> Unit {
    val context = LocalContext.current
    return {
        DatePickerDialog(context, { _, year, month, date -> onSelect(LocalDate.of(year, month + 1, date)) },
            day.year, day.monthValue - 1, day.dayOfMonth).apply {
            val zone = ZoneId.systemDefault()
            datePicker.minDate = FIRST_DAY.atStartOfDay(zone).toInstant().toEpochMilli()
            datePicker.maxDate = limit.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        }.show()
    }
}

@Composable
fun TimeAction(time: LocalTime, onSelect: (LocalTime) -> Unit): () -> Unit {
    val context = LocalContext.current
    return {
        TimePickerDialog(context, { _, hour, minute -> onSelect(LocalTime.of(hour, minute)) },
            time.hour, time.minute, true).show()
    }
}
