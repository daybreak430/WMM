package com.wmm.ledger

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// 固定合成账本和操作序列，仅记录帧耗时，不采集账本内容；供同机前后对照复测。
class MotionPerformanceTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun measureLongLedgerScrollAndStateChanges() {
        val today = LocalDate.now()
        val tags = (0..11).map { Tag("perf-tag-$it", Kind.EXPENSE.name, "类型$it", sortOrder = it) }
        val scenes = (0..3).map { Scene("perf-scene-$it", "场景$it", it) }
        val entries = (0 until 1200).map { index ->
            val day = today.minusDays((index / 20).toLong())
            Entry("perf-entry-$index", Kind.EXPENSE.name, (index + 1) * 137L, tags[index % tags.size].id,
                null, "测试流水$index", day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + index,
                day.toString(), scenes[index % scenes.size].id)
        }
        withLedger(LedgerSnapshot(tags = tags, scenes = scenes, entries = entries)) { _, activity ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            // 先暖机，排除安装后首次着色器与字体初始化。
            ui.onNodeWithText("统计").performClick()
            ui.onNodeWithText("账本").performClick()
            val frames = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
            val worker = HandlerThread("动效帧采样").apply { start() }
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                    frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION) to
                        metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
                }
            }
            activity.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
            try {
                repeat(8) { ui.onNode(hasScrollAction()).performTouchInput { swipeUp(durationMillis = 300) } }
                repeat(4) {
                    ui.onNodeWithText("统计").performClick()
                    listOf("周", "年", "月").forEach { ui.onNodeWithText(it).performClick() }
                    ui.onNodeWithText("账本").performClick()
                    ui.onNodeWithContentDescription("记一笔").performClick()
                    ui.onNodeWithText("全部类型").performClick()
                    ui.onNodeWithText("收起类型").performClick()
                    ui.onNodeWithContentDescription("关闭").performClick()
                }
            } finally {
                activity.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                worker.quitSafely()
                worker.join(2000)
            }
            val samples = synchronized(frames) { frames.toList() }
            assertTrue("未获得足够的帧样本", samples.size > 60)
            val total = samples.map { it.first / 1_000_000.0 }.sorted()
            val layout = samples.map { it.second / 1_000_000.0 }.sorted()
            fun percentile(values: List<Double>, fraction: Double) = values[((values.size - 1) * fraction).toInt()]
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val report = JSONObject().apply {
                put("version", context.packageManager.getPackageInfo(context.packageName, 0).versionName); put("frames", samples.size); put("entries", entries.size)
                put("totalP50Ms", percentile(total, .5)); put("totalP95Ms", percentile(total, .95))
                put("layoutP50Ms", percentile(layout, .5)); put("layoutP95Ms", percentile(layout, .95))
                put("over16_67Ms", total.count { it > 16.67 }); put("over33_33Ms", total.count { it > 33.33 })
            }
            File(context.getExternalFilesDir(null), "motion-performance.json").writeText(report.toString(2))
            android.util.Log.i("WmmMotionPerformance", report.toString())
        }
    }
}
