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

        // Skala 6.0 (di atas 5.0) dijepit ke maks 5.0
        val overrideHigh = LayoutOverride(
            kitId = "new-metro",
            drums = mapOf(
                "kecil1" to DrumOverride(scale = 6.0f)
            )
        )
        val resultHigh = LayoutMerger.apply(base, overrideHigh)
        val drumHigh = resultHigh.drums.first { it.id == "kecil1" }
        assertEquals(0.15f * 5.0f, drumHigh.radius.rx, 0.001f)

        // Skala 2.0 (di antara 0.5..5.0) dipertahankan
        val overrideMid = LayoutOverride(
            kitId = "new-metro",
            drums = mapOf(
                "kecil1" to DrumOverride(scale = 2.0f)
            )
        )
        val resultMid = LayoutMerger.apply(base, overrideMid)
        val drumMid = resultMid.drums.first { it.id == "kecil1" }
        assertEquals(0.15f * 2.0f, drumMid.radius.rx, 0.001f)

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
        val centerNearTop = Point(0.50f, 0.15f)
        val maxAdaptive = LayoutMerger.calculateMaxAdaptiveScale(centerNearTop, Radius(0.20f, 0.20f), 2.0f)
        assertEquals(LayoutMerger.MAX_SCALE, maxAdaptive, 0.001f)
    }

    @Test
    fun testCenterOutOfBoundsClampedToCanvas() {
        val base = createBaseKit()

        // Center keluar kanvas x = 5.0, y = -5.0
        val overrideOOB = LayoutOverride(
            kitId = "new-metro",
            drums = mapOf(
                "bass" to DrumOverride(center = Point(5.0f, -5.0f), scale = 1.0f)
            )
        )
        val result = LayoutMerger.apply(base, overrideOOB)
        val bass = result.drums.first { it.id == "bass" }

        // Dengan safety retention:
        // horizontalRetention = (0.20 / 2.0) * 0.8 = 0.08 => maxX = 1.08
        // verticalRetention = 0.20 * 0.8 = 0.16 => minY = -0.16
        assertEquals(1.08f, bass.center.x, 0.001f)
        assertEquals(-0.16f, bass.center.y, 0.001f)

        // Posisi negatif yang berada dalam batas retensi (-0.05 >= -0.08) dipertahankan
        val overridePartialOff = LayoutOverride(
            kitId = "new-metro",
            drums = mapOf(
                "bass" to DrumOverride(center = Point(-0.05f, 0.50f), scale = 1.0f)
            )
        )
        val resultPartial = LayoutMerger.apply(base, overridePartialOff)
        val bassPartial = resultPartial.drums.first { it.id == "bass" }
        assertEquals(-0.05f, bassPartial.center.x, 0.001f)
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
