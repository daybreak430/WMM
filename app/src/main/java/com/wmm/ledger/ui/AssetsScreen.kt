package com.wmm.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.Account
import com.wmm.ledger.data.AccountType
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.MAX_NAME
import com.wmm.ledger.data.accountBalances
import com.wmm.ledger.data.balanceDescription
import com.wmm.ledger.data.balanceInput
import com.wmm.ledger.data.isCredit
import com.wmm.ledger.data.currency
import com.wmm.ledger.data.editableAmount
import com.wmm.ledger.data.netAssetTotal
import com.wmm.ledger.data.parseCents
import com.wmm.ledger.data.validName
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.scroll.dragAutoScroll
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AssetsScreen(data: LedgerSnapshot, model: LedgerViewModel, back: () -> Unit) {
    val balances = remember(data) { data.accountBalances() }
    val active = remember(data.accounts) { data.accounts.filterNot { it.archived } }
    val activeById = remember(active) { active.associateBy { it.id } }
    val order = rememberItemOrder(active.map { it.id })
    val dragState = rememberReorderDragState()
    val listState = rememberLazyListState()
    var accountEditor by rememberSaveable { mutableStateOf<String?>(null) }
    var accountOptions by rememberSaveable { mutableStateOf<String?>(null) }
    var accountCalibration by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("资产", back = back, end = {
            Button(onClick = { accountEditor = "new" }, shape = FieldShape) {
                LucideIcon(Glyph.PLUS, MaterialTheme.colorScheme.onPrimary, size = 17)
                Text("添加账户", Modifier.padding(start = 4.dp))
            }
        })
        DragAndDropContainer(dragState, modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().dragAutoScroll(dragState, listState),
                state = listState,
                contentPadding = PaddingValues(
                    start = PagePadding, end = PagePadding, top = 8.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("net-assets") {
                    BalancePanel {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("净资产总额", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge)
                            SummaryLabel("${active.size} 个账户", Glyph.WALLET)
                        }
                        AmountText(currency(data.netAssetTotal(balances)),
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp), style = HeroNumber)
                    }
                }
                item("account-title") { SectionTitle("账户", Modifier.padding(top = 5.dp)) }
                if (active.isEmpty()) item("empty") { GroupCard { EmptyPanel("还没有账户", glyph = Glyph.WALLET) } }
                items(order.ids, key = { "account:$it" }) { id ->
                    ReorderableLedgerItem(
                        id, order, activeById.keys, dragState,
                        save = { updated, failed -> model.reorderAccounts(updated, failed) },
                        modifier = ledgerItemMotion(Modifier.fillMaxWidth()),
                    ) {
                        AccountCard(activeById.getValue(id), balances[id] ?: 0) { accountOptions = id }
                    }
                }
            }
        }
    }

    val editing = active.firstOrNull { it.id == accountEditor }
    if (accountEditor == "new" || editing != null) {
        AccountDialog(editing, model) { accountEditor = null }
    }
    val opening = active.firstOrNull { it.id == accountOptions }
    if (opening != null) {
        val calibrated = Instant.ofEpochMilli(opening.calibratedAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"))
        LedgerAlertDialog(onDismissRequest = { accountOptions = null }, title = { Text(opening.name) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AmountText(opening.balanceDescription(balances[opening.id] ?: 0), style = HeroNumber)
                Text("上次校准 $calibrated", style = MaterialTheme.typography.bodySmall)
                SettingsRow("校准余额", icon = Glyph.WALLET) {
                    accountCalibration = opening.id
                    accountOptions = null
                }
                Hairline()
                SettingsRow("编辑账户", icon = Glyph.EDIT) {
                    accountEditor = opening.id
                    accountOptions = null
                }
            }
        }, confirmButton = { TextButton(onClick = { accountOptions = null }) { Text("关闭") } })
    }
    val calibrating = active.firstOrNull { it.id == accountCalibration }
    if (calibrating != null) {
        CalibrationDialog(calibrating, balances[calibrating.id] ?: 0, model) { accountCalibration = null }
    }
}

@Composable
private fun AccountCard(account: Account, balance: Long, open: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = open, shape = CardShape, color = colors.surface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Glyph.WALLET)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account.name, style = MaterialTheme.typography.titleMedium)
                val typeLabel = AccountType.valueOf(account.type).title
                if (typeLabel != account.name) {
                    Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            AmountText(account.balanceDescription(balance), modifier = Modifier.weight(.9f),
                style = RowNumber.copy(textAlign = TextAlign.End))
            LucideIcon(Glyph.RIGHT, colors.onSurfaceVariant, size = 16)
        }
    }
}

@Composable
private fun AccountDialog(account: Account?, model: LedgerViewModel, close: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(account?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf("0.00") }
    var typeName by rememberSaveable { mutableStateOf(account?.type ?: AccountType.SAVINGS.name) }
    var saving by remember { mutableStateOf(false) }
    val type = AccountType.valueOf(typeName)
    val parsed = parseCents(amount, signed = true)
    var chooseType by rememberSaveable { mutableStateOf(false) }
    LedgerAlertDialog(onDismissRequest = close, title = { Text(if (account == null) "添加账户" else "编辑账户") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { if (it.length <= MAX_NAME) name = it }, Modifier.fillMaxWidth(),
                label = { Text("账户名称") }, singleLine = true, shape = FieldShape, colors = ledgerFieldColors())
            SettingsRow("账户类型", end = type.title) { chooseType = true }
            if (account == null) {
                SignedAmountField(amount, { amount = it }, if (type.isCredit) "当前欠款" else "初始余额")
                if (type.isCredit) {
                    Text("欠款填正数，溢缴款填负数。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            saving = true
            model.saveAccount(account?.id, type, name, parsed, close).invokeOnCompletion { saving = false }
        }, enabled = validName(name.trim()) && (account != null || parsed != null) && !saving) { Text("保存账户") }
    }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
    if (chooseType) {
        LedgerAlertDialog(onDismissRequest = { chooseType = false }, title = { Text("账户类型") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AccountType.entries.forEach { option ->
                    SettingsRow(option.title, end = if (option == type) "当前" else null) {
                        typeName = option.name
                        chooseType = false
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { chooseType = false }) { Text("关闭") } })
    }
}

@Composable
private fun CalibrationDialog(account: Account, balance: Long, model: LedgerViewModel, close: () -> Unit) {
    var input by rememberSaveable { mutableStateOf(editableAmount(account.balanceInput(balance))) }
    var saving by remember { mutableStateOf(false) }
    val parsed = parseCents(input, signed = true)
    LedgerAlertDialog(onDismissRequest = close, title = { Text("校准余额") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(account.name, style = MaterialTheme.typography.titleMedium)
            SignedAmountField(input, { input = it }, if (account.isCredit) "现在的欠款" else "现在的真实余额")
            if (account.isCredit) {
                Text("欠款填正数，溢缴款填负数。", style = MaterialTheme.typography.bodySmall)
            }
            Text("保存后，只有这次校准之后发生的流水会增减余额。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        TextButton(onClick = {
            if (parsed != null) {
                saving = true
                model.calibrate(account.id, parsed, close).invokeOnCompletion { saving = false }
            }
        }, enabled = parsed != null && !saving) { Text("确认校准") }
    }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}

@Composable
private fun SignedAmountField(input: String, onInput: (String) -> Unit, label: String) {
    OutlinedTextField(input, { if (it.length <= 17) onInput(it) }, Modifier.fillMaxWidth(),
        label = { Text(label) }, prefix = { Text("¥ ") }, singleLine = true,
        shape = FieldShape, colors = ledgerFieldColors(),
        trailingIcon = { TextButton(onClick = {
            onInput(if (input.startsWith("-")) input.removePrefix("-") else "-$input")
        }) { Text("±") } },
        isError = input.isNotBlank() && parseCents(input, signed = true) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}
