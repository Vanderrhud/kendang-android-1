package com.vrkendang.view

import android.view.View
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.parser.KitJsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CalibrationOverlayViewTest {

    private fun loadNewMetroKit(): KitData {
        val candidates = listOf(
            File("src/main/assets/kits/new-metro/kit.json"),
            File("app/src/main/assets/kits/new-metro/kit.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("kit.json New Metro tidak ditemukan")
        return KitJsonParser.parse(file.readText())
    }

    @Test
    fun testStartCalibrationDisablesKitSelectorAndEntersEditMode() {
        val context = RuntimeEnvironment.getApplication()
        val headerView = HeaderView(context)
        val boardView = KendangBoardView(context)
        val overlay = CalibrationOverlayView(context)

        overlay.headerView = headerView
        overlay.boardView = boardView

        val kit = loadNewMetroKit()
        overlay.startCalibration(kit, kit)

        // Sesuai Task 10.1:
        // Layar beralih ke mode kalibrasi (VISIBLE)
        assertEquals(View.VISIBLE, overlay.visibility)
        // Tombol pemilih kit dinonaktifkan
        assertFalse(headerView.kitSelectorButton.isEnabled)
        // Board mode menjadi EDIT_LAYOUT
        assertEquals(KendangBoardView.BoardMode.EDIT_LAYOUT, boardView.mode)
    }

    @Test
    fun testSaveLayoutOverrideClosesCalibrationAndReEnablesKitSelector() {
        val context = RuntimeEnvironment.getApplication()
        val headerView = HeaderView(context)
        val boardView = KendangBoardView(context)
        val overlay = CalibrationOverlayView(context)

        overlay.headerView = headerView
        overlay.boardView = boardView

        val kit = loadNewMetroKit()
        overlay.startCalibration(kit, kit)

        overlay.saveLayoutOverride()

        // Sesuai Task 10.4:
        // Kalibrasi ditutup (GONE)
        assertEquals(View.GONE, overlay.visibility)
        // Pemilih kit aktif kembali
        assertTrue(headerView.kitSelectorButton.isEnabled)
        // Board kembali ke mode PLAY
        assertEquals(KendangBoardView.BoardMode.PLAY, boardView.mode)
    }

    @Test
    fun testCancelClosesCalibrationAndRevertsBoardMode() {
        val context = RuntimeEnvironment.getApplication()
        val headerView = HeaderView(context)
        val boardView = KendangBoardView(context)
        val overlay = CalibrationOverlayView(context)

        overlay.headerView = headerView
        overlay.boardView = boardView

        val kit = loadNewMetroKit()
        overlay.startCalibration(kit, kit)

        overlay.handleCancel()

        assertEquals(View.GONE, overlay.visibility)
        assertTrue(headerView.kitSelectorButton.isEnabled)
        assertEquals(KendangBoardView.BoardMode.PLAY, boardView.mode)
    }
}
