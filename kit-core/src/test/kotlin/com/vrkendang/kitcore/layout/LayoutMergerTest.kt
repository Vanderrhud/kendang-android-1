package com.vrkendang.kitcore.layout

import com.vrkendang.kitcore.model.*
import kotlin.test.*

class LayoutMergerTest {

    private fun createBaseKit(): KitData {
        return KitData(
            formatVersion = 1,
            kitId = "new-metro",
            name = "New Metro",
            version = 1,
            canvasAspect = 2.0f,
            drums = listOf(
                DrumData(
                    id = "bass",
                    model = "bass",
                    center = Point(0.25f, 0.55f),
                    radius = Radius(0.20f, 0.20f),
                    divider = DividerData(-0.30f, 0.0f, 0.80f),
                    zones = listOf(
                        ZoneData(id = "bass-1", region = "main", sound = null, gain = 1.0f)
                    )
                ),
                DrumData(
                    id = "kecil1",
                    model = "single",
                    center = Point(0.50f, 0.50f),
                    radius = Radius(0.15f, 0.15f),
                    zones = listOf(
                        ZoneData(id = "kecil1-1", region = "full", sound = null, gain = 1.0f)
                    )
                )
            )
        )
    }

    @Test
    fun testScaleClampingLimits() {
        val base = createBaseKit()

        // Skala 2.0 (di atas 1.5) dijepit ke maks 1.5
        val overrideHigh = LayoutOverride(
            kitId = "new-metro",
            drums = mapOf(
                "kecil1" to DrumOverride(scale = 2.0f)
            )
        )
        val resultHigh = LayoutMerger.apply(base, overrideHigh)
        val drumHigh = resultHigh.drums.first { it.id == "kecil1" }
        assertEquals(0.15f * 1.5f, drumHigh.radius.rx, 0.001f)

        // Skala 0.1 (di bawah 0.5) dijepit ke min 0.5
        val overrideLow = LayoutOverride(
            kitId = "new-metro",
            drums = mapOf(
                "kecil1" to DrumOverride(scale = 0.1f)
            )
        )
        val resultLow = LayoutMerger.apply(base, overrideLow)
        val drumLow = resultLow.drums.first { it.id == "kecil1" }
        assertEquals(0.15f * 0.5f, drumLow.radius.rx, 0.001f)
    }

    @Test
    fun testAdaptiveScaleNearCanvasEdge() {
        val base = createBaseKit()

        // Kendang berada sangat dekat tepi atas (y = 0.15, radius ry = 0.20)
        // Jika scale = 1.0, ry = 0.20 > y (0.15), skala adaptif akan membatasi agar tidak keluar
        val centerNearTop = Point(0.50f, 0.15f)
        val maxAdaptive = LayoutMerger.calculateMaxAdaptiveScale(centerNearTop, Radius(0.20f, 0.20f), 2.0f)
        assertTrue(maxAdaptive <= (0.15f / 0.20f) + 0.001f)
    }

    @Test
    fun testCenterOutOfBoundsClampedToCanvas() {
        val base = createBaseKit()

        // Center keluar kanvas x = 1.5, y = -0.5
        val overrideOOB = LayoutOverride(
            kitId = "new-metro",
            drums = mapOf(
                "bass" to DrumOverride(center = Point(1.5f, -0.5f), scale = 1.0f)
            )
        )
        val result = LayoutMerger.apply(base, overrideOOB)
        val bass = result.drums.first { it.id == "bass" }

        assertTrue(bass.center.x <= 1.0f)
        assertTrue(bass.center.x >= 0.0f)
        assertTrue(bass.center.y <= 1.0f)
        assertTrue(bass.center.y >= 0.0f)
    }

    @Test
    fun testUnknownDrumIdIgnored() {
        val base = createBaseKit()

        // Override memuat ID yang tidak ada di base kit
        val overrideUnknown = LayoutOverride(
            kitId = "new-metro",
            drums = mapOf(
                "ghost_drum" to DrumOverride(center = Point(0.1f, 0.1f), scale = 1.2f)
            )
        )
        val result = LayoutMerger.apply(base, overrideUnknown)

        assertEquals(base.drums.size, result.drums.size)
        assertEquals(base.drums[0].center, result.drums[0].center)
    }

    @Test
    fun testCorruptJsonFallbackToBase() {
        val base = createBaseKit()
        val corruptJson = "{ \"kitId\": \"new-metro\", \"drums\": { broken... "

        val result = LayoutMerger.mergeFromJson(base, corruptJson)
        assertEquals(base, result)

        val nullResult = LayoutMerger.mergeFromJson(base, null)
        assertEquals(base, nullResult)
    }
}
