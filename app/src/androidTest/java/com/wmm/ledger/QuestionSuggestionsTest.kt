package com.wmm.ledger

import com.wmm.ledger.data.LedgerError
import com.wmm.ledger.data.Provider
import com.wmm.ledger.data.QuestionSuggestions
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuestionSuggestionsTest {
    @Test fun requestOnlyContainsCapabilitiesAndPreviousGeneratedQuestions() {
        val previous = listOf("今年最大支出是哪笔？", "我记录过几次转账？", "下次定时账单在什么时候？")
        Provider.entries.forEach { provider ->
            val request = QuestionSuggestions.request(provider, previous)
            assertEquals(provider.model, request.getString("model"))
            assertEquals(1000, request.getInt("max_tokens"))
            val payload = JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content"))
            assertEquals(setOf("本次可探索的角度", "上次建议"), payload.keys().asSequence().toSet())
            val angles = payload.getJSONArray("本次可探索的角度")
            assertEquals(3, angles.length())
            assertEquals(3, (0 until angles.length()).map { angles.getString(it) }.distinct().size)
            assertEquals(previous, (0 until 3).map { payload.getJSONArray("上次建议").getString(it) })
        }
    }

    @Test fun readsThreeModelGeneratedQuestionsAndRejectsInvalidOrRepeatedGroups() {
        val reply = """{"questions":["这个月哪天花得最多？","我最近常用哪个支付账户？","我有哪些待执行的定时账单？"]}"""
        val questions = QuestionSuggestions.parse(reply, emptyList())
        assertEquals(3, questions.size)
        assertEquals("这个月哪天花得最多？", questions.first())
        listOf(
            """{"questions":["只有一个问题？"]}""",
            """{"questions":["这个月哪天花得最多？","这个月哪天花得最多？","我有哪些定时账单？"]}""",
            """{"questions":[123,"这个月哪天花得最多？","我有哪些定时账单？"]}""",
            reply + "无关内容"
        ).forEach { invalid ->
            try { QuestionSuggestions.parse(invalid, emptyList()); fail("应拒绝无效建议") }
            catch (_: LedgerError) { }
        }
        try { QuestionSuggestions.parse(reply, questions); fail("换一组不能原样复用上一组") }
        catch (_: LedgerError) { }
    }
}
