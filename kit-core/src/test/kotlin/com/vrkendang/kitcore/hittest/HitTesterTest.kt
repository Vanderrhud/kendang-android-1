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
    fun testSplitRadialSectorsWithAngleRotation() {
        // Kit dengan drum radial 4 zona, aspect 1.0 agar xu = x
        val radial4Kit = KitData(
            formatVersion = 1,
            kitId = "radial-4",
            name = "Radial 4",
            canvasAspect = 1.0f,
            drums = listOf(
                DrumData(
                    id = "kendang-4",
                    model = "split",
                    center = Point(0.50f, 0.50f),
                    radius = Radius(0.20f, 0.20f),
                    divider = DividerData(angle = 0f),
                    zones = listOf(
                        ZoneData(id = "z-0", region = "sector", band = 0),
                        ZoneData(id = "z-1", region = "sector", band = 1),
                        ZoneData(id = "z-2", region = "sector", band = 2),
                        ZoneData(id = "z-3", region = "sector", band = 3)
                    )
                )
            )
        )

        // Sudut rotasi 0 derajat:
        // z-0: 0..90 deg (kanan-bawah: x=0.6, y=0.6)
        val hit0 = HitTester.findZone(radial4Kit, 0.60f, 0.60f)
        assertNotNull(hit0)
        assertEquals("z-0", hit0.id)

        // z-1: 90..180 deg (kiri-bawah: x=0.4, y=0.6)
        val hit1 = HitTester.findZone(radial4Kit, 0.40f, 0.60f)
        assertNotNull(hit1)
        assertEquals("z-1", hit1.id)

        // z-2: 180..270 deg (kiri-atas: x=0.4, y=0.4)
        val hit2 = HitTester.findZone(radial4Kit, 0.40f, 0.40f)
        assertNotNull(hit2)
        assertEquals("z-2", hit2.id)

        // z-3: 270..360 deg (kanan-atas: x=0.6, y=0.4)
        val hit3 = HitTester.findZone(radial4Kit, 0.60f, 0.40f)
        assertNotNull(hit3)
        assertEquals("z-3", hit3.id)

        // Uji rotasi garis vertikal pada 2 zona (angle = 90)
        val split2Rotated = KitData(
            formatVersion = 1,
            kitId = "split-rot",
            name = "Split Rotated",
            canvasAspect = 1.0f,
            drums = listOf(
                DrumData(
                    id = "drum-rot",
                    model = "split",
                    center = Point(0.50f, 0.50f),
                    radius = Radius(0.20f, 0.20f),
                    divider = DividerData(angle = 90f),
                    zones = listOf(
                        ZoneData(id = "kiri", region = "sector", band = 0),
                        ZoneData(id = "kanan", region = "sector", band = 1)
                    )
                )
            )
        )

        // Titik sisi kiri (x=0.4, y=0.5 -> atan2=180 deg, relativeDeg = (180 - 90) = 90 deg -> index 0: kiri)
        val hitLeft = HitTester.findZone(split2Rotated, 0.40f, 0.50f)
        assertNotNull(hitLeft)
        assertEquals("kiri", hitLeft.id)

        // Titik sisi kanan (x=0.6, y=0.5 -> atan2=0 deg, relativeDeg = (0 - 90 + 360) = 270 deg -> index 1: kanan)
        val hitRight = HitTester.findZone(split2Rotated, 0.60f, 0.50f)
        assertNotNull(hitRight)
        assertEquals("kanan", hitRight.id)
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

    @Test
    fun testBassSingleZoneCoversFullDrum() {
        val bass1Zone = DrumData(
            id = "bass",
            model = "bass",
            center = Point(0.25f, 0.55f),
            radius = Radius(0.30f, 0.30f),
            zones = listOf(ZoneData(id = "bass-1", region = "main"))
        )
        val kitSingle = KitData(
            kitId = "single-bass",
            name = "Single Bass",
            canvasAspect = 2.0f,
            drums = listOf(bass1Zone)
        )

        // Titik tengah
        val hitCenter = HitTester.findZone(kitSingle, 0.25f, 0.55f)
        assertNotNull(hitCenter)
        assertEquals("bass-1", hitCenter.id)

        // Titik tepi (yang tadinya crescent)
        val hitEdge = HitTester.findZone(kitSingle, 0.35f, 0.35f)
        assertNotNull(hitEdge)
        assertEquals("bass-1", hitEdge.id, "Bass dengan 1 zona harus memicu bass-1 di seluruh permukaan")
    }

    @Test
    fun testBassOvalDividerAndCustomSplits() {
        // Bass dengan oval rx=0.50, ry=0.90 (oval meninggi vertikal) dan 2 splits (3 zona crescent, total 4 zona)
        val bassOval = DrumData(
            id = "bass",
            model = "bass",
            center = Point(0.25f, 0.55f),
            radius = Radius(0.30f, 0.30f),
            divider = DividerData(
                dx = 0.0f,
                dy = 0.0f,
                r = 0.50f,
                rx = 0.40f,
                ry = 0.80f,
                splits = listOf(0.30f, 0.70f)
            ),
            zones = listOf(
                ZoneData(id = "bass-1", region = "main"),
                ZoneData(id = "bass-2", region = "crescent", band = 0),
                ZoneData(id = "bass-3", region = "crescent", band = 1),
                ZoneData(id = "bass-4", region = "crescent", band = 2)
            )
        )
        val kitOval = KitData(
            kitId = "oval-bass",
            name = "Oval Bass",
            canvasAspect = 2.0f,
            drums = listOf(bassOval)
        )

        // Pusat: x=0.25, y=0.55 -> harus bass-1
        val hitCenter = HitTester.findZone(kitOval, 0.25f, 0.55f)
        assertNotNull(hitCenter)
        assertEquals("bass-1", hitCenter.id)

        // Tepi kanan atas: Y < 0.30 split -> bass-2 (band 0)
        // Y rentang [0.25, 0.85], total 0.60. Y = 0.25 + 0.15*0.60 = 0.34
        val hitTop = HitTester.findZone(kitOval, 0.32f, 0.35f)
        assertNotNull(hitTop)
        assertEquals("crescent", hitTop.region)

        // Tepi tengah: 0.30 <= normalizedY < 0.70 -> bass-3 (band 1)
        val hitMid = HitTester.findZone(kitOval, 0.36f, 0.55f)
        assertNotNull(hitMid)
        assertEquals("bass-3", hitMid.id)
    }

    @Test
    fun testRotatedOvalHitTesting() {
        // Kendang oval mendatar rx=0.30, ry=0.10, aspect=1.0
        // Jika unrotated (rotation = 0):
        //   Titik (0.50 + 0.20, 0.50) -> dx=0.20 <= 0.30 (INSIDE)
        //   Titik (0.50, 0.50 + 0.20) -> dy=0.20 > 0.10 (OUTSIDE)
        // Jika dirotasi 90 derajat searah jarum jam:
        //   Sumbu panjang rx sekarang sejajar sumbu Y, sumbu pendek ry sejajar sumbu X.
        //   Titik (0.50 + 0.20, 0.50) -> rotDx=0, rotDy=-0.20 -> (0/0.3)^2 + (-0.2/0.1)^2 = 4.0 > 1.0 (OUTSIDE)
        //   Titik (0.50, 0.50 + 0.20) -> rotDx=0.20, rotDy=0 -> (0.2/0.3)^2 + (0/0.1)^2 = 0.44 <= 1.0 (INSIDE)
        val rotatedOvalKit = KitData(
            formatVersion = 1,
            kitId = "rotated-oval-kit",
            name = "Rotated Oval Kit",
            canvasAspect = 1.0f,
            drums = listOf(
                DrumData(
                    id = "drum-rot-oval",
                    model = "single",
                    center = Point(0.50f, 0.50f),
                    radius = Radius(0.30f, 0.10f),
                    rotation = 90.0f,
                    zones = listOf(ZoneData(id = "single-rot", region = "full"))
                )
            )
        )

        // Titik (0.50, 0.70): di dalam oval yang diputar 90 derajat
        val hitInside = HitTester.findZone(rotatedOvalKit, 0.50f, 0.70f)
        assertNotNull(hitInside, "Titik (0.50, 0.70) harus masuk kendang oval yang diputar 90 derajat")
        assertEquals("single-rot", hitInside.id)

        // Titik (0.70, 0.50): di luar oval yang diputar 90 derajat
        val hitOutside = HitTester.findZone(rotatedOvalKit, 0.70f, 0.50f)
        assertNull(hitOutside, "Titik (0.70, 0.50) harus berada di luar kendang oval yang diputar 90 derajat")
    }

    @Test
    fun testRotatedSplitDrumWithDividerAngle() {
        // Kendang model split (2 zona: upper dan lower dengan garis horizontal saat rotation=0, angle=0)
        // Jika drum.rotation = 90 derajat:
        // Titik lokal Y < cy (upper) yang awalnya di atas pusat sekarang berotasi 90 derajat searah jarum jam ke sisi KANAN pusat.
        // Titik lokal Y > cy (lower) yang awalnya di bawah pusat berotasi ke sisi KIRI pusat.
        val rotatedSplitKit = KitData(
            formatVersion = 1,
            kitId = "rotated-split-kit",
            name = "Rotated Split Kit",
            canvasAspect = 1.0f,
            drums = listOf(
                DrumData(
                    id = "split-rot",
                    model = "split",
                    center = Point(0.50f, 0.50f),
                    radius = Radius(0.20f, 0.20f),
                    rotation = 90.0f,
                    divider = DividerData(angle = 0f),
                    zones = listOf(
                        ZoneData(id = "zone-lower", region = "lower"),
                        ZoneData(id = "zone-upper", region = "upper")
                    )
                )
            )
        )

        // Titik di kiri pusat (0.35, 0.50) -> dx = -0.15, dy = 0.
        // rotDx = dx*cos(90) + dy*sin(90) = 0
        // rotDy = -dx*sin(90) + dy*cos(90) = -(-0.15) = +0.15 -> localYu = 0.50 + 0.15 = 0.65 > cy (lower)
        val hitLeft = HitTester.findZone(rotatedSplitKit, 0.35f, 0.50f)
        assertNotNull(hitLeft)
        assertEquals("zone-lower", hitLeft.id)

        // Titik di kanan pusat (0.65, 0.50) -> dx = +0.15, dy = 0.
        // rotDx = 0
        // rotDy = -(+0.15)*1 = -0.15 -> localYu = 0.50 - 0.15 = 0.35 < cy (upper)
        val hitRight = HitTester.findZone(rotatedSplitKit, 0.65f, 0.50f)
        assertNotNull(hitRight)
        assertEquals("zone-upper", hitRight.id)
    }

    @Test
    fun testRotatedBassDrum() {
        // Kendang bass dengan rotasi 180 derajat
        // Pusat C_div aslinya di sebelah kiri pusat kendang (dx = -0.30).
        // Ketika dirotasi 180 derajat, C_div berpindah ke sebelah kanan pusat kendang.
        val rotatedBass = DrumData(
            id = "bass-rot",
            model = "bass",
            center = Point(0.50f, 0.50f),
            radius = Radius(0.30f, 0.30f),
            rotation = 180.0f,
            divider = DividerData(dx = -0.30f, dy = 0.0f, r = 0.80f),
            zones = listOf(
                ZoneData(id = "bass-main", region = "main"),
                ZoneData(id = "bass-crescent", region = "crescent", band = 0)
            )
        )
        val kitBassRot = KitData(
            kitId = "rotated-bass-kit",
            name = "Rotated Bass Kit",
            canvasAspect = 1.0f,
            drums = listOf(rotatedBass)
        )

        // Pada rotasi 0, main zone ada di sebelah kiri (misal x = 0.40, y = 0.50).
        // Pada rotasi 180: titik (x = 0.60, y = 0.50) -> dx = 0.10.
        // rotDx = 0.10 * cos(180) = -0.10 -> localXu = 0.50 - 0.10 = 0.40 (di dalam main zone C_div!)
        val hitMainRot = HitTester.findZone(kitBassRot, 0.60f, 0.50f)
        assertNotNull(hitMainRot)
        assertEquals("bass-main", hitMainRot.id)
    }
}
