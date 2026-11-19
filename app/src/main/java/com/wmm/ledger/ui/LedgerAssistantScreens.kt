package com.wmm.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wmm.ledger.QuestionTurn
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.LedgerSearchResult
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Provider
import com.wmm.ledger.data.QuerySource
import com.wmm.ledger.data.Transfer
import java.time.LocalDate

@Composable
internal fun LedgerSearchScreen(data: LedgerSnapshot, query: String, result: LedgerSearchResult, today: LocalDate,
    change: (String) -> Unit, back: () -> Unit, edit: (Entry) -> Unit, editTransfer: (Transfer) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var requested by rememberSaveable { mutableStateOf(false) }
    fun finish() { focus.clearFocus(); keyboard?.hide() }
    LaunchedEffect(Unit) { if (!requested) { requested = true; requester.requestFocus() } }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader("搜索", back)
        OutlinedTextField(query, change, modifier = Modifier.fillMaxWidth().padding(horizontal = PagePadding)
            .focusRequester(requester).testTag("ledger-search"), singleLine = true,
            placeholder = { Text("类型、场景、账户、金额、备注") }, shape = FieldShape, colors = ledgerFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { finish() }),
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { change("") }) { LucideIcon(Glyph.CLOSE, description = "清空搜索") } })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = PagePadding, vertical = 12.dp)) {
            if (query.isNotBlank()) item("count") {
                Text("${result.entries.size + result.transfers.size} 笔", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (result.entries.isEmpty() && result.transfers.isEmpty()) item("empty") { EmptyPanel("没有找到流水") }
            ledgerRows(result.entries, data.copy(transfers = result.transfers), today,
                { finish(); edit(it) }, { finish(); editTransfer(it) })
        }
    }
}

@Composable
internal fun LedgerQuestionScreen(turns: List<QuestionTurn>, question: String, change: (String) -> Unit,
    provider: Provider, hasKey: Boolean, hasConsent: Boolean,
    back: () -> Unit, openModel: () -> Unit, submit: (String, Boolean) -> Unit, evidence: (Long) -> Unit,
    clear: () -> Unit) {
    var pendingConsent by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = turns.any { it.answer == null && it.error == null }
    val canClear = turns.isNotEmpty() || question.isNotEmpty()
    fun send(text: String, consent: Boolean = false) {
        if (text.isBlank() || busy) return
        if (!hasKey) { openModel(); return }
        if (!hasConsent && !consent) { pendingConsent = text; return }
        focus.clearFocus(); keyboard?.hide()
        submit(text.trim(), consent)
        change("")
    }
    LaunchedEffect(turns.size, turns.lastOrNull()?.answer, turns.lastOrNull()?.error) {
        if (turns.isNotEmpty()) list.animateScrollToItem(turns.lastIndex)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader("问 AI", back) {
            IconButton(onClick = { confirmClear = true }, enabled = canClear) {
                LucideIcon(Glyph.DELETE, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canClear) 1f else .38f),
                    description = "清空对话")
            }
        }
        LazyColumn(Modifier.weight(1f), state = list,
            contentPadding = PaddingValues(horizontal = PagePadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (turns.isEmpty()) item("examples") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("今年支出最大的一笔是多少？", "我最常用的通勤方式是什么？").forEach { example ->
                        TextButton(onClick = { change(example) }) { Text(example) }
                    }
                    if (!hasKey) TextButton(onClick = openModel) { Text("配置模型") }
                }
            }
            items(turns, key = { it.id }) { turn ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = FieldShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(turn.question, Modifier.padding(14.dp), style = MaterialTheme.typography.titleSmall)
                    }
                    GroupCard {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                turn.answer != null -> {
                                    Text(turn.answer.text, style = MaterialTheme.typography.bodyMedium)
                                    if (turn.answer.evidence.isNotEmpty()) TextButton(onClick = { evidence(turn.id) }) {
                                        Text(if (turn.answer.evidenceTruncated) "查看部分相关流水" else "查看相关流水")
                                    }
                                }
                                turn.error != null -> {
                                    Text(turn.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { send(turn.question) }, enabled = !busy) { Text("重试") }
                                }
                                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("正在查账…", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = PagePadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(question, { change(it.take(1_000)) }, modifier = Modifier.weight(1f).testTag("ledger-question"),
                placeholder = { Text("问问自己的账本") }, maxLines = 3, shape = FieldShape, colors = ledgerFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(question) }))
            IconButton(onClick = { send(question) }, enabled = !busy && question.isNotBlank()) {
                LucideIcon(Glyph.EXPENSE, if (!busy && question.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline, "发送问题")
            }
        }
    }
    pendingConsent?.let { pending ->
        LedgerAlertDialog(onDismissRequest = { pendingConsent = null }, title = { Text("向 ${provider.title} 提问？") },
            text = { Text("将发送问题、分类与账户名称，以及回答所需的记录、备注或汇总。API Key 仅用于鉴权，头像和昵称不发送。") },
            confirmButton = { TextButton(onClick = { pendingConsent = null; send(pending, true) }) { Text("同意并提问") } },
            dismissButton = { TextButton(onClick = { pendingConsent = null }) { Text("取消") } })
    }
    if (confirmClear) ConfirmDialog("清空当前对话？", "当前对话和未发送内容将被清除，无法恢复。", "清空",
        onDismiss = { confirmClear = false }, onConfirm = {
            confirmClear = false
            pendingConsent = null
            focus.clearFocus(); keyboard?.hide()
            clear()
        })
}

@Composable
internal fun QuestionEvidenceScreen(data: LedgerSnapshot, turn: QuestionTurn?, today: LocalDate,
    back: () -> Unit, edit: (Entry) -> Unit, editTransfer: (Transfer) -> Unit) {
    val references = turn?.answer?.evidence.orEmpty()
    val entryIds = remember(references) { references.filter { it.source == QuerySource.ENTRIES }.mapTo(hashSetOf()) { it.id } }
    val transferIds = remember(references) { references.filter { it.source == QuerySource.TRANSFERS }.mapTo(hashSetOf()) { it.id } }
    val entries = remember(data.entries, entryIds) { data.entries.filter { it.id in entryIds } }
    val transfers = remember(data.transfers, transferIds) { data.transfers.filter { it.id in transferIds } }
    Column(Modifier.fillMaxSize()) {
        PageHeader("相关流水", back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = PagePadding, vertical = 8.dp)) {
            if (entries.isEmpty() && transfers.isEmpty()) item("empty") { EmptyPanel("暂无相关流水") }
            ledgerRows(entries, data.copy(transfers = transfers), today, edit, editTransfer)
        }
    }
}
