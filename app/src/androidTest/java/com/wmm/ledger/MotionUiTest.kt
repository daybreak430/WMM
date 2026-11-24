package com.wmm.ledger

import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.Tag
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.LedgerSnapshot
import java.io.File
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import com.wmm.ledger.ui.AmountText
import com.wmm.ledger.ui.LedgerTheme
import com.wmm.ledger.ui.LedgerVisibility
import com.wmm.ledger.ui.LocalLedgerMotionEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MotionUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun expandedTagsAndScenesRemainReachableWhenTheWindowIsShort() {
        val tags = (0..11).map { Tag("motion-tag-$it", Kind.EXPENSE.name, "类型$it", sortOrder = it) }
        val scenes = (0..2).map { Scene("motion-scene-$it", "场景$it", it) }
        withLedger(LedgerSnapshot(tags = tags, scenes = scenes)) { _, _ ->
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
            ui.onNodeWithContentDescription("记一笔").performClick()
            ui.onNodeWithText("全部类型").performScrollTo().performClick()
            ui.onNodeWithText("类型11").performScrollTo().performClick()
            ui.onNodeWithText("收起类型").performScrollTo().performClick()
            ui.onNodeWithText("场景2").performScrollTo().performClick()
            if (!ui.onNodeWithTag("entry-account").isDisplayed()) ui.onNodeWithTag("entry-account").performScrollTo()
            ui.onNodeWithTag("entry-account").assertIsDisplayed()
            ui.onNodeWithTag("entry-note").assertIsDisplayed()
            ui.enterAmount("12.35")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val config = context.resources.configuration
            val output = File(context.getExternalFilesDir(null), "motion-editor-${config.screenWidthDp}x${config.screenHeightDp}.png")
            output.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            ui.onNodeWithText("保存流水").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("记一笔").isDisplayed() }
        }
    }

    @Test fun disappearingControlsLoseTouchAndSemanticsBeforeTheirExitFinishes() {
        val visible = mutableStateOf(true)
        var clicks = 0
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { host -> host.setContent {
                LedgerTheme(false) {
                    Column {
                        LedgerVisibility(visible.value) {
                            Text("可选场景", Modifier.fillMaxWidth().height(64.dp).clickable { clicks++ })
                        }
                    }
                }
            } }
            val center = ui.onNodeWithText("可选场景").fetchSemanticsNode().boundsInRoot.center
            ui.mainClock.autoAdvance = false
            try {
                ui.runOnIdle { visible.value = false }
                ui.mainClock.advanceTimeByFrame()
                ui.onNodeWithText("可选场景").assertDoesNotExist()
                ui.onRoot().performTouchInput { click(center) }
                assertEquals(0, clicks)
                ui.runOnIdle { visible.value = true }
                ui.mainClock.advanceTimeBy(64)
                ui.onNodeWithText("可选场景").performClick()
                assertEquals(1, clicks)
            } finally { ui.mainClock.autoAdvance = true }
        }
    }

    @Test fun hidingAnAmountRemovesItsOldValueEvenInTheFirstAnimationFrame() {
        val amount = mutableStateOf("¥12,345.67")
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { host -> host.setContent { LedgerTheme(false) { AmountText(amount.value) } } }
            ui.onNodeWithText("¥12,345.67").assertIsDisplayed()
            ui.mainClock.autoAdvance = false
            try {
                ui.runOnIdle { amount.value = "¥••••••" }
                ui.mainClock.advanceTimeByFrame()
                ui.onNodeWithText("¥12,345.67", useUnmergedTree = true).assertDoesNotExist()
                ui.onNodeWithText("¥••••••").assertIsDisplayed()
                ui.runOnIdle { amount.value = "¥88.00" }
                ui.mainClock.advanceTimeByFrame()
                ui.onNodeWithText("¥••••••", useUnmergedTree = true).assertDoesNotExist()
                ui.onNodeWithText("¥88.00").assertIsDisplayed()
            } finally { ui.mainClock.autoAdvance = true }
        }
    }

    @Test fun reducedMotionRemovesHiddenContentWithoutWaitingForAnimationTime() {
        val visible = mutableStateOf(true)
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { host -> host.setContent {
                LedgerTheme(true) {
                    CompositionLocalProvider(LocalLedgerMotionEnabled provides false) {
                        LedgerVisibility(visible.value) { Text("待移除内容") }
                    }
                }
            } }
            ui.onNodeWithText("待移除内容").assertIsDisplayed()
            ui.mainClock.autoAdvance = false
            try {
                ui.runOnIdle { visible.value = false }
                ui.mainClock.advanceTimeByFrame()
                ui.onNodeWithText("待移除内容", useUnmergedTree = true).assertDoesNotExist()
            } finally { ui.mainClock.autoAdvance = true }
        }
    }
}
