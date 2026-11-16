package com.wmm.ledger.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wmm.ledger.R

// 使用 Lucide 官方 SVG 转成的 Android 矢量资源，保留原始笔画宽度、端点和转角。
enum class Glyph(@param:DrawableRes @get:DrawableRes val drawable: Int) {
    BOOK(R.drawable.lucide_notebook_pen),
    CHART(R.drawable.lucide_chart_no_axes_column_increasing),
    WALLET(R.drawable.lucide_wallet),
    PERSON(R.drawable.lucide_user_round),
    PLUS(R.drawable.lucide_plus),
    BACK(R.drawable.lucide_arrow_left),
    LEFT(R.drawable.lucide_chevron_left),
    RIGHT(R.drawable.lucide_chevron_right),
    TAG(R.drawable.lucide_tag),
    INCOME(R.drawable.lucide_arrow_down_left),
    EXPENSE(R.drawable.lucide_arrow_up_right),
    CALENDAR(R.drawable.lucide_calendar_days),
    SPARK(R.drawable.lucide_sparkles),
    BOX(R.drawable.lucide_archive),
    EDIT(R.drawable.lucide_pencil),
    DELETE(R.drawable.lucide_trash),
    CLOSE(R.drawable.lucide_x),
    DOWNLOAD(R.drawable.lucide_download),
    UPLOAD(R.drawable.lucide_upload),
}

@Composable
fun LucideIcon(glyph: Glyph, color: Color = MaterialTheme.colorScheme.onSurface,
    description: String? = null, size: Int = 22) {
    Icon(
        painter = painterResource(glyph.drawable),
        contentDescription = description,
        modifier = Modifier.size(size.dp),
        tint = color,
    )
}

@Composable
fun IconBadge(glyph: Glyph, emphasis: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(13.dp),
        color = if (emphasis) colors.primaryContainer else colors.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            LucideIcon(glyph, if (emphasis) colors.primary else colors.onSurfaceVariant, size = 19)
        }
    }
}
