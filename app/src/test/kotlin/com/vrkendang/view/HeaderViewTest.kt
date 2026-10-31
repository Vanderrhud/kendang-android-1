package com.vrkendang.view

import android.view.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HeaderViewTest {

    @Test
    fun testAdSlotIsHiddenInMvp() {
        val context = RuntimeEnvironment.getApplication()
        val headerView = HeaderView(context)

        // Sesuai Task 9.1: slot iklan tengah tersembunyi pada MVP
        assertEquals(View.GONE, headerView.adSlotContainer.visibility)
    }

    @Test
    fun testSetActiveKitNameUpdatesSelectorButtonText() {
        val context = RuntimeEnvironment.getApplication()
        val headerView = HeaderView(context)

        headerView.setActiveKitName("Koplo Mantap")
        assertTrue(headerView.kitSelectorButton.text.contains("Koplo Mantap"))
    }

    @Test
    fun testKitSelectorButtonClickTriggersListener() {
        val context = RuntimeEnvironment.getApplication()
        val headerView = HeaderView(context)

        var clicked = false
        headerView.onKitSelectorClicked = {
            clicked = true
        }

        headerView.kitSelectorButton.performClick()
        assertTrue(clicked)
    }

    @Test
    fun testSettingsMenuHasEditLayoutOption() {
        val items = SettingsMenuProviderImpl.getMenuItems()
        assertTrue(items.any { it.id == "edit_layout" && it.title == "Edit Tata Letak" })
    }
}
