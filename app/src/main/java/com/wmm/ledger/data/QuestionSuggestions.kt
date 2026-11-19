package com.wmm.ledger.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object QuestionSuggestions {
    // 只发送产品能力与上次的通用建议，不读取账本快照；随机角度由模型写成具体问题。
    fun request(provider: Provider, previous: List<String>): JSONObject {
        val angles = listOf("金额极值", "消费频次", "类型分布", "场景差异", "时间变化", "支付账户", "信用欠款", "定时账单")
            .shuffled().take(3)
        return ModelProtocol.chatRequest(provider, """
            为个人记账应用的「问 AI」首页生成三条可以直接提问的中文问题，面向用户自己的账本。
            自主选取具体角度，表达自然、有用，每条不超过 40 字。三条问题应有明显区别，和上次建议也不同。
            可查询范围：收入与支出流水的日期、类型、场景、账户、金额和备注；账户间转账；当前账户余额、信用欠款；定时账单的周期和下次日期。
            可按条件筛选、笔数或金额排名、分组、求和、平均、比较期间。不要要求未记录的心理动机、历史资产、预算、投资建议或 AI 周月年报。
            不知道用户是否有某类记录，不预设用户有具体商户、场景或消费习惯。不先下结论，不生成回答，不要求用户提供产品之外的数据。
            仅输出 JSON：{"questions":["问题一？","问题二？","问题三？"]}，不使用 Markdown。
        """.trimIndent(), JSONObject().put("本次可探索的角度", JSONArray(angles))
            .put("上次建议", JSONArray(previous.take(3))).toString(), json = true, maxTokens = 1_000)
    }

    fun parse(reply: String, previous: List<String>): List<String> = try {
        val clean = reply.trim().let { if (it.startsWith("```")) it.substringAfter('\n').removeSuffix("```").trim() else it }
        val reader = JSONTokener(clean)
        val root = reader.nextValue() as JSONObject
        require(reader.nextClean() == '\u0000')
        val array = root.getJSONArray("questions")
        require(array.length() == 3)
        val questions = (0 until array.length()).map { index ->
            (array.get(index) as String).trim().also {
                require(it.length in 4..60 && '\n' !in it && '\r' !in it)
            }
        }
        val normalized = questions.map(::normalizeLedgerText)
        require(normalized.distinct().size == 3)
        require(normalized.none { it in previous.map(::normalizeLedgerText) })
        questions
    } catch (_: Exception) {
        throw LedgerError("建议问题未生成，请重试。")
    }
}
