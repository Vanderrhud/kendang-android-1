package com.vrkendang.kitcore.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KitJsonParserTest {

    @Test
    fun testParseNewMetroKit() {
        val newMetroJson = """
            {
              "formatVersion": 1,
              "kitId": "new-metro",
              "name": "New Metro",
              "version": 1,
              "canvasAspect": 2.0,
              "drums": [
                {
                  "id": "bass",
                  "model": "bass",
                  "center": { "x": 0.25, "y": 0.55 },
                  "radius": { "rx": 0.30, "ry": 0.30 },
                  "divider": { "dx": -0.30, "dy": 0.0, "r": 0.80 },
                  "zones": [
                    { "id": "bass-1", "region": "main", "sound": null, "gain": 1.0 },
                    { "id": "bass-2", "region": "crescent", "band": 0, "sound": null, "gain": 1.0 },
                    { "id": "bass-3", "region": "crescent", "band": 1, "sound": null, "gain": 1.0 },
                    { "id": "bass-4", "region": "crescent", "band": 2, "sound": null, "gain": 1.0 },
                    { "id": "bass-5", "region": "crescent", "band": 3, "sound": null, "gain": 1.0 }
                  ]
                },
                {
                  "id": "kecil1",
                  "model": "single",
                  "center": { "x": 0.52, "y": 0.35 },
                  "radius": { "rx": 0.17, "ry": 0.17 },
                  "zones": [
                    { "id": "kecil1-1", "region": "full", "sound": null, "gain": 1.0 }
                  ]
                },
                {
                  "id": "sedang",
                  "model": "split",
                  "center": { "x": 0.66, "y": 0.60 },
                  "radius": { "rx": 0.20, "ry": 0.20 },
                  "zones": [
                    { "id": "sedang-1", "region": "lower", "sound": null, "gain": 1.0 },
                    { "id": "sedang-2", "region": "upper", "sound": null, "gain": 1.0 }
                  ]
                },
                {
                  "id": "kecil2",
                  "model": "split",
                  "center": { "x": 0.82, "y": 0.38 },
                  "radius": { "rx": 0.17, "ry": 0.17 },
                  "zones": [
                    { "id": "kecil2-1", "region": "lower", "sound": null, "gain": 1.0 },
                    { "id": "kecil2-2", "region": "upper", "sound": null, "gain": 1.0 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val kit = KitJsonParser.parse(newMetroJson)
        assertEquals("new-metro", kit.kitId)
        assertEquals(4, kit.drums.size)
        val totalZones = kit.drums.sumOf { it.zones.size }
        assertEquals(10, totalZones)

        // Verifikasi semua zona null
        kit.drums.forEach { drum ->
            drum.zones.forEach { zone ->
                assertNull(zone.sound)
            }
        }
    }

    @Test
    fun testParsePartiallyAssignedKit() {
        val partiallyAssignedJson = """
            {
              "formatVersion": 1,
              "kitId": "custom-kit",
              "name": "Custom Kit",
              "drums": [
                {
                  "id": "bass",
                  "model": "bass",
                  "center": { "x": 0.25, "y": 0.55 },
                  "radius": { "rx": 0.30, "ry": 0.30 },
                  "divider": { "dx": -0.30, "dy": 0.0, "r": 0.80 },
                  "zones": [
                    { "id": "bass-1", "region": "main", "sound": "sounds/bass-1.wav", "gain": 1.5 },
                    { "id": "bass-2", "region": "crescent", "band": 0, "sound": null, "gain": 1.0 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val kit = KitJsonParser.parse(partiallyAssignedJson)
        assertEquals("custom-kit", kit.kitId)
        val bass1 = kit.drums[0].zones[0]
        assertEquals("sounds/bass-1.wav", bass1.sound)
        assertEquals(1.5f, bass1.gain)

        val bass2 = kit.drums[0].zones[1]
        assertNull(bass2.sound)
    }

    @Test
    fun testParseCorruptJsonThrowsException() {
        val corruptJson = "{ \"kitId\": \"broken\", "
        assertFailsWith<IllegalArgumentException> {
            KitJsonParser.parse(corruptJson)
        }
    }

    @Test
    fun testParseMissingRequiredFieldThrowsException() {
        // Hilang drums
        val missingDrumsJson = """
            {
              "formatVersion": 1,
              "kitId": "missing-drums",
              "name": "No Drums"
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            KitJsonParser.parse(missingDrumsJson)
        }
    }

    @Test
    fun testParseOldKitWithoutImageHasNullImageAndOutputsExplicitNull() {
        val oldKitJson = """
            {
              "formatVersion": 1,
              "kitId": "old-kit",
              "name": "Old Kit",
              "drums": [
                {
                  "id": "bass",
                  "model": "bass",
                  "center": { "x": 0.25, "y": 0.55 },
                  "radius": { "rx": 0.30, "ry": 0.30 },
                  "zones": [
                    { "id": "bass-1", "region": "main", "sound": null, "gain": 1.0 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = KitJsonParser.parse(oldKitJson)
        assertNull(parsed.drums[0].image)

        val stringified = KitJsonParser.stringify(parsed)
        kotlin.test.assertTrue(stringified.contains("\"image\": null"), "Stringify harus menyertakan explicit null image")
    }

    @Test
    fun testParseAndStringifyKitWithImage() {
        val kitWithImageJson = """
            {
              "formatVersion": 1,
              "kitId": "image-kit",
              "name": "Image Kit",
              "drums": [
                {
                  "id": "bass",
                  "model": "bass",
                  "center": { "x": 0.25, "y": 0.55 },
                  "radius": { "rx": 0.30, "ry": 0.30 },
                  "image": "images/bass.png",
                  "zones": [
                    { "id": "bass-1", "region": "main", "sound": null, "gain": 1.0 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = KitJsonParser.parse(kitWithImageJson)
        assertEquals("images/bass.png", parsed.drums[0].image)

        val stringified = KitJsonParser.stringify(parsed)
        kotlin.test.assertTrue(stringified.contains("\"image\": \"images/bass.png\""))
    }

    @Test
    fun testParseKitWithoutRotationDefaultsToZero() {
        val oldKitJson = """
            {
              "formatVersion": 1,
              "kitId": "old-kit",
              "name": "Old Kit",
              "drums": [
                {
                  "id": "bass",
                  "model": "bass",
                  "center": { "x": 0.25, "y": 0.55 },
                  "radius": { "rx": 0.30, "ry": 0.30 },
                  "zones": [
                    { "id": "bass-1", "region": "main", "sound": null, "gain": 1.0 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = KitJsonParser.parse(oldKitJson)
        assertEquals(0.0f, parsed.drums[0].rotation)
    }

    @Test
    fun testParseAndRoundTripKitWithRotation() {
        val kitWithRotationJson = """
            {
              "formatVersion": 1,
              "kitId": "rot-kit",
              "name": "Rotation Kit",
              "drums": [
                {
                  "id": "sedang",
                  "model": "split",
                  "center": { "x": 0.60, "y": 0.60 },
                  "radius": { "rx": 0.25, "ry": 0.15 },
                  "rotation": 35.5,
                  "zones": [
                    { "id": "sedang-1", "region": "lower", "sound": null, "gain": 1.0 },
                    { "id": "sedang-2", "region": "upper", "sound": null, "gain": 1.0 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = KitJsonParser.parse(kitWithRotationJson)
        assertEquals(35.5f, parsed.drums[0].rotation)

        val stringified = KitJsonParser.stringify(parsed)
        val reparsed = KitJsonParser.parse(stringified)
        assertEquals(35.5f, reparsed.drums[0].rotation)
    }
}
