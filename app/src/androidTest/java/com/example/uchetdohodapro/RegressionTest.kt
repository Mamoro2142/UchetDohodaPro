package com.example.uchetdohodapro

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.uchetdohodapro.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

abstract class RegressionBase {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context get() = instrumentation.targetContext
    val dao get() = WorkDatabase.getInstance(context).workDao()
    val device get() = UiDevice.getInstance(instrumentation)
    val backupName = "UchetDohodaPro-backup.json"

    fun shot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "regression").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(dir, "$name.png")))
        device.dumpWindowHierarchy(File(dir, "$name.xml"))
    }
    @After fun captureFinalScreen() { shot(javaClass.simpleName + "-final") }

    fun profile() {
        compose.onNodeWithText("Профиль", useUnmergedTree = false).performClick()
    }
    fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    fun waitForText(text: String) {
        compose.waitUntil(15000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    fun chooseBackup() {
        val file = device.wait(Until.findObject(By.text(backupName)), 15000)
            ?: error("Резервная копия не найдена в системном выборе файла")
        file.click()
        waitForText("Заменить данные приложения?")
    }
    fun importBackup(confirm: Boolean) {
        click("Восстановить из файла")
        chooseBackup()
        compose.onNodeWithText(if (confirm) "Заменить" else "Отмена").performClick()
        if (confirm) waitForText("Данные восстановлены")
    }
    fun verifyRestoredData() = runBlocking {
        val days = dao.backupDays()
        assertEquals(2, days.size)
        val original = days.single { it.id == 7L }
        assertEquals("2026-10-24", original.date)
        assertEquals(1.234, original.picks!!, 0.0)
        assertNull(original.weight)
        assertEquals(2.5, original.picksRate, 0.0)
        assertEquals(3.085, original.calculateIncome(), 0.00000001)
        val added = days.single { it.id != 7L }
        assertEquals(3.75, added.calculateIncome(), 0.0)
        assertNull(added.weight)
        assertTrue(days.all { it.shiftId == 9L })
        assertEquals(1, dao.backupShifts().size)
        assertNull(dao.backupTariffs().single().savedAt)
        assertEquals(250L, dao.backupMoneyOperations().single().amountMinor)
        assertEquals(9L, dao.backupMoneyOperations().single().shiftId)
    }
}

@RunWith(AndroidJUnit4::class)
class UpgradeRegressionTest : RegressionBase() {
    @Test fun upgradeOfflineSmallScreenAndSafRoundTrip() {
        runBlocking {
            val old = dao.backupDays().single()
            assertEquals(7L, old.id)
            assertEquals(3.085, old.calculateIncome(), 0.00000001)
            assertNull(dao.backupTariffs().single().savedAt)
        }
        shot("01-updated-home")
        compose.onNodeWithText("Добавить рабочий день", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("НОВЫЙ РАБОЧИЙ ДЕНЬ").assertExists()
        compose.onNodeWithText("Открыть форму").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("2026-11-14")
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextInput("1,5")
        Espresso.closeSoftKeyboard()
        val preview = "3,75 ₽"
        compose.onNodeWithText(preview).performScrollTo().assertIsDisplayed()
        shot("02-comma-preview")
        click("СОХРАНИТЬ РАБОЧИЙ ДЕНЬ")
        waitForText("Рабочий день сохранён")
        device.pressBack()
        compose.onNodeWithText("Добавить рабочий день", substring = true).assertExists()
        // A single Back returns home: no duplicate form stack.
        click("История рабочих дней")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("2026-10-24"))
        compose.onNodeWithText("2026-10-24").performClick()
        // Finish the Compose transition before injecting an Android Back event.
        compose.onNodeWithText("РЕДАКТИРОВАНИЕ ДНЯ").assertIsDisplayed()
        compose.waitForIdle()
        device.pressBack()
        waitForText("ИСТОРИЯ РАБОЧИХ ДНЕЙ")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("2026-10-24"))
        compose.onNodeWithText("2026-10-24").performClick()
        click("СОХРАНИТЬ ИЗМЕНЕНИЯ")
        waitForText("ИСТОРИЯ РАБОЧИХ ДНЕЙ")
        runBlocking {
            assertEquals(1.234, dao.getWorkDay(7)!!.picks!!, 0.0)
            dao.insertWorkDay(WorkDayEntity(date = "2026-11-15", picks = 1.0, picksRate = 1.0))
        }
        // Room commits before its Flow reaches Compose. Wait for the UI count,
        // then search the LazyColumn rather than racing the invalidation query.
        waitForText("Рабочих дней: 3")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("2026-11-15"))
        compose.onNodeWithText("2026-11-15").performClick()
        click("Удалить рабочий день")
        compose.onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { runBlocking { dao.backupDays().size == 2 } }
        device.pressBack()
        compose.onNodeWithText("Добавить рабочий день", substring = true).assertExists()
        compose.onNodeWithText("Вахта").performClick()
        click("Подробная статистика")
        compose.onNodeWithText("2026-10-24").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Вес:", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Объём:", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Боксы:", substring = true).assertCountEquals(0)
        shot("03-empty-metrics-hidden")
        device.pressBack()
        profile()
        click("Добавить аванс или штраф")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("1,25")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("2026-11-14")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10000) { runBlocking { dao.backupMoneyOperations().size == 1 } }
        click("Изменить")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("2,50")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10000) { runBlocking { dao.backupMoneyOperations().singleOrNull()?.amountMinor == 250L } }
        shot("04-operation-updated")
        compose.onNodeWithText("Статистика").performClick()
        compose.onNodeWithText("2026-10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3,09 ₽").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Остаток: 1,25 ₽").performScrollTo().assertIsDisplayed()
        shot("04-monthly-statistics")
        profile()
        click("Создать резервную копию")
        val save = device.wait(Until.findObject(By.text(Pattern.compile("(?i)save"))), 15000)
            ?: error("Системная кнопка сохранения резервной копии не найдена")
        save.click()
        waitForText("Резервная копия создана")
        click("Удалить")
        compose.onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { runBlocking { dao.backupMoneyOperations().isEmpty() } }
        // Add disposable test data. Cancellation must retain it; confirmed import must replace it.
        runBlocking { dao.insertWorkDay(WorkDayEntity(date = "2026-11-15", picks = 10.0, picksRate = 1.0)) }
        importBackup(confirm = false)
        runBlocking { assertEquals(3, dao.backupDays().size); assertTrue(dao.backupMoneyOperations().isEmpty()) }
        importBackup(confirm = true)
        verifyRestoredData()
        // A deleted shift must not force an operation into a different shift.
        runBlocking { dao.deleteShiftSafely(9) }
        waitForText("Без привязки к вахте")
        click("Изменить")
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextInput("После удаления вахты")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10000) { runBlocking { dao.backupMoneyOperations().singleOrNull()?.comment == "После удаления вахты" } }
        runBlocking { assertNull(dao.backupMoneyOperations().single().shiftId) }
        importBackup(confirm = true)
        verifyRestoredData()
        shot("05-restored-profile")
    }
}

@RunWith(AndroidJUnit4::class)
class FreshRestoreRegressionTest : RegressionBase() {
    @Test fun safRestoreAfterUninstallAndReinstall() {
        runBlocking { assertTrue(dao.backupDays().isEmpty()); assertTrue(dao.backupMoneyOperations().isEmpty()) }
        profile()
        importBackup(confirm = true)
        verifyRestoredData()
        shot("06-restored-after-reinstall")
    }
}
