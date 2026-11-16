package com.wmm.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.LabelStyle
import com.wmm.ledger.data.MAX_NAME
import com.wmm.ledger.data.validName

@Composable
internal fun NewLabelDialog(
    model: LedgerViewModel,
    title: String,
    fieldLabel: String,
    section: String,
    onDismiss: () -> Unit,
    onSave: (String, LabelStyle?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var style by remember { mutableStateOf<LabelStyle?>(null) }
    var styling by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    val provider by model.provider.collectAsStateWithLifecycle()
    val configuredProviders by model.providersWithKeys.collectAsStateWithLifecycle()

    fun generateLocal() {
        if (!validName(name.trim()) || styling) return
        styling = true
        model.suggestNewLabelIcon(name, section, useModel = false, done = {
            style = it.style
            styling = false
        }, onFailure = { styling = false })
    }

    fun generateWithModel() {
        if (!validName(name.trim()) || styling) return
        styling = true
        model.suggestNewLabelIcon(name, section, useModel = true, done = {
            style = it.style
            styling = false
        }, onFailure = { styling = false })
    }

    LedgerAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= MAX_NAME) {
                            name = it
                            // 名称变化后旧建议可能失真，必须重新生成。
                            style = null
                        }
                    },
                    label = { Text(fieldLabel) },
                    singleLine = true,
                    enabled = !styling,
                    modifier = Modifier.fillMaxWidth().testTag("new-label-field"),
                    shape = FieldShape,
                    colors = ledgerFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    style?.let { selected ->
                        LabelBadge(selected, 38, Modifier.testTag("new-label-icon-preview"))
                    }
                    TextButton(
                        onClick = {
                            if (provider in configuredProviders) consent = true else generateLocal()
                        },
                        enabled = validName(name.trim()) && !styling,
                    ) {
                        if (styling) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            LucideIcon(Glyph.SPARK, MaterialTheme.colorScheme.primary, size = 18)
                        }
                        Text(
                            if (style == null) "AI 配图标" else "重新配图标",
                            Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), style) },
                enabled = validName(name.trim()) && !styling,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (consent) {
        LedgerAlertDialog(
            onDismissRequest = { consent = false },
            title = { Text("让 AI 推荐图标？") },
            text = { Text("将名称「${name.trim()}」和$section 用途发给 ${provider.title}。") },
            confirmButton = {
                TextButton(onClick = {
                    consent = false
                    generateWithModel()
                }) { Text("生成") }
            },
            dismissButton = {
                TextButton(onClick = {
                    consent = false
                    generateLocal()
                }) { Text("本机推荐") }
                TextButton(onClick = { consent = false }) { Text("取消") }
            },
        )
    }
}
