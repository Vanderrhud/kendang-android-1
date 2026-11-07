package com.vrkendang.kitcore.validation

import com.vrkendang.kitcore.model.*
import kotlin.test.*

class KitValidatorTest {

    private fun createValidKit(): KitData {
        return KitData(
            formatVersion = 1,
            kitId = "valid-kit",
            name = "Valid Kit",
            version = 1,
            canvasAspect = 2.0f,
            drums = listOf(
                DrumData(
                    id = "bass",
                    model = "bass",
                    center = Point(0.25f, 0.55f),
                    radius = Radius(0.30f, 0.30f),
                    divider = DividerData(-0.30f, 0.0f, 0.80f),
                    zones = listOf(
                        ZoneData(id = "bass-1", region = "main", sound = null, gain = 1.0f),
                        ZoneData(id = "bass-2", region = "crescent", band = 0, sound = null, gain = 1.0f)
                    )
                ),
                DrumData(
                    id = "sedang",
                    model = "split",
                    center = Point(0.60f, 0.50f),
                    radius = Radius(0.20f, 0.20f),
                    zones = listOf(
                        ZoneData(id = "sedang-1", region = "lower", sound = null, gain = 1.0f),
                        ZoneData(id = "sedang-2", region = "upper", sound = null, gain = 1.0f)
                    )
                ),
                DrumData(
                    id = "kecil1",
                    model = "single",
                    center = Point(0.50f, 0.30f),
                    radius = Radius(0.15f, 0.15f),
                    zones = listOf(
                        ZoneData(id = "kecil1-1", region = "full", sound = null, gain = 1.0f)
                    )
                )
            )
        )
    }

    @Test
    fun testValidKitPasses() {
        val kit = createValidKit()
        val errors = KitValidator.validate(kit)
        assertTrue(errors.isEmpty(), "Valid kit should have no errors: $errors")
    }

    @Test
    fun testIdentityViolations() {
        // Invalid kitId (huruf besar & spasi)
        val kitInvalidId = createValidKit().copy(kitId = "Invalid Kit_Id")
        val errorsId = KitValidator.validate(kitInvalidId)
        assertTrue(errorsId.any { it.contains("kitId") })

        // formatVersion != 1
        val kitInvalidVer = createValidKit().copy(formatVersion = 2)
        val errorsVer = KitValidator.validate(kitInvalidVer)
        assertTrue(errorsVer.any { it.contains("formatVersion") })

        // Duplikasi id drum
        val kitDupDrum = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0],
                createValidKit().drums[0]
            )
        )
        val errorsDupDrum = KitValidator.validate(kitDupDrum)
        assertTrue(errorsDupDrum.any { it.contains("Duplikasi id drum") })

        // Duplikasi id zona
        val kitDupZone = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    zones = listOf(
                        ZoneData("same-id", "main"),
                        ZoneData("same-id", "crescent", band = 0)
                    )
                )
            )
        )
        val errorsDupZone = KitValidator.validate(kitDupZone)
        assertTrue(errorsDupZone.any { it.contains("Duplikasi id zona") })
    }

    @Test
    fun testGeometryRangeViolations() {
        // Center di luar rentang -3.0..3.0
        val kitBadCenter = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(center = Point(3.5f, -3.2f))
            )
        )
        val errorsCenter = KitValidator.validate(kitBadCenter)
        assertTrue(errorsCenter.any { it.contains("center") })

        // Radius <= 0 atau > 5.0
        val kitBadRadius = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(radius = Radius(-0.1f, 5.5f))
            )
        )
        val errorsRadius = KitValidator.validate(kitBadRadius)
        assertTrue(errorsRadius.any { it.contains("Radius") })

        // Center sebagian keluar kanvas (-0.5, 1.2) dan radius perbesaran skala (1.5) tetap valid
        val kitAllowedGeometry = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    center = Point(-0.5f, 1.2f),
                    radius = Radius(1.5f, 1.5f)
                )
            )
        )
        val errorsAllowed = KitValidator.validate(kitAllowedGeometry)
        assertTrue(errorsAllowed.isEmpty(), "Geometry within extended range should be valid: $errorsAllowed")

        // canvasAspect <= 0
        val kitBadAspect = createValidKit().copy(canvasAspect = -1.0f)
        val errorsAspect = KitValidator.validate(kitBadAspect)
        assertTrue(errorsAspect.any { it.contains("canvasAspect") })
    }

    @Test
    fun testGainLimitViolations() {
        // gain < 0.0 atau > 2.0
        val kitBadGain = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    zones = listOf(
                        ZoneData(id = "bass-1", region = "main", gain = 2.5f)
                    )
                )
            )
        )
        val errorsGain = KitValidator.validate(kitBadGain)
        assertTrue(errorsGain.any { it.contains("gain") })
    }

    @Test
    fun testDividerRangeViolations() {
        // Bass tanpa divider
        val kitNoDiv = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(divider = null)
            )
        )
        val errorsNoDiv = KitValidator.validate(kitNoDiv)
        assertTrue(errorsNoDiv.any { it.contains("divider") })

        // divider dx/dy di luar -3.0..3.0 atau r <= 0 atau > 3.0
        val kitBadDiv = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    divider = DividerData(dx = 4.0f, dy = 0.0f, r = 0.0f)
                )
            )
        )
        val errorsBadDiv = KitValidator.validate(kitBadDiv)
        assertTrue(errorsBadDiv.any { it.contains("divider") })

        // divider rx/ry di luar batas
        val kitBadRx = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    divider = DividerData(dx = 0.0f, dy = 0.0f, r = 0.8f, rx = 4.0f, ry = -1.0f)
                )
            )
        )
        val errorsBadRx = KitValidator.validate(kitBadRx)
        assertTrue(errorsBadRx.any { it.contains("rx") || it.contains("ry") })

        // divider split di luar 0.0..1.0
        val kitBadSplitVal = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    divider = DividerData(dx = 0.0f, dy = 0.0f, r = 0.8f, splits = listOf(1.5f))
                )
            )
        )
        val errorsBadSplitVal = KitValidator.validate(kitBadSplitVal)
        assertTrue(errorsBadSplitVal.any { it.contains("split") })
    }

    @Test
    fun testBassZoneCountValidations() {
        // Bass dengan 1 zona (valid)
        val kitBass1 = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    zones = listOf(ZoneData(id = "bass-1", region = "main"))
                )
            )
        )
        assertTrue(KitValidator.validate(kitBass1).isEmpty(), "Bass dengan 1 zona harus valid")

        // Bass dengan > 5 zona (tidak valid)
        val sixZones = (1..6).map {
            ZoneData(id = "bass-$it", region = if (it == 1) "main" else "crescent", band = if (it > 1) (it - 2).coerceIn(0, 3) else null)
        }
        val kitBass6 = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(zones = sixZones)
            )
        )
        val errors6 = KitValidator.validate(kitBass6)
        assertTrue(errors6.any { it.contains("1 sampai 5 zona") })
    }

    @Test
    fun testModelRegionViolations() {
        // Split dengan region selain upper/lower
        val kitBadSplit = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[1].copy(
                    zones = listOf(ZoneData(id = "sedang-1", region = "invalid_region"))
                )
            )
        )
        val errorsSplit = KitValidator.validate(kitBadSplit)
        assertTrue(errorsSplit.any { it.contains("upper") })

        // Bass crescent tanpa band
        val kitNoBand = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    zones = listOf(ZoneData(id = "bass-2", region = "crescent", band = null))
                )
            )
        )
        val errorsNoBand = KitValidator.validate(kitNoBand)
        assertTrue(errorsNoBand.any { it.contains("band") })

        // Single dengan region selain full
        val kitBadSingle = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[2].copy(
                    zones = listOf(ZoneData(id = "kecil1-1", region = "half"))
                )
            )
        )
        val errorsSingle = KitValidator.validate(kitBadSingle)
        assertTrue(errorsSingle.any { it.contains("full") })
    }

    @Test
    fun testSoundPathViolations() {
        // Path traversal
        val kitTraversal = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    zones = listOf(ZoneData(id = "bass-1", region = "main", sound = "../evil.wav"))
                )
            )
        )
        val errorsTraversal = KitValidator.validate(kitTraversal)
        assertTrue(errorsTraversal.any { it.contains("..") })

        // Absolute path
        val kitAbs = createValidKit().copy(
            drums = listOf(
                createValidKit().drums[0].copy(
                    zones = listOf(ZoneData(id = "bass-1", region = "main", sound = "/system/etc/evil.wav"))
                )
            )
        )
        val errorsAbs = KitValidator.validate(kitAbs)
        assertTrue(errorsAbs.any { it.contains("absolut") })
    }

    @Test
    fun testSplitRadialZoneCountAndAngleValidations() {
        val baseKit = createValidKit()
        // Split dengan 3 zona dan angle valid (valid)
        val kitSplit3 = baseKit.copy(
            drums = listOf(
                DrumData(
                    id = "sedang",
                    model = "split",
                    center = Point(0.6f, 0.5f),
                    radius = Radius(0.2f, 0.2f),
                    divider = DividerData(angle = 45f),
                    zones = listOf(
                        ZoneData(id = "sedang-1", region = "sector", band = 0),
                        ZoneData(id = "sedang-2", region = "sector", band = 1),
                        ZoneData(id = "sedang-3", region = "sector", band = 2)
                    )
                )
            )
        )
        assertTrue(KitValidator.validate(kitSplit3).isEmpty(), "Split dengan 3 zona harus valid")

        // Split dengan 5 zona (tidak valid, max 4)
        val fiveZones = (1..5).map {
            ZoneData(id = "sedang-$it", region = "sector", band = it - 1)
        }
        val kitSplit5 = baseKit.copy(
            drums = listOf(
                DrumData(
                    id = "sedang",
                    model = "split",
                    center = Point(0.6f, 0.5f),
                    radius = Radius(0.2f, 0.2f),
                    zones = fiveZones
                )
            )
        )
        val errors5 = KitValidator.validate(kitSplit5)
        assertTrue(errors5.any { it.contains("1 sampai 4 zona") })

        // Angle di luar 0..360
        val kitBadAngle = kitSplit3.copy(
            drums = listOf(
                kitSplit3.drums[0].copy(
                    divider = DividerData(angle = 400f)
                )
            )
        )
        val errorsAngle = KitValidator.validate(kitBadAngle)
        assertTrue(errorsAngle.any { it.contains("angle") })
    }

    @Test
    fun testDrumImageValidation() {
        val baseKit = createValidKit()

        // Valid image: persis images/<drumId>.png
        val validImageKit = baseKit.copy(
            drums = baseKit.drums.map { drum ->
                drum.copy(image = "images/${drum.id}.png")
            }
        )
        val validErrors = KitValidator.validate(validImageKit)
        assertTrue(validErrors.isEmpty(), "Valid images should pass: $validErrors")

        // Image null juga harus valid
        val nullImageKit = baseKit.copy(
            drums = baseKit.drums.map { drum ->
                drum.copy(image = null)
            }
        )
        assertTrue(KitValidator.validate(nullImageKit).isEmpty())

        // Nama tidak identik dengan drum.id
        val mismatchKit = baseKit.copy(
            drums = listOf(
                baseKit.drums[0].copy(image = "images/other.png")
            )
        )
        val mismatchErrors = KitValidator.validate(mismatchKit)
        assertTrue(mismatchErrors.any { it.contains("harus persis 'images/bass.png'") })

        // Path absolut
        val absoluteKit = baseKit.copy(
            drums = listOf(
                baseKit.drums[0].copy(image = "/images/bass.png")
            )
        )
        val absErrors = KitValidator.validate(absoluteKit)
        assertTrue(absErrors.any { it.contains("tidak boleh absolut") })

        // Path traversal
        val traversalKit = baseKit.copy(
            drums = listOf(
                baseKit.drums[0].copy(image = "images/../bass.png")
            )
        )
        val traversalErrors = KitValidator.validate(traversalKit)
        assertTrue(traversalErrors.any { it.contains("tidak boleh mengandung '..'") })
    }

    @Test
    fun testValidAndInvalidDrumRotation() {
        val baseKit = createValidKit()

        // Nilai valid: 0.0, 45.0, 360.0
        val validKit = baseKit.copy(
            drums = baseKit.drums.mapIndexed { idx, drum ->
                when (idx) {
                    0 -> drum.copy(rotation = 0.0f)
                    1 -> drum.copy(rotation = 45.0f)
                    else -> drum.copy(rotation = 360.0f)
                }
            }
        )
        val validErrors = KitValidator.validate(validKit)
        assertTrue(validErrors.isEmpty(), "Valid rotation should pass: $validErrors")

        // Di bawah 0.0 (< 0)
        val negativeRotKit = baseKit.copy(
            drums = listOf(baseKit.drums[0].copy(rotation = -1.0f))
        )
        val negativeErrors = KitValidator.validate(negativeRotKit)
        assertTrue(negativeErrors.any { it.contains("Rotasi drum 'bass' harus di rentang 0.0..360.0") })

        // Di atas 360.0 (> 360)
        val overRotKit = baseKit.copy(
            drums = listOf(baseKit.drums[0].copy(rotation = 360.1f))
        )
        val overErrors = KitValidator.validate(overRotKit)
        assertTrue(overErrors.any { it.contains("Rotasi drum 'bass' harus di rentang 0.0..360.0") })
    }
}
