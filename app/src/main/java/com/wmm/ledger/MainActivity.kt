package com.wmm.ledger

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.wmm.ledger.data.Period
import com.wmm.ledger.ui.AssetsScreen
import com.wmm.ledger.ui.BillImportScreen
import com.wmm.ledger.ui.ConfirmDialog
import com.wmm.ledger.ui.EmptyPanel
import com.wmm.ledger.ui.EntryEditor
import com.wmm.ledger.ui.Glyph
import com.wmm.ledger.ui.LedgerNavigation
import com.wmm.ledger.ui.LedgerScreen
import com.wmm.ledger.ui.LedgerSearchScreen
import com.wmm.ledger.ui.LedgerQuestionScreen
import com.wmm.ledger.ui.QuestionEvidenceScreen
import com.wmm.ledger.ui.LedgerScreenFrame
import com.wmm.ledger.ui.LedgerScreenTheme
import com.wmm.ledger.ui.LedgerTheme
import com.wmm.ledger.ui.LucideIcon
import com.wmm.ledger.ui.ModelSettings
import com.wmm.ledger.ui.NavigationBottomGap
import com.wmm.ledger.ui.NavigationTopGap
import com.wmm.ledger.ui.SettingsScreen
import com.wmm.ledger.ui.TransferEditor
import com.wmm.ledger.ui.ScheduledBillsScreen
import com.wmm.ledger.ui.ScheduledBillEditor
import com.wmm.ledger.ui.StatsScreen
import com.wmm.ledger.ui.Tab
import com.wmm.ledger.ui.TagDrillScreen
import com.wmm.ledger.ui.TagsScreen
import com.wmm.ledger.ui.blockExitingPage
import com.wmm.ledger.ui.entryDrawerEnterMotion
import com.wmm.ledger.ui.entryDrawerExitMotion
import com.wmm.ledger.ui.ledgerNavigationHeight
import com.wmm.ledger.ui.pageMotion
import java.time.LocalDate
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: LedgerViewModel = viewModel()
            val appearance by model.appearance.collectAsStateWithLifecycle()
            val dark = when (appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(0, 0) { dark },
                    navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B273A.toInt()) { dark },
                )
            }
            LedgerTheme(dark) { LedgerApp(model, appearance) }
        }
    }
}

private data class Destination(val route: String?, val depth: Int)

@Composable
private fun LedgerApp(model: LedgerViewModel, appearance: Appearance) {
    val assistant: LedgerAssistantViewModel = viewModel()
    val questionTurns by assistant.questions.collectAsStateWithLifecycle()
    val snapshot by model.data.collectAsStateWithLifecycle()
    val openError by model.unableToOpen.collectAsStateWithLifecycle()
    val provider by model.provider.collectAsStateWithLifecycle()
    val keys by model.providersWithKeys.collectAsStateWithLifecycle()
    val pendingRestore by model.pendingRestore.collectAsStateWithLifecycle()
    val fileBusy by model.fileBusy.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(Tab.LEDGER.name) }
    val tab = if (tabName == Tab.STATS.name) Tab.STATS else Tab.LEDGER
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
    val period = Period.valueOf(periodName).let { if (it == Period.DAY) Period.WEEK else it }
    val day = if (followToday) today else LocalDate.parse(dayText).coerceAtMost(today)
    val navState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.sharedBackups.collect { uri ->
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "记个账备份", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(share, null))
                } catch (_: ActivityNotFoundException) {
                    snackbar.showSnackbar("此设备无法打开系统分享面板。")
                }
            }
        }
    }
    LaunchedEffect(model) { model.feedback.collect { snackbar.showSnackbar(it, withDismissAction = true) } }
    LaunchedEffect(model, today, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refreshScheduledBills() }
    }

    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun back() {
        if (route != null) {
            focus.clearFocus(force = true)
            keyboard?.hide()
            val returnTo = parent
            route = returnTo
            parent = when {
                returnTo == "bill-import" || returnTo == "scheduled-bills" -> "settings"
                returnTo?.startsWith("ask-evidence:") == true -> "ask"
                else -> null
            }
        } else {
            tabName = Tab.LEDGER.name
        }
    }

    BackHandler(enabled = route != null || tab != Tab.LEDGER, onBack = ::back)

    val depth = when {
        route == null -> 0
        parent == null -> 1
        parent == "scheduled-bills" -> 3
        else -> 2
    }
    val destination = Destination(route, depth)
    val entryEditor = route == "new" || route?.startsWith("entry:") == true || route?.startsWith("transfer:") == true
    val refined = entryEditor || (route == null && tab == Tab.LEDGER)
    LaunchedEffect(route) {
        // 连续记账时移除上一页的提示，避免成功提示挡住新一笔的操作。
        if (entryEditor) snackbar.currentSnackbarData?.dismiss()
    }
    val tabHeight = ledgerNavigationHeight()
    val tabSpace = tabHeight + NavigationBottomGap + NavigationTopGap
    LedgerScreenFrame(refined, tab.name) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            snackbarHost = {
                if (!entryEditor) LedgerFeedback(snackbar,
                    Modifier.navigationBarsPadding().padding(bottom = if (route == null) tabSpace else 0.dp))
            },
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).clipToBounds()
            val data = snapshot
            when {
                openError -> Box(modifier, contentAlignment = Alignment.Center) {
                    EmptyPanel("无法打开账本", "请重新打开应用。若仍无法打开，请保留现有应用数据。")
                }
                data == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> AnimatedContent(
                    targetState = destination,
                    contentKey = { it.route },
                    modifier = modifier,
                    transitionSpec = {
                        when {
                            initialState.route == null && targetState.route == "new" -> entryDrawerEnterMotion()
                            initialState.route == "new" && targetState.route == null -> entryDrawerExitMotion()
                            else -> pageMotion(if (targetState.depth >= initialState.depth) 1 else -1)
                        }
                    },
                    label = "详情页转场",
                ) { screen ->
                    val current = screen.route
                    val active = screen == destination
                    if (current == null) {
                        val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        val bottomInset = tabSpace + systemBottom
                        val background = MaterialTheme.colorScheme.background
                        val backdrop = rememberLayerBackdrop(onDraw = remember(background) {
                            {
                                // 先铺背景再录制列表，透明间隙里的文字也能完整参与折射。
                                drawRect(background)
                                drawContent()
                            }
                        })
                        val capture = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.layerBackdrop(backdrop)
                        } else Modifier
                        Box(Modifier.fillMaxSize().blockExitingPage(active)) {
                            Box(Modifier.matchParentSize().then(capture)) {
                                LedgerScreenTheme(tab == Tab.LEDGER) {
                                    navState.SaveableStateProvider(tab.name) {
                                        when (tab) {
                                            Tab.LEDGER -> LedgerScreen(data, today, { route = "settings" }, {
                                                dayText = today.toString()
                                                followToday = true
                                                periodName = Period.MONTH.name
                                                tabName = Tab.STATS.name
                                            }, { route = "entry:${it.id}" }, bottomInset,
                                                editTransfer = { route = "transfer:${it.id}" },
                                                search = { parent = null; route = "search" },
                                                ask = { parent = null; route = "ask" })
                                            Tab.STATS -> StatsScreen(data, today, period, day, { periodName = it.name }, {
                                                dayText = it.toString()
                                                followToday = it == today
                                            }, { route = "tag:$it" }, { route = "scene:$it" }, bottomInset)
                                        }
                                    }
                                }
                            }
                            LedgerNavigation(
                                tab, backdrop, tabHeight,
                                Modifier.align(Alignment.BottomCenter).padding(
                                    start = 22.dp, end = 22.dp, bottom = systemBottom + NavigationBottomGap,
                                ),
                                onRecord = { if (active) route = "new" },
                            ) { if (active) tabName = it.name }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().navigationBarsPadding().blockExitingPage(active)) {
                            LedgerScreenTheme(current == "new" || current.startsWith("entry:") || current.startsWith("transfer:")) {
                                when {
                                    current == "settings" -> SettingsScreen(data, model, provider, provider in keys,
                                        fileBusy, appearance, ::back,
                                        assets = { parent = current; route = "assets" },
                                        tags = { parent = current; route = "tags" },
                                        openModel = { parent = current; route = "model" },
                                        importBill = { parent = current; route = "bill-import" },
                                        scheduledBills = { parent = current; route = "scheduled-bills" })
                                    current == "scheduled-bills" -> ScheduledBillsScreen(data, ::back) { id ->
                                        parent = current
                                        route = "scheduled-bill:${id.orEmpty()}"
                                    }
                                    current.startsWith("scheduled-bill:") -> {
                                        val id = current.substringAfter(':')
                                        val bill = remember(current) { data.scheduledBills.firstOrNull { it.id == id } }
                                        if (id.isEmpty() || bill != null) {
                                            ScheduledBillEditor(data, bill, model, today) { if (route == current) back() }
                                        } else {
                                            LaunchedEffect(current) { if (route == current) back() }
                                        }
                                    }
                                    current == "search" -> navState.SaveableStateProvider("search") {
                                        val term by assistant.searchTerm.collectAsStateWithLifecycle()
                                        val result by assistant.searchResults.collectAsStateWithLifecycle()
                                        LedgerSearchScreen(data, term, result, today, assistant::search, ::back,
                                            edit = { parent = current; route = "entry:${it.id}" },
                                            editTransfer = { parent = current; route = "transfer:${it.id}" })
                                    }
                                    current == "ask" -> navState.SaveableStateProvider("ask") {
                                        val question by assistant.questionDraft.collectAsStateWithLifecycle()
                                        val suggestions by assistant.suggestions.collectAsStateWithLifecycle()
                                        LedgerQuestionScreen(questionTurns, question, assistant::editQuestion,
                                            provider, provider in keys, assistant.hasConsent(provider),
                                            ::back, openModel = { parent = current; route = "model" },
                                            submit = { question, consent -> assistant.ask(question, provider, consent) },
                                            evidence = { parent = current; route = "ask-evidence:$it" }, clear = assistant::clearQuestions,
                                            suggestions = suggestions, refreshSuggestions = { assistant.suggest(provider, it) })
                                    }
                                    current.startsWith("ask-evidence:") -> {
                                        val turn = questionTurns.firstOrNull { it.id == current.substringAfter(':').toLongOrNull() }
                                        QuestionEvidenceScreen(data, turn, today, ::back,
                                            edit = { parent = current; route = "entry:${it.id}" },
                                            editTransfer = { parent = current; route = "transfer:${it.id}" })
                                    }
                                    current == "assets" -> AssetsScreen(data, model, ::back)
                                    current == "tags" -> TagsScreen(data, model, ::back)
                                    current == "model" -> ModelSettings(model, provider, keys, ::back)
                                    current == "bill-import" -> BillImportScreen(data, model, provider, provider in keys, ::back) {
                                        parent = current
                                        route = "model"
                                    }
                                    current == "new" -> EntryEditor(data, null, model) { if (route == current) back() }
                                    current.startsWith("transfer:") -> {
                                        val initial = remember(current) {
                                            data.transfers.firstOrNull { it.id == current.substringAfter(':') }
                                        }
                                        if (initial != null) {
                                            TransferEditor(data, initial, model, back = { if (route == current) back() })
                                        } else {
                                            LaunchedEffect(current) { if (route == current) back() }
                                        }
                                    }
                                    current.startsWith("entry:") -> {
                                        // 离场动画仍使用打开时的流水，删除记录后画面不会突然变空。
                                        val initial = remember(current) {
                                            data.entries.firstOrNull { it.id == current.substringAfter(':') }
                                        }
                                        if (initial != null) {
                                            EntryEditor(data, initial, model) { if (route == current) back() }
                                        } else {
                                            LaunchedEffect(current) { if (route == current) back() }
                                        }
                                    }
                                    current.startsWith("tag:") || current.startsWith("scene:") -> {
                                        TagDrillScreen(data, today, period, day, current.substringAfter(':'),
                                            scene = current.startsWith("scene:"), back = ::back) {
                                            parent = current
                                            route = "entry:${it.id}"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (entryEditor) {
            // 记账页的新提示放在顶部，数字区与保存键始终保留完整触控区域。
            Box(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()),
                contentAlignment = Alignment.TopCenter) {
                LedgerFeedback(snackbar)
            }
        }
    }

    pendingRestore?.let { backup ->
        ConfirmDialog("替换本机账本？", "此备份含 ${backup.entries.size} 笔收支、${backup.transfers.size} 笔转账、${backup.accounts.size} 个账户和 " +
            "${backup.scheduledBills.size} 条定时账单。恢复会替换本机账本和定时账单，无法撤销。", "替换并恢复",
            model::cancelRestore, { assistant.clearQuestions(); model.restore() })
    }
}

@Composable
private fun LedgerFeedback(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), dismissAction = {
            IconButton(onClick = data::dismiss) {
                LucideIcon(Glyph.CLOSE, MaterialTheme.colorScheme.inverseOnSurface, "关闭提示", size = 19)
            }
        }) { Text(data.visuals.message) }
    }
}
