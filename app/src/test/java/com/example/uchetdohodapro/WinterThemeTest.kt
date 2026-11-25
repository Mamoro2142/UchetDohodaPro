package com.example.uchetdohodapro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WinterThemeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun clear() { context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit() }
    @After fun clean() { clear() }

    @Test fun standardThemeIsDefault() {
        assertFalse(WinterPreferences.enabled(context))
    }

    @Test fun togglePersistsAcrossReads() {
        WinterPreferences.setEnabled(context, true)
        assertTrue(WinterPreferences.enabled(ApplicationProvider.getApplicationContext()))
        WinterPreferences.setEnabled(context, false)
        assertFalse(WinterPreferences.enabled(ApplicationProvider.getApplicationContext()))
    }

    @Test fun hintIsOneTimeAndIndependentOfTheme() {
        assertFalse(WinterPreferences.hintSeen(context))
        assertTrue(showWinterHint(WinterPreferences.enabled(context), WinterPreferences.hintSeen(context)))
        WinterPreferences.setEnabled(context, true)
        assertTrue(WinterPreferences.hintSeen(context))
        assertFalse(showWinterHint(true, true))
        WinterPreferences.setEnabled(context, false)
        assertFalse(WinterPreferences.enabled(context))
        assertTrue(WinterPreferences.hintSeen(ApplicationProvider.getApplicationContext()))
        assertFalse(showWinterHint(false, WinterPreferences.hintSeen(context)))
        assertFalse(showWinterHint(true, false))
    }

    @Test fun sharedAppearanceRestoresChoiceAndOneTimeHint() {
        val appearance = WinterAppearance(context)
        assertFalse(appearance.enabled)
        assertFalse(appearance.hintSeen)
        appearance.toggle()
        assertTrue(appearance.enabled)
        assertTrue(appearance.hintSeen)
        val recreated = WinterAppearance(context)
        assertTrue(recreated.enabled)
        assertTrue(recreated.hintSeen)
        recreated.toggle()
        assertFalse(recreated.enabled)
        val reopened = WinterAppearance(context)
        assertFalse(reopened.enabled)
        assertTrue(reopened.hintSeen)
        assertFalse(showWinterHint(reopened.enabled, reopened.hintSeen))
    }

    @Test fun sparklesAreBoundedAndSeamless() {
        for (offset in listOf(0f, 0.19f, 0.38f, 0.57f)) {
            assertEquals(winterSparkle(0f, offset), winterSparkle(1f, offset), 0.00001f)
            for (i in 0..100) assertTrue(winterSparkle(i / 100f, offset) in 0f..1f)
        }
    }

    @Test fun animationIsBoundedAndSeamless() {
        for (cycles in 1..3) for (offset in listOf(0f, 0.17f, -0.35f)) {
            assertEquals(winterPulse(0f, cycles, offset), winterPulse(1f, cycles, offset), 0.00001f)
            for (i in 0..100) assertTrue(winterPulse(i / 100f, cycles, offset) in 0f..1f)
        }
    }
}
