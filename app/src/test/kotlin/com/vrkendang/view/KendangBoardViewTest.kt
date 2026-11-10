package com.vrkendang.view

import android.view.MotionEvent
import com.vrkendang.audio.AudioEngineApi
import com.vrkendang.kitcore.model.DrumData
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius
import com.vrkendang.kitcore.model.ZoneData
import com.vrkendang.kitcore.parser.KitJsonParser
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun testFindDrumAtPixelWithRotatedOval() {
        // Kit dengan canvasAspect 1.0 agar X dan Y proporsional langsung
        val rotatedDrum = DrumData(
            id = "drum-rot",
            model = "single",
            center = Point(0.50f, 0.50f),
            radius = Radius(0.30f, 0.10f),
            rotation = 90.0f,
            zones = listOf(ZoneData(id = "rot-zone", region = "full"))
        )
        val kit = KitData(
            kitId = "rot-test",
            name = "Rot Test",
            canvasAspect = 1.0f,
            drums = listOf(rotatedDrum)
        )

        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kit
        boardView.setDimensionsForTest(1000, 1000)

        // Pusat: x=500, y=500 -> harus mengenai drum
        val centerHit = boardView.findDrumAtPixel(500f, 500f)
        assertNotNull(centerHit)
        assertEquals("drum-rot", centerHit.id)

        // Karena dirotasi 90 derajat, sumbu panjang rx=0.30 (300px) mengarah vertikal (Y),
        // sedangkan sumbu pendek ry=0.10 (100px) mengarah horizontal (X).
        // Titik vertikal (x=500, y=700) -> dy = 200px <= 300px -> INSIDE
        val verticalHit = boardView.findDrumAtPixel(500f, 700f)
        assertNotNull(verticalHit, "Piksel vertikal di dalam rotasi 90 derajat harus terdeteksi")
        assertEquals("drum-rot", verticalHit.id)

        // Titik horizontal (x=700, y=500) -> dx = 200px > 100px -> OUTSIDE
        val horizontalMiss = boardView.findDrumAtPixel(700f, 500f)
        assertNull(horizontalMiss, "Piksel horizontal di luar ketebalan rotasi 90 derajat harus bernilai null")
    }

    @Test
    fun testHandleTouchAtWithRotatedDrum() {
        val rotatedDrum = DrumData(
            id = "drum-rot",
            model = "single",
            center = Point(0.50f, 0.50f),
            radius = Radius(0.30f, 0.10f),
            rotation = 90.0f,
            zones = listOf(ZoneData(id = "rot-zone", region = "full"))
        )
        val kit = KitData(
            kitId = "rot-touch-test",
            name = "Rot Touch Test",
            canvasAspect = 1.0f,
            drums = listOf(rotatedDrum)
        )

        val fakeEngine = FakeAudioEngine()
        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kit
        boardView.audioEngine = fakeEngine
        boardView.setDimensionsForTest(1000, 1000)

        // Sentuhan pada piksel yang berada di dalam oval rotasi (500, 700)
        val handled = boardView.handleTouchAt(500f, 700f, MotionEvent.ACTION_DOWN)
        assertTrue(handled, "Sentuhan di dalam kendang yang dirotasi harus tertangani")
        assertEquals(1, boardView.activeHighlightsCount)

        // Sentuhan pada piksel di luar oval rotasi (700, 500)
        val missed = boardView.handleTouchAt(700f, 500f, MotionEvent.ACTION_DOWN)
        assertFalse(missed, "Sentuhan di luar kendang yang dirotasi harus diabaikan")
    }

    @Test
    fun testDrumBodyBitmapsPropertyAssignment() {
        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        assertTrue(boardView.drumBodyBitmaps.isEmpty())
        val fakeMap = mapOf<String, android.graphics.Bitmap>()
        boardView.drumBodyBitmaps = fakeMap
        assertEquals(0, boardView.drumBodyBitmaps.size)
    }

    @Test
    fun testTouchOnBodyOutsideSkinIgnored() {
        // Kendang dengan kulit di pusat (0.5, 0.5) dengan radius rx=0.15, ry=0.15 (px: 500, 500, r=150px)
        // Body menjulur ke bawah: offsetY = 0.25 (y=0.75, px: 750)
        val drumWithBody = DrumData(
            id = "drum-body",
            model = "single",
            center = Point(0.50f, 0.50f),
            radius = Radius(0.15f, 0.15f),
            image = "images/drum-body.png",
            body = com.vrkendang.kitcore.model.DrumBodyData(
                image = "images/drum-body-body.png",
                width = 0.30f,
                height = 0.40f,
                offsetX = 0.0f,
                offsetY = 0.25f,
                rotation = 0.0f
            ),
            zones = listOf(ZoneData(id = "drum-body-1", region = "full"))
        )
        val kit = KitData(
            kitId = "body-test",
            name = "Body Test",
            canvasAspect = 1.0f,
            drums = listOf(drumWithBody)
        )

        val fakeEngine = FakeAudioEngine()
        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kit
        boardView.audioEngine = fakeEngine
        boardView.setDimensionsForTest(1000, 1000)

        // Sentuhan di area kulit (500, 500) -> tertangani
        val skinHit = boardView.handleTouchAt(500f, 500f, MotionEvent.ACTION_DOWN)
        assertTrue(skinHit, "Sentuhan di area kulit harus tertangani")
        assertEquals(1, boardView.activeHighlightsCount)

        // Sentuhan di area body di luar kulit (x=500, y=750 -> di luar radius kulit y=650) -> diabaikan
        val bodyMiss = boardView.handleTouchAt(500f, 750f, MotionEvent.ACTION_DOWN)
        assertFalse(bodyMiss, "Sentuhan di area gambar body di luar kulit kendang harus diabaikan")
    }

    @Test
    fun testDrawWithSkinAndBodyBitmapsDoesNotCrash() {
        val drum = DrumData(
            id = "drum1",
            model = "single",
            center = Point(0.50f, 0.50f),
            radius = Radius(0.15f, 0.15f),
            image = "images/drum1.png",
            body = com.vrkendang.kitcore.model.DrumBodyData(
                image = "images/drum1-body.png",
                width = 0.30f,
                height = 0.30f
            ),
            zones = listOf(ZoneData(id = "drum1-1", region = "full"))
        )
        val kit = KitData(
            kitId = "draw-test",
            name = "Draw Test",
            canvasAspect = 2.0f,
            drums = listOf(drum)
        )

        val boardView = KendangBoardView(object : android.content.ContextWrapper(null) {})
        boardView.kit = kit
        boardView.setDimensionsForTest(2000, 1000)

        val skinBmp = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val bodyBmp = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
        boardView.drumBitmaps = mapOf("drum1" to skinBmp)
        boardView.drumBodyBitmaps = mapOf("drum1" to bodyBmp)

        val canvas = android.graphics.Canvas(android.graphics.Bitmap.createBitmap(2000, 1000, android.graphics.Bitmap.Config.ARGB_8888))
        boardView.draw(canvas)
    }
}
