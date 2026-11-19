package com.wmm.ledger

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

internal fun SemanticsNodeInteractionsProvider.enterAmount(value: String, replace: Boolean = false) {
    if (replace) onNodeWithTag("amount-key-clear").performClick()
    value.forEach { onNodeWithTag("amount-key-$it").performClick() }
}
