package com.wmm.ledger

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.wmm.ledger.data.BillLabels
import com.wmm.ledger.data.BillRow
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import com.wmm.ledger.ui.BillRowDialog
import com.wmm.ledger.ui.LedgerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class BillReviewUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun existingTypeCanReplaceTheSuggestionAndSceneCanBeCleared() {
        val row = BillRow("draft", Kind.EXPENSE, "18.50", "2026-01-02", "", "午餐", "出差", "")
        val labels = BillLabels(LedgerSnapshot(tags = listOf(Tag("meal", Kind.EXPENSE.name, "工作餐")),
            scenes = listOf(Scene("trip", "出差"))))
        val reviewed = mutableStateOf<BillRow?>(null)
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity {
                it.setContent {
                    LedgerTheme(false) {
                        if (reviewed.value == null) BillRowDialog(row, labels, dismiss = {}) { reviewed.value = it }
                    }
                }
            }
            ui.onNodeWithText("AI 建议 · 出差 · 午餐").assertIsDisplayed()
            ui.onNodeWithText("时间 HH:mm（选填）").assertDoesNotExist()
            ui.onNodeWithText("场景").performScrollTo().performClick()
            ui.onNodeWithText("无场景").performClick()
            ui.onNodeWithText("类型").performScrollTo().performClick()
            ui.onNodeWithText("工作餐").performClick()
            ui.runOnIdle { assertNull(reviewed.value) }
            ui.onNodeWithText("保存修改").performClick()
            ui.runOnIdle { assertEquals(row.copy(type = "工作餐", scene = ""), reviewed.value) }
        }
    }
}
