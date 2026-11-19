package com.wmm.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wmm.ledger.data.LedgerAnswer
import com.wmm.ledger.data.LedgerError
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.LedgerSearchResult
import com.wmm.ledger.data.Provider
import com.wmm.ledger.data.QuestionContext
import com.wmm.ledger.data.searchLedger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class QuestionTurn(val id: Long, val question: String, val answer: LedgerAnswer? = null, val error: String? = null)

// 搜索与问答独立管理临时状态，不把问题、备注或回答写入未加密缓存和日志。
internal class LedgerAssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository.shared(application)
    private val term = MutableStateFlow("")
    val searchTerm = term.asStateFlow()
    val searchResults = combine(flow { emitAll(repository.observe()) }, term) { data, query -> data.searchLedger(query) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerSearchResult())
    private val turns = MutableStateFlow<List<QuestionTurn>>(emptyList())
    val questions = turns.asStateFlow()
    private val draft = MutableStateFlow("")
    val questionDraft = draft.asStateFlow()
    private var nextId = 0L
    private var questionJob: Job? = null

    fun search(query: String) { term.value = query.take(200) }
    fun editQuestion(question: String) { draft.value = question.take(1_000) }
    fun hasConsent(provider: Provider) = repository.secrets.hasQuestionConsent(provider)

    fun ask(question: String, provider: Provider, consent: Boolean = false) {
        if (questionJob?.isActive == true || question.isBlank()) return
        val turn = QuestionTurn(++nextId, question.trim().take(1_000))
        val history = turns.value.mapNotNull { previous -> previous.answer?.let { QuestionContext(previous.question, it.text) } }.takeLast(2)
        turns.value = (turns.value + turn).takeLast(12)
        questionJob = viewModelScope.launch {
            try {
                if (consent) withContext(Dispatchers.IO) { repository.secrets.setQuestionConsent(provider) }
                val answer = repository.askQuestion(turn.question, history, provider)
                turns.value = turns.value.map { if (it.id == turn.id) it.copy(answer = answer) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = (error as? LedgerError)?.display ?: "暂时无法回答，请稍后重试。"
                turns.value = turns.value.map { if (it.id == turn.id) it.copy(error = message) else it }
            }
        }
    }

    fun clearQuestions() {
        questionJob?.cancel()
        questionJob = null
        turns.value = emptyList()
        draft.value = ""
    }
}
