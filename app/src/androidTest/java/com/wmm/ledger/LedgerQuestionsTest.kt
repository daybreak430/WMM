package com.wmm.ledger

import com.wmm.ledger.data.*
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LedgerQuestionsTest {
    private val today = LocalDate.of(2026, 11, 19)
    private val data = LedgerSnapshot(tags = listOf(Tag("metro", "EXPENSE", "地铁")),
        scenes = listOf(Scene("commute", "通勤")), entries = listOf(
            Entry("winner", "EXPENSE", 125_050, "metro", null, "需要核对的备注", 1, "2026-05-01", "commute"),
            Entry("small", "EXPENSE", 300, "metro", null, "无关的私密备注", 2, "2026-05-02", "commute")))

    @Test fun largestExpenseOnlySendsTheSelectedDetailAndLocalTotals() {
        var calls = 0
        val answer = LedgerQuestions.answer(data, "今年最大一笔支出？", emptyList(), today, Provider.DEEPSEEK) { request ->
            calls++
            val content = request.getJSONArray("messages").getJSONObject(1).getString("content")
            if (calls == 1) {
                assertFalse(content.contains("需要核对的备注"))
                assertFalse(content.contains("无关的私密备注"))
                """{"queries":[{"source":"ENTRIES","mode":"RECORDS","filter":{"from":"2026-01-01","to":"2026-12-31","kinds":["EXPENSE"]},"limit":1}]}"""
            } else {
                val result = JSONObject(content).getJSONArray("本机查询结果").getJSONObject(0)
                assertEquals("2026-01-01", result.getJSONObject("筛选条件").getString("from"))
                assertEquals(2, result.getJSONObject("完整匹配统计").getInt("笔数"))
                assertEquals("1253.50", result.getJSONObject("完整匹配统计").getString("支出_元"))
                assertEquals(1, result.getInt("省略明细数"))
                assertEquals("1250.50", result.getJSONArray("明细").getJSONObject(0).getString("金额_元"))
                assertTrue(content.contains("需要核对的备注"))
                assertFalse(content.contains("无关的私密备注"))
                "最大一笔为 5 月 1 日的地铁支出，1250.50 元。"
            }
        }
        assertEquals(2, calls)
        assertEquals(listOf(LedgerReference(QuerySource.ENTRIES, "winner")), answer.evidence)
        assertFalse(answer.evidenceTruncated)
    }

    @Test fun groupingAndCrossPeriodComparisonUseLocallyComputedPercentages() {
        var calls = 0
        LedgerQuestions.answer(data, "分组和比较", emptyList(), today, Provider.MINIMAX) { request ->
            if (++calls == 1) """{"queries":[
                {"source":"ENTRIES","mode":"GROUPS","groupBy":["TYPE"],"sort":"COUNT"},
                {"source":"ENTRIES","mode":"SUMMARY","filter":{"from":"2026-05-01","to":"2026-05-01"}},
                {"source":"ENTRIES","mode":"SUMMARY","filter":{"from":"2026-05-02","to":"2026-05-02"}}]}"""
            else {
                val content = JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content"))
                val group = content.getJSONArray("本机查询结果").getJSONObject(0).getJSONArray("分组").getJSONObject(0)
                assertEquals("100.00%", group.getString("笔数占全部"))
                val comparison = content.getJSONArray("汇总比较").getJSONObject(0)
                assertEquals(2, comparison.getInt("基准查询"))
                assertEquals(3, comparison.getInt("对照查询"))
                assertEquals("-1247.50", comparison.getString("合计差额_元"))
                "按账本记录比较。"
            }
        }
        assertEquals(2, calls)
    }

    @Test fun unsupportedPlansAreRejectedWithoutExecutingModelCode() {
        listOf(
            """{"queries":[{"source":"SQL","mode":"RECORDS"}]}""",
            """{"queries":[{"source":"REPORTS","mode":"RECORDS"}]}""",
            """{"queries":[{"source":"ENTRIES","mode":"SUMMARY","filter":{"from":"2026-02-30"}}]}""",
            """{"queries":[{"source":"ENTRIES","mode":"SUMMARY","filter":{"minCents":1.5}}]}""",
            """{"queries":[{"source":"ACCOUNTS","mode":"SUMMARY","filter":{"from":"2026-01-01"}}]}""",
            """{"queries":[{"source":"ENTRIES","mode":"RECORDS","limit":1000}]}""",
            """{"queries":[]} SELECT * FROM entries"""
        ).forEach { plan ->
            try { LedgerQuestions.parsePlan(plan); fail("应拒绝无效查询：$plan") }
            catch (_: LedgerError) { }
        }
    }

    @Test fun clarificationDoesNotSendALedgerDetailRequest() {
        var calls = 0
        val answer = LedgerQuestions.answer(data, "我为什么这样花钱？", emptyList(), today, Provider.DEEPSEEK) {
            calls++; """{"queries":[],"clarification":"仅凭流水无法判断消费动机。"}"""
        }
        assertEquals(1, calls)
        assertTrue(answer.evidence.isEmpty())
        assertEquals("仅凭流水无法判断消费动机。", answer.text)
    }
}
