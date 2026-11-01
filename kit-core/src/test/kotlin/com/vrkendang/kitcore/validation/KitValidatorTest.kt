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
}
