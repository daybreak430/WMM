package com.wmm.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wmm.ledger.data.Period
import com.wmm.ledger.ui.AssetsScreen
import com.wmm.ledger.ui.ConfirmDialog
import com.wmm.ledger.ui.EmptyPanel
import com.wmm.ledger.ui.EntryEditor
import com.wmm.ledger.ui.Glyph
import com.wmm.ledger.ui.LedgerScreen
import com.wmm.ledger.ui.LedgerTheme
import com.wmm.ledger.ui.LineIcon
import com.wmm.ledger.ui.ModelSettings
import com.wmm.ledger.ui.SettingsScreen
import com.wmm.ledger.ui.StatsScreen
import com.wmm.ledger.ui.TagDrillScreen
import com.wmm.ledger.ui.TagsScreen
import java.time.LocalDate
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LedgerTheme { LedgerApp(viewModel()) } }
    }
}

private enum class Tab(val label: String, val icon: Glyph) {
    LEDGER("账本", Glyph.BOOK), STATS("统计", Glyph.CHART), ASSETS("资产", Glyph.WALLET)
}

@Composable
private fun LedgerApp(model: LedgerViewModel) {
    val snapshot by model.data.collectAsStateWithLifecycle()
    val openError by model.unableToOpen.collectAsStateWithLifecycle()
    val provider by model.provider.collectAsStateWithLifecycle()
    val keys by model.providersWithKeys.collectAsStateWithLifecycle()
    val generating by model.generating.collectAsStateWithLifecycle()
    val pendingRestore by model.pendingRestore.collectAsStateWithLifecycle()
    val fileBusy by model.fileBusy.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(Tab.LEDGER.name) }
    val tab = Tab.valueOf(tabName)
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var parent by rememberSaveable { mutableStateOf<String?>(null) }
    var periodName by rememberSaveable { mutableStateOf(Period.MONTH.name) }
    var dayText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var followToday by rememberSaveable { mutableStateOf(true) }
    val today by produceState(LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(15_000)
        }
    }
    val period = Period.valueOf(periodName)
    val day = if (followToday) today else LocalDate.parse(dayText).coerceAtMost(today)
    val navState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model) { model.feedback.collect { snackbar.showSnackbar(it, withDismissAction = true) } }

    fun back() {
        if (parent != null) {
            route = parent
            parent = null
        } else if (route != null) route = null else tabName = Tab.LEDGER.name
    }

    BackHandler(enabled = route != null || tab != Tab.LEDGER, onBack = ::back)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route == null && snapshot != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(selected = tab == item, onClick = { tabName = item.name },
                            icon = { LineIcon(item.icon, if (tab == item) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant) }, label = { Text(item.label) })
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val data = snapshot
            val current = route
            when {
                openError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyPanel("无法打开账本", "请重新打开应用。若仍无法打开，请保留现有应用数据。")
                }
                data == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                current == "settings" -> SettingsScreen(data, model, provider, provider in keys, fileBusy, ::back,
                    tags = { parent = current; route = "tags" }, openModel = { parent = current; route = "model" })
                current == "tags" -> TagsScreen(data, model, ::back)
                current == "model" -> ModelSettings(model, provider, keys, ::back)
                current == "new" -> EntryEditor(data, null, model) { if (route == current) back() }
                current?.startsWith("entry:") == true -> {
                    val entry = data.entries.firstOrNull { it.id == current.substringAfter(':') }
                    if (entry != null) {
                        EntryEditor(data, entry, model) { if (route == current) back() }
                    } else {
                        LaunchedEffect(current) { if (route == current) back() }
                    }
                }
                current?.startsWith("tag:") == true -> TagDrillScreen(data, today, period, day, current.substringAfter(':'), ::back) {
                    parent = current
                    route = "entry:${it.id}"
                }
                else -> navState.SaveableStateProvider(tab.name) {
                    when (tab) {
                        Tab.LEDGER -> LedgerScreen(data, today, { route = "settings" }, {
                            dayText = today.toString()
                            followToday = true
                            periodName = Period.MONTH.name
                            tabName = Tab.STATS.name
                        }, { route = "new" }, { route = "entry:${it.id}" })
                        Tab.STATS -> StatsScreen(data, today, period, day, { periodName = it.name }, {
                            dayText = it.toString()
                            followToday = it == today
                        }, { route = "tag:$it" }, generating, provider in keys, model.hasReportConsent(),
                            { agreed -> model.generate(period, day, agreed) }, { route = "model" })
                        Tab.ASSETS -> AssetsScreen(data, model)
                    }
                }
            }
        }
    }

    pendingRestore?.let { backup ->
        ConfirmDialog("替换本机账本？", "此备份含 ${backup.entries.size} 笔流水和 ${backup.accounts.size} 个账户。" +
            "恢复会替换本机的流水、标签、资产、资料和报告，无法撤销。", "替换并恢复",
            model::cancelRestore, model::restore)
    }
}
