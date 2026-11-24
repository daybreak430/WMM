package com.wmm.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmm.ledger.data.Cashflow
import com.wmm.ledger.data.currency
import kotlin.math.max

@Composable
internal fun LedgerDayHeader(label: String, total: Cashflow?) {
    val colors = MaterialTheme.colorScheme
    Layout(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp), content = {
        Text(label, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (total != null && total.income > 0) DailyAmount("收", total.income, colors.primary)
            if (total != null && total.expense > 0) DailyAmount("支", total.expense, colors.onSurfaceVariant)
        }
    }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val date = measurables[0].measure(loose)
        val amounts = measurables[1].measure(loose)
        // 先按真实文字宽度判断能否同排；不足时让整组小计落到日期下方，避免金额被日期挤窄。
        val sameLine = date.width + 16.dp.roundToPx() + amounts.width <= constraints.maxWidth
        val height = if (sameLine) max(date.height, amounts.height) else date.height + 4.dp.roundToPx() + amounts.height
        layout(constraints.maxWidth, height) {
            date.placeRelative(0, if (sameLine) (height - date.height) / 2 else 0)
            amounts.placeRelative(constraints.maxWidth - amounts.width, if (sameLine) (height - amounts.height) / 2
                else date.height + 4.dp.roundToPx())
        }
    }
}

@Composable
private fun DailyAmount(label: String, cents: Long, color: Color) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
            fontWeight = FontWeight.Normal)) { append("$label ") }
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(currency(cents)) }
    }
    Text(text, Modifier.semantics { contentDescription = "${if (label == "收") "当日收入" else "当日支出"} ${currency(cents)}" },
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp,
            fontFeatureSettings = "tnum", letterSpacing = 0.sp))
}
