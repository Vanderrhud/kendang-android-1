package com.vrkendang.kitcore.factory

import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.packageio.KitPackageReader
import com.vrkendang.kitcore.packageio.KitPackageWriter
import com.vrkendang.kitcore.validation.KitValidator
import java.io.ByteArrayInputStream
import kotlin.test.*

class KitFactoryAndWriterTest {

    @Test
    fun testKitFactoryCreatesValidKitFromTemplate() {
        val newKit = KitFactory.createFromTemplate("custom-metro", "Custom Metro")
        assertEquals("custom-metro", newKit.kitId)
        assertEquals("Custom Metro", newKit.name)
        assertEquals(4, newKit.drums.size)

        // Verifikasi semua sound null
        for (drum in newKit.drums) {
            for (zone in drum.zones) {
                assertNull(zone.sound)
            }
        }

        // Lolos validator
        val errors = KitValidator.validate(newKit)
        assertTrue(errors.isEmpty(), "Kit baru harus lolos validasi: $errors")
    }

    @Test
    fun testKitFactoryRejectsDuplicateBuiltInKitId() {
        assertFailsWith<IllegalArgumentException> {
            KitFactory.createFromTemplate("new-metro", "Duplicate New Metro")
        }
    }

    @Test
    fun testPackageWriterAndReaderRoundTripIdentical() {
        val kit = KitFactory.createFromTemplate("my-kit", "My Kit")
        // Pasang 1 sound
        val modifiedDrums = kit.drums.map { drum ->
            if (drum.id == "kecil1") {
                drum.copy(
                    zones = drum.zones.map { it.copy(sound = "sounds/kecil1-1.wav") }
                )
            } else {
                drum
            }
        }
        val kitWithSound = kit.copy(drums = modifiedDrums)

        val dummyWav = ByteArray(50) { 1 }
        val wavMap = mapOf(
            "sounds/kecil1-1.wav" to dummyWav,
            "sounds/orphan.wav" to ByteArray(100) { 9 } // WAV yatim, tidak boleh ikut
        )

        // Tulis paket
        val zipBytes = KitPackageWriter.toByteArray(kitWithSound) { path -> wavMap[path] }

        // Baca ulang paket dengan KitPackageReader
        val extracted = KitPackageReader.read(ByteArrayInputStream(zipBytes))

        assertEquals(kitWithSound.kitId, extracted.kitData.kitId)
        assertEquals(kitWithSound.drums.size, extracted.kitData.drums.size)
        assertTrue(extracted.wavFiles.containsKey("sounds/kecil1-1.wav"))
        assertEquals(dummyWav.size, extracted.wavFiles["sounds/kecil1-1.wav"]!!.size)

        // Pastikan WAV yatim tidak ikut dikemas
        assertFalse(extracted.wavFiles.containsKey("sounds/orphan.wav"), "WAV yatim tidak boleh ikut dikemas ke paket")
    }

    @Test
    fun testChangingOrClearingSoundUpdatesPackageFiles() {
        val kit = KitFactory.createFromTemplate("dynamic-kit", "Dynamic Kit")

        // 1. Awalnya semua sound null -> ZIP hanya berisi kit.json
        val emptyZip = KitPackageWriter.toByteArray(kit) { null }
        val extractedEmpty = KitPackageReader.read(ByteArrayInputStream(emptyZip))
        assertTrue(extractedEmpty.wavFiles.isEmpty())

        // 2. Isi sound bass-1 -> ZIP berisi kit.json dan sounds/bass-1.wav
        val kitWithBass = kit.copy(
            drums = kit.drums.map { drum ->
                if (drum.id == "bass") {
                    drum.copy(
                        zones = drum.zones.map { zone ->
                            if (zone.id == "bass-1") zone.copy(sound = "sounds/bass-1.wav") else zone
                        }
                    )
                } else drum
            }
        )
        val bassWav = ByteArray(40) { 2 }
        val bassZip = KitPackageWriter.toByteArray(kitWithBass) { if (it == "sounds/bass-1.wav") bassWav else null }
        val extractedBass = KitPackageReader.read(ByteArrayInputStream(bassZip))
        assertEquals(1, extractedBass.wavFiles.size)
        assertTrue(extractedBass.wavFiles.containsKey("sounds/bass-1.wav"))

        // 3. Kosongkan kembali -> ZIP kembali tanpa sound
        val clearedZip = KitPackageWriter.toByteArray(kit) { null }
        val extractedCleared = KitPackageReader.read(ByteArrayInputStream(clearedZip))
        assertTrue(extractedCleared.wavFiles.isEmpty())
    }
}
