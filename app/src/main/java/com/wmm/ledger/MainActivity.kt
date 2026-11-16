package com.wmm.ledger

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wmm.ledger.data.Period
import com.wmm.ledger.ui.AssetsScreen
import com.wmm.ledger.ui.ConfirmDialog
import com.wmm.ledger.ui.EmptyPanel
import com.wmm.ledger.ui.EntryEditor
import com.wmm.ledger.ui.Glyph
import com.wmm.ledger.ui.LedgerScreenFrame
import com.wmm.ledger.ui.LedgerScreen
import com.wmm.ledger.ui.LedgerTheme
import com.wmm.ledger.ui.LedgerScreenTheme
import com.wmm.ledger.ui.blockExitingPage
import com.wmm.ledger.ui.pageMotion
import com.wmm.ledger.ui.LucideIcon
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

private enum class Tab(val label: String, val icon: Glyph) {
    LEDGER("账本", Glyph.BOOK), STATS("统计", Glyph.CHART), ASSETS("资产", Glyph.WALLET)
}

private data class Destination(val route: String?, val depth: Int)

@Composable
private fun LedgerApp(model: LedgerViewModel, appearance: Appearance) {
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

    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun back() {
        if (route != null) {
            focus.clearFocus(force = true)
            keyboard?.hide()
            route = parent
            parent = null
        } else {
            tabName = Tab.LEDGER.name
        }
    }

    BackHandler(enabled = route != null || tab != Tab.LEDGER, onBack = ::back)

    val destination = Destination(route, if (route == null) 0 else if (parent == null) 1 else 2)
    val refined = route == "new" || route?.startsWith("entry:") == true || (route == null && tab == Tab.LEDGER)
    LedgerScreenFrame(refined, tabName) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            snackbarHost = {
                SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = if (route == null) 62.dp else 0.dp)) { data ->
                    Snackbar(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        dismissAction = {
                            IconButton(onClick = data::dismiss) {
                                LucideIcon(Glyph.CLOSE, MaterialTheme.colorScheme.inverseOnSurface, "关闭提示", size = 19)
                            }
                        },
                    ) { Text(data.visuals.message) }
                }
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
                    transitionSpec = { pageMotion(if (targetState.depth >= initialState.depth) 1 else -1) },
                    label = "详情页转场",
                ) { screen ->
                    val current = screen.route
                    val active = screen == destination
                    if (current == null) {
                        Column(Modifier.fillMaxSize().blockExitingPage(active)) {
                            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                                LedgerScreenTheme(tab == Tab.LEDGER) {
                                    navState.SaveableStateProvider(tab.name) {
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
                                            }, { route = "tag:$it" }, { route = "scene:$it" }, generating,
                                                provider in keys, model.hasReportConsent(),
                                                { agreed -> model.generate(period, day, agreed) },
                                                { route = "model" })
                                            Tab.ASSETS -> AssetsScreen(data, model)
                                        }
                                    }
                                }
                            }
                            LedgerNavigation(tab) { if (active) tabName = it.name }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().navigationBarsPadding().blockExitingPage(active)) {
                            LedgerScreenTheme(current == "new" || current.startsWith("entry:")) {
                                when {
                                    current == "settings" -> SettingsScreen(data, model, provider, provider in keys,
                                        fileBusy, appearance, ::back,
                                        tags = { parent = current; route = "tags" },
                                        openModel = { parent = current; route = "model" })
                                    current == "tags" -> TagsScreen(data, model, ::back)
                                    current == "model" -> ModelSettings(model, provider, keys, ::back)
                                    current == "new" -> EntryEditor(data, null, model) { if (route == current) back() }
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
    }

    pendingRestore?.let { backup ->
        ConfirmDialog("替换本机账本？", "此备份含 ${backup.entries.size} 笔流水和 ${backup.accounts.size} 个账户。" +
            "恢复会替换本机的流水、标签、资产、资料和报告，无法撤销。", "替换并恢复",
            model::cancelRestore, model::restore)
    }
}


@Composable
private fun LedgerNavigation(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp)) {
            val indicatorWidth = 14.dp
            val tabWidth = maxWidth / Tab.entries.size
            val indicatorPosition = tabWidth * (selected.ordinal + .5f) - indicatorWidth / 2
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                for (item in Tab.entries) {
                    val active = selected == item
                    val tint = if (active) colors.primary else colors.onSurfaceVariant
                    Column(
                        Modifier.weight(1f).heightIn(min = 62.dp)
                            .selectable(active, role = Role.Tab, onClick = { onSelect(item) })
                            .padding(top = 7.dp, bottom = 13.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        LucideIcon(item.icon, tint, size = 21)
                        Text(item.label, color = tint, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomStart).offset(x = indicatorPosition, y = (-5).dp)
                .width(indicatorWidth).height(2.dp).background(colors.primary, CircleShape))
        }
    }
}
