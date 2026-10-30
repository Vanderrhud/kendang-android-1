package com.vrkendang.kitcore.hittest

import com.vrkendang.kitcore.factory.KitFactory
import com.vrkendang.kitcore.model.*
import kotlin.test.*

class HitTesterTest {

    private val newMetroKit = KitFactory.createFromTemplate("new-metro-test", "New Metro Test")

    @Test
    fun testBassMainZone() {
        // Bass center (x=0.25, y=0.55), canvasAspect=2.0 -> CX_u = 0.50, CY_u = 0.55, rx = 0.30
        // C_div: dx = -0.30, dy = 0.0, r = 0.80 -> center (0.41, 0.55), radius = 0.24
        // Titik dekat pusat C_div: xu = 0.41 -> canvasX = 0.41 / 2.0 = 0.205, canvasY = 0.55
        val zone = HitTester.findZone(newMetroKit, 0.205f, 0.55f)
        assertNotNull(zone)
        assertEquals("bass-1", zone.id)
        assertEquals("main", zone.region)
    }

    @Test
    fun testBassCrescentBands() {
        // Titik crescent di sisi kanan oval Bass (xu = 0.70 -> canvasX = 0.35f, di luar C_div).
        // Pada X tersebut oval hanya mencakup Y sekitar [0.326, 0.774], sehingga titik
        // band teratas dan terbawah dipilih menjauh dari tepi oval.
        // Oval Y rentang [0.25, 0.85], total tinggi 0.60, 4 band per 0.15 tinggi:
        // Band 0: [0.25, 0.40) -> Y = 0.35
        // Band 1: [0.40, 0.55) -> Y = 0.48
        // Band 2: [0.55, 0.70) -> Y = 0.62
        // Band 3: [0.70, 0.85] -> Y = 0.75

        val band0Zone = HitTester.findZone(newMetroKit, 0.35f, 0.35f)
        assertNotNull(band0Zone)
        assertEquals("bass-2", band0Zone.id)
        assertEquals(0, band0Zone.band)

        val band1Zone = HitTester.findZone(newMetroKit, 0.35f, 0.48f)
        assertNotNull(band1Zone)
        assertEquals("bass-3", band1Zone.id)
        assertEquals(1, band1Zone.band)

        val band2Zone = HitTester.findZone(newMetroKit, 0.35f, 0.62f)
        assertNotNull(band2Zone)
        assertEquals("bass-4", band2Zone.id)
        assertEquals(2, band2Zone.band)

        val band3Zone = HitTester.findZone(newMetroKit, 0.35f, 0.75f)
        assertNotNull(band3Zone)
        assertEquals("bass-5", band3Zone.id)
        assertEquals(3, band3Zone.band)
    }

    @Test
    fun testBassDividerBoundary() {
        // C_div: center (0.41, 0.55), radius 0.24.
        // Di sumbu X (Y = 0.55):
        // Tepi kanan C_div ada di xu = 0.41 + 0.24 = 0.65
        // Sedikit di dalam C_div (xu = 0.64 -> canvasX = 0.32f) -> bass-1 (main)
        val insideDiv = HitTester.findZone(newMetroKit, 0.32f, 0.55f)
        assertNotNull(insideDiv)
        assertEquals("bass-1", insideDiv.id)

        // Sedikit di luar C_div (xu = 0.66 -> canvasX = 0.33f) -> bass-4 (crescent band 2 karena Y=0.55)
        val outsideDiv = HitTester.findZone(newMetroKit, 0.33f, 0.55f)
        assertNotNull(outsideDiv)
        assertEquals("crescent", outsideDiv.region)
    }

    @Test
    fun testSplitModelSedangAndKecil2() {
        // Sedang: center x=0.66, y=0.60, radius rx=0.20, ry=0.20
        // Upper: Y < 0.60 (misal Y = 0.55) -> sedang-2
        val upperZone = HitTester.findZone(newMetroKit, 0.66f, 0.55f)
        assertNotNull(upperZone)
        assertEquals("sedang-2", upperZone.id)
        assertEquals("upper", upperZone.region)

        // Lower: Y > 0.60 (misal Y = 0.65) -> sedang-1
        val lowerZone = HitTester.findZone(newMetroKit, 0.66f, 0.65f)
        assertNotNull(lowerZone)
        assertEquals("sedang-1", lowerZone.id)
        assertEquals("lower", lowerZone.region)

        // Garis tengah: Y == cy (Y = 0.60) -> harus masuk lower (sesuai spesifikasi Y >= cy)
        val centerLineZone = HitTester.findZone(newMetroKit, 0.66f, 0.60f)
        assertNotNull(centerLineZone)
        assertEquals("sedang-1", centerLineZone.id)
        assertEquals("lower", centerLineZone.region)
    }

    @Test
    fun testSingleModelKecil1() {
        // Kecil 1: center x=0.52, y=0.35, radius rx=0.17, ry=0.17
        val insideZone = HitTester.findZone(newMetroKit, 0.52f, 0.35f)
        assertNotNull(insideZone)
        assertEquals("kecil1-1", insideZone.id)
        assertEquals("full", insideZone.region)
    }

    @Test
    fun testPointOutsideAllDrumsReturnsNull() {
        // Pojok kiri atas
        assertNull(HitTester.findZone(newMetroKit, 0.01f, 0.01f))
        // Pojok kanan bawah
        assertNull(HitTester.findZone(newMetroKit, 0.99f, 0.99f))
        // Di luar kanvas
        assertNull(HitTester.findZone(newMetroKit, -0.1f, 0.5f))
        assertNull(HitTester.findZone(newMetroKit, 1.2f, 0.5f))
    }

    @Test
    fun testOverlapPriorityFollowsDrumsArrayOrder() {
        // Buat 2 kendang yang sengaja tumpang tindih
        val drumA = DrumData(
            id = "drum-A",
            model = "single",
            center = Point(0.5f, 0.5f),
            radius = Radius(0.2f, 0.2f),
            zones = listOf(ZoneData(id = "zone-A", region = "full"))
        )
        val drumB = DrumData(
            id = "drum-B",
            model = "single",
            center = Point(0.55f, 0.5f),
            radius = Radius(0.2f, 0.2f),
            zones = listOf(ZoneData(id = "zone-B", region = "full"))
        )

        // Kasus 1: Array order [A, B] -> titik di tengah (x=0.525, y=0.5) harus mengembalikan zone-A
        val kitAB = KitData(
            formatVersion = 1,
            kitId = "overlap-test-1",
            name = "Overlap Test",
            drums = listOf(drumA, drumB)
        )
        val hitAB = HitTester.findZone(kitAB, 0.525f, 0.5f)
        assertNotNull(hitAB)
        assertEquals("zone-A", hitAB.id)

        // Kasus 2: Array order [B, A] -> titik yang sama harus mengembalikan zone-B
        val kitBA = KitData(
            formatVersion = 1,
            kitId = "overlap-test-2",
            name = "Overlap Test Reversed",
            drums = listOf(drumB, drumA)
        )
        val hitBA = HitTester.findZone(kitBA, 0.525f, 0.5f)
        assertNotNull(hitBA)
        assertEquals("zone-B", hitBA.id)
    }
}
