package com.wmm.ledger

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class LaunchEntryUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun enabledLaunchWorksOnColdAndWarmEntryAndPreservesDraftsOnRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("launch", Context.MODE_PRIVATE)
        val original = if (preferences.contains("open_entry")) preferences.getBoolean("open_entry", false) else null
        val launcherIntent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val home = ui.onNodeWithContentDescription("个人设置")
        val amount = ui.onNodeWithTag("entry-amount")
        val setting = ui.onNodeWithText("启动时打开记一笔")
        fun openSettings() {
            ui.waitUntil(10_000) { home.isDisplayed() }
            home.performClick()
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("启动时打开记一笔"))
        }
        try {
            preferences.edit().remove("open_entry").commit()
            ActivityScenario.launch<MainActivity>(launcherIntent).use {
                openSettings()
                setting.assertIsOff().performClick()
                setting.assertIsOn()
                amount.assertDoesNotExist()
            }
            ActivityScenario.launch<MainActivity>(launcherIntent).use { activity ->
                ui.waitUntil(10_000) { amount.isDisplayed() }
                ui.onNodeWithContentDescription("关闭").performClick()
                ui.waitUntil(10_000) { home.isDisplayed() }
                activity.moveToState(Lifecycle.State.CREATED)
                activity.moveToState(Lifecycle.State.RESUMED)
                ui.waitUntil(10_000) { amount.isDisplayed() }
                ui.onNodeWithTag("amount-key-5").performClick()
                ui.onNodeWithText("¥ 5").assertIsDisplayed()
                activity.moveToState(Lifecycle.State.CREATED)
                activity.moveToState(Lifecycle.State.RESUMED)
                activity.recreate()
                ui.waitUntil(10_000) { ui.onNodeWithText("¥ 5").isDisplayed() }
                ui.onNodeWithContentDescription("关闭").performClick()
                ui.waitUntil(10_000) { home.isDisplayed() }
                activity.recreate()
                ui.waitUntil(10_000) { home.isDisplayed() }
                activity.onActivity { it.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                android.os.SystemClock.sleep(700)
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("input keyevent KEYCODE_BACK").close()
                ui.waitUntil(10_000) { home.isDisplayed() }
                amount.assertDoesNotExist()
                openSettings()
                setting.assertIsOn().performClick()
                setting.assertIsOff()
            }
            ActivityScenario.launch<MainActivity>(launcherIntent).use {
                ui.waitUntil(10_000) { home.isDisplayed() }
                amount.assertDoesNotExist()
            }
        } finally {
            preferences.edit().apply {
                if (original == null) remove("open_entry") else putBoolean("open_entry", original)
            }.commit()
        }
    }
}
