package com.wmm.ledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.R
import com.wmm.ledger.data.IconColor
import com.wmm.ledger.data.LabelIcon
import com.wmm.ledger.data.LabelStyle
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.StyleLabel
import com.wmm.ledger.data.styleLabels

private val LabelIcon.drawable: Int
    get() = when (this) {
        LabelIcon.TAG -> R.drawable.lucide_tag
        LabelIcon.MEAL -> R.drawable.lucide_utensils
        LabelIcon.COFFEE -> R.drawable.lucide_coffee
        LabelIcon.TRAIN -> R.drawable.lucide_train_front
        LabelIcon.CAR -> R.drawable.lucide_car_front
        LabelIcon.PLANE -> R.drawable.lucide_plane
        LabelIcon.BED -> R.drawable.lucide_bed_double
        LabelIcon.SHOP -> R.drawable.lucide_shopping_bag
        LabelIcon.GIFT -> R.drawable.lucide_gift
        LabelIcon.HEALTH -> R.drawable.lucide_heart_pulse
        LabelIcon.HOME -> R.drawable.lucide_house
        LabelIcon.WORK -> R.drawable.lucide_briefcase_business
        LabelIcon.MONEY -> R.drawable.lucide_banknote
        LabelIcon.TICKET -> R.drawable.lucide_ticket
        LabelIcon.STUDY -> R.drawable.lucide_graduation_cap
        LabelIcon.SPORT -> R.drawable.lucide_dumbbell
        LabelIcon.TRAVEL -> R.drawable.lucide_map_pin
        LabelIcon.GAME -> R.drawable.lucide_gamepad_2
        LabelIcon.PHONE -> R.drawable.lucide_smartphone
    }

internal data class LabelColors(val ink: Color, val fill: Color)

@Composable
internal fun IconColor.labelColors(): LabelColors {
    val colors = MaterialTheme.colorScheme
    val dark = LocalLedgerDark.current
    if (dark) {
        val ink = when (this) {
            IconColor.BLUE -> colors.primary
            IconColor.AZURE -> Color(0xFF83D8FF)
            IconColor.INDIGO -> Color(0xFFB6BEFF)
            IconColor.VIOLET -> Color(0xFFD0B3FF)
            IconColor.ROSE -> Color(0xFFFFB0D4)
            IconColor.RED -> Color(0xFFFFB4AC)
            IconColor.AMBER -> Color(0xFFFFC77E)
            IconColor.SLATE -> Color(0xFFB4CAE6)
        }
        return LabelColors(ink, lerp(colors.surface, ink, .14f))
    }
    return when (this) {
        IconColor.BLUE -> LabelColors(colors.primary, Color(0xFFEBF4FF))
        IconColor.AZURE -> LabelColors(Color(0xFF00739F), Color(0xFFEAF8FF))
        IconColor.INDIGO -> LabelColors(Color(0xFF4C54BF), Color(0xFFEEF0FF))
        IconColor.VIOLET -> LabelColors(Color(0xFF7843B5), Color(0xFFF5EDFF))
        IconColor.ROSE -> LabelColors(Color(0xFFA83473), Color(0xFFFDEEF7))
        IconColor.RED -> LabelColors(Color(0xFFB83239), Color(0xFFFFEEEF))
        IconColor.AMBER -> LabelColors(Color(0xFFA75208), Color(0xFFFFF3DD))
        IconColor.SLATE -> LabelColors(Color(0xFF45668F), Color(0xFFEDF4FD))
    }
}

@Composable
internal fun LabelBadge(style: LabelStyle?, size: Int = 38) {
    val colors = MaterialTheme.colorScheme
    val palette = style?.color?.labelColors()
    Box(Modifier.size(size.dp)
        .background(palette?.fill ?: colors.surfaceVariant, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center) {
        val iconSize = if (size <= 36) 17 else 19
        if (style == null || palette == null) LucideIcon(Glyph.TAG, colors.onSurfaceVariant, size = iconSize)
        else LabelGlyph(style, iconSize, palette.ink)
    }
}

@Composable
internal fun LabelGlyph(style: LabelStyle, size: Int, tint: Color) {
    Icon(painterResource(style.icon.drawable), contentDescription = null,
        tint = tint, modifier = Modifier.size(size.dp))
}

@Composable
internal fun LabelStylesScreen(data: LedgerSnapshot, model: LedgerViewModel, back: () -> Unit) {
    val preview by model.iconPreview.collectAsStateWithLifecycle()
    val busy by model.iconBusy.collectAsStateWithLifecycle()
    val provider by model.provider.collectAsStateWithLifecycle()
    val keys by model.providersWithKeys.collectAsStateWithLifecycle()
    val labels = remember(data.tags, data.scenes) { data.styleLabels() }
    val sections = listOf("支出", "收入", "场景")
    var selected by rememberSaveable { mutableStateOf(sections.first()) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var consent by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize()) {
        PageHeader("图标与颜色", back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(PagePadding, 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                PrimaryAction(
                    text = if (busy) "正在准备预览" else "AI 一键配图标",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && labels.any { it.style == null },
                    onClick = { if (provider in keys) consent = true else model.suggestIcons(false) },
                    glyph = Glyph.SPARK,
                )
            }
            item { LedgerSegments(sections, selected, { it }) { selected = it } }
            val visible = labels.filter { it.section == selected }
            if (visible.isEmpty()) item { GroupCard { EmptyPanel("还没有${if (selected == "场景") "场景" else "${selected}类型"}") } }
            items(visible, key = { it.id }) { label ->
                GroupCard {
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { editing = label.id }
                        .padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        LabelBadge(label.style)
                        Text(label.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        LucideIcon(Glyph.RIGHT, MaterialTheme.colorScheme.onSurfaceVariant, size = 16)
                    }
                }
            }
        }
    }
    if (consent) {
        LedgerAlertDialog(onDismissRequest = { consent = false }, title = { Text("让 AI 推荐图标？") },
            text = { Text("将待配图标的标签名称和方向发给 ${provider.title}。") },
            confirmButton = { TextButton(onClick = { consent = false; model.suggestIcons(true) }) { Text("生成预览") } },
            dismissButton = {
                TextButton(onClick = { consent = false; model.suggestIcons(false) }) { Text("本机推荐") }
                TextButton(onClick = { consent = false }) { Text("取消") }
            })
    }
    preview?.let { pending ->
        LedgerAlertDialog(onDismissRequest = model::discardIconPreview,
            title = { Text(if (pending.usedModel) "图标预览" else "本机推荐预览") }, text = {
                LazyColumn(Modifier.heightIn(max = 330.dp)) {
                    items(pending.changes, key = { "${it.target.section}:${it.target.id}" }) { change ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            LabelBadge(change.style, 35)
                            Column(Modifier.weight(1f)) {
                                Text(change.target.name, style = MaterialTheme.typography.titleSmall)
                                Text(change.target.section, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }, confirmButton = {
                TextButton(onClick = model::applyIconPreview, enabled = !busy && pending.changes.isNotEmpty()) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text("应用 ${pending.changes.size} 个")
                }
            }, dismissButton = { TextButton(onClick = model::discardIconPreview, enabled = !busy) { Text("取消") } })
    }
    labels.firstOrNull { it.id == editing && it.section == selected }?.let { label ->
        LabelStyleDialog(label, dismiss = { editing = null }) { style ->
            model.saveLabelIcon(label, style) { editing = null }
        }
    }
}

@Composable
private fun LabelStyleDialog(label: StyleLabel, dismiss: () -> Unit, save: (LabelStyle?) -> Unit) {
    var icon by rememberSaveable { mutableStateOf(label.style?.icon ?: LabelIcon.TAG) }
    var color by rememberSaveable { mutableStateOf(label.style?.color ?: IconColor.BLUE) }
    val colors = MaterialTheme.colorScheme
    val selected = LabelStyle(icon, color)
    LedgerAlertDialog(onDismissRequest = dismiss, title = { Text(label.name) }, text = {
        LazyColumn(Modifier.heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabelBadge(selected)
                    Text("${icon.title} · ${color.title}", style = MaterialTheme.typography.titleSmall)
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconColor.entries.forEach { candidate ->
                        val active = color == candidate
                        Box(Modifier.size(33.dp).clip(CircleShape)
                            .background(if (active) colors.surfaceContainerHighest else Color.Transparent)
                            .selectable(active, interactionSource = null, indication = null, role = Role.RadioButton,
                                onClick = { color = candidate })
                            .semantics { contentDescription = candidate.title },
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(16.dp).background(candidate.labelColors().ink, CircleShape))
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    LabelIcon.entries.forEach { candidate ->
                        val active = icon == candidate
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (active) colors.primaryContainer else colors.surfaceVariant)
                            .selectable(active, interactionSource = null, indication = null, role = Role.RadioButton,
                                onClick = { icon = candidate }),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(candidate.drawable), contentDescription = candidate.title,
                                tint = if (active) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { save(selected) }) { Text("保存") } },
        dismissButton = {
            if (label.style != null) TextButton(onClick = { save(null) }) { Text("清除图标") }
            TextButton(onClick = dismiss) { Text("取消") }
        })
}
