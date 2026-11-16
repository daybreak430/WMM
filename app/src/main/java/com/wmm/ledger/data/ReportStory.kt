package com.wmm.ledger.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class ReportFinding(val title: String, val body: String)

internal data class ReportStory(val headline: String, val findings: List<ReportFinding>, val nextStep: String?) {
    fun encode(): String = JSONObject().put("headline", headline)
        .put("findings", JSONArray(findings.map { JSONObject().put("title", it.title).put("body", it.body) }))
        .put("nextStep", nextStep ?: JSONObject.NULL).toString()

    companion object {
        fun parse(content: String): ReportStory? = runCatching {
            val trimmed = content.trim()
            val json = if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
                trimmed.substringAfter('\n').removeSuffix("```").trim()
            } else trimmed
            val reader = JSONTokener(json)
            val root = reader.nextValue() as JSONObject
            require(reader.nextClean() == '\u0000')
            val headline = (root.get("headline") as String).trim()
            require(headline.isNotEmpty())
            val items = root.getJSONArray("findings")
            require(items.length() <= 3)
            val findings = List(items.length()) { index ->
                val item = items.getJSONObject(index)
                val title = (item.get("title") as String).trim()
                val body = (item.get("body") as String).trim()
                require(title.isNotEmpty() && body.isNotEmpty())
                ReportFinding(title, body)
            }
            val next = (root.opt("nextStep") as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ReportStory(headline, findings, next)
        }.getOrNull()
    }
}
