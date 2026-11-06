package com.vrkendang.view

import android.view.MotionEvent
import com.vrkendang.audio.AudioEngineApi
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.parser.KitJsonParser
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KendangBoardViewTest {

    private class FakeAudioEngine : AudioEngineApi {
        val triggeredEvents = mutableListOf<Pair<Int, Float>>()

        override fun loadSound(pcmData: ByteBuffer, frameCount: Int, sampleRate: Int, slotIndex: Int): Boolean = true
        override fun releaseAll() {}
        override fun openStream(): Boolean = true
        override fun startStream(): Boolean = true

        override fun triggerSound(slotIndex: Int, gain: Float): Boolean {
            triggeredEvents.add(Pair(slotIndex, gain))
            return true
        }
    }

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
    fun testUnassignedZoneTapTriggersHighlightWithoutAudio() {
        // Kit dengan zona sound: null
        val baseKit = loadNewMetroKit()
        val kit = baseKit.copy(
            drums = baseKit.drums.map { drum ->
                drum.copy(zones = drum.zones.map { it.copy(sound = null) })
            }
        )
        val fakeEngine = FakeAudioEngine()

        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kit
        boardView.audioEngine = fakeEngine

        // Konfigurasi mapper 2000x1000 (aspect 2.0)
        boardView.setDimensionsForTest(2000, 1000)

        var hitZoneId: String? = null
        boardView.onZoneHitListener = { _, zone ->
            hitZoneId = zone.id
        }

        // Bass center: x=0.25 (px=500), y=0.55 (px=550)
        val handled = boardView.handleTouchAt(500f, 550f, MotionEvent.ACTION_DOWN)

        assertTrue(handled, "Sentuhan pada Bass harus tertangani")
        assertEquals("bass-1", hitZoneId)
        assertEquals(1, boardView.activeHighlightsCount, "Visual tap highlight harus aktif")
        assertEquals(0, fakeEngine.triggeredEvents.size, "Zona tanpa suara tidak boleh memicu audio engine")
    }

    @Test
    fun testAssignedZoneTapTriggersAudioAndHighlight() {
        val baseKit = loadNewMetroKit()
        // Buat salinan di mana bass-1 memiliki sound
        val modifiedDrums = baseKit.drums.map { drum ->
            if (drum.id == "bass") {
                drum.copy(
                    zones = drum.zones.map { zone ->
                        if (zone.id == "bass-1") zone.copy(sound = "sounds/bass-1.wav", gain = 1.5f) else zone
                    }
                )
            } else drum
        }
        val kitWithSound = baseKit.copy(drums = modifiedDrums)

        val fakeEngine = FakeAudioEngine()
        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kitWithSound
        boardView.audioEngine = fakeEngine
        boardView.slotByZoneId = mapOf("bass-1" to 3)

        boardView.setDimensionsForTest(2000, 1000)

        val handled = boardView.handleTouchAt(500f, 550f, MotionEvent.ACTION_DOWN)
        assertTrue(handled)
        assertEquals(1, boardView.activeHighlightsCount)
        assertEquals(1, fakeEngine.triggeredEvents.size)
        assertEquals(3, fakeEngine.triggeredEvents[0].first)
        assertEquals(1.5f, fakeEngine.triggeredEvents[0].second)
    }

    @Test
    fun testTouchInMarginIsIgnored() {
        val kit = loadNewMetroKit()
        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kit

        // Layar 2400x1000 dengan canvasAspect 2.0 -> kanvas 2000x1000, margin kiri 200px (0..200)
        boardView.setDimensionsForTest(2400, 1000)

        var hit = false
        boardView.onZoneHitListener = { _, _ -> hit = true }

        // Sentuhan pada piksel x=100 (area margin kiri)
        val handled = boardView.handleTouchAt(100f, 500f, MotionEvent.ACTION_DOWN)

        assertFalse(handled, "Sentuhan di area margin harus diabaikan")
        assertFalse(hit)
        assertEquals(0, boardView.activeHighlightsCount)
    }

    @Test
    fun testActionMoveDoesNotTriggerAudioOrNewHighlight() {
        val kit = loadNewMetroKit()
        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kit
        boardView.setDimensionsForTest(2000, 1000)

        // ACTION_MOVE tidak boleh memicu suara baru
        val handled = boardView.handleTouchAt(500f, 550f, MotionEvent.ACTION_MOVE)

        assertFalse(handled, "ACTION_MOVE tidak boleh memicu hit baru")
        assertEquals(0, boardView.activeHighlightsCount)
    }

    @Test
    fun testEditLayoutModeDoesNotTriggerAudio() {
        val kit = loadNewMetroKit()
        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kit
        boardView.setDimensionsForTest(2000, 1000)
        boardView.mode = KendangBoardView.BoardMode.EDIT_LAYOUT

        // Pada mode edit tata letak, handleTouchAt mengembalikan false (tidak ada audio)
        val handled = boardView.handleTouchAt(500f, 550f, MotionEvent.ACTION_DOWN)
        assertFalse(handled, "Mode EDIT_LAYOUT tidak boleh memicu play hit")
        assertEquals(0, boardView.activeHighlightsCount)
    }

    @Test
    fun testDrumBitmapsPropertyAssignment() {
        val kit = loadNewMetroKit()
        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kit
        boardView.setDimensionsForTest(2000, 1000)

        assertTrue(boardView.drumBitmaps.isEmpty())
        val fakeBitmaps = mapOf<String, android.graphics.Bitmap>()
        boardView.drumBitmaps = fakeBitmaps
        assertEquals(0, boardView.drumBitmaps.size)
    }
}
