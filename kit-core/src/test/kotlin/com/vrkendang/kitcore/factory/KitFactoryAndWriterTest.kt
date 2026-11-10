package com.vrkendang.kitcore.factory

import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.packageio.KitPackageReader
import com.vrkendang.kitcore.packageio.KitPackageWriter
import com.vrkendang.kitcore.validation.KitValidator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

    private fun createMinimalPng(): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        ))
        val dos = java.io.DataOutputStream(baos)
        dos.writeInt(13)
        dos.write("IHDR".toByteArray(Charsets.US_ASCII))
        dos.writeInt(100)
        dos.writeInt(100)
        dos.writeByte(8)
        dos.writeByte(6)
        dos.writeByte(0)
        dos.writeByte(0)
        dos.writeByte(0)
        dos.writeInt(0)
        dos.flush()
        return baos.toByteArray()
    }

    @Test
    fun testPackageWriterIncludesReferencedPngAndExcludesOrphanPng() {
        val kit = KitFactory.createFromTemplate("png-kit", "PNG Kit")
        val kitWithPng = kit.copy(
            drums = kit.drums.map { drum ->
                if (drum.id == "kecil1") drum.copy(image = "images/kecil1.png") else drum
            }
        )

        val pngBytes = createMinimalPng()
        val orphanPngBytes = createMinimalPng()

        val imageMap = mapOf(
            "images/kecil1.png" to pngBytes,
            "images/orphan.png" to orphanPngBytes
        )

        val zipBytes = KitPackageWriter.toByteArray(
            kit = kitWithPng,
            wavProvider = { null },
            imageProvider = { path -> imageMap[path] }
        )

        val extracted = KitPackageReader.read(ByteArrayInputStream(zipBytes))
        assertEquals(kitWithPng.kitId, extracted.kitData.kitId)
        assertTrue(extracted.imageFiles.containsKey("images/kecil1.png"))
        assertFalse(extracted.imageFiles.containsKey("images/orphan.png"), "PNG yatim tidak boleh ikut dikemas")
        assertEquals(pngBytes.size, extracted.imageFiles["images/kecil1.png"]!!.size)
    }

    @Test
    fun testPackageWriterIncludesReferencedBodyPngAndExcludesOrphanPng() {
        val kit = KitFactory.createFromTemplate("body-kit", "Body Kit")
        val bodyData = com.vrkendang.kitcore.model.DrumBodyData(
            image = "images/sedang-body.png",
            width = 0.45f,
            height = 0.35f,
            offsetX = 0.0f,
            offsetY = 0.15f,
            rotation = 0.0f
        )
        val kitWithBody = kit.copy(
            drums = kit.drums.map { drum ->
                if (drum.id == "sedang") {
                    drum.copy(image = "images/sedang.png", body = bodyData)
                } else drum
            }
        )

        val skinPng = createMinimalPng()
        val bodyPng = createMinimalPng()
        val orphanPng = createMinimalPng()

        val imageMap = mapOf(
            "images/sedang.png" to skinPng,
            "images/sedang-body.png" to bodyPng,
            "images/orphan.png" to orphanPng
        )

        val zipBytes = KitPackageWriter.toByteArray(
            kit = kitWithBody,
            wavProvider = { null },
            imageProvider = { path -> imageMap[path] }
        )

        val extracted = KitPackageReader.read(ByteArrayInputStream(zipBytes))
        assertEquals(kitWithBody.kitId, extracted.kitData.kitId)
        assertTrue(extracted.imageFiles.containsKey("images/sedang.png"))
        assertTrue(extracted.imageFiles.containsKey("images/sedang-body.png"))
        assertFalse(extracted.imageFiles.containsKey("images/orphan.png"), "PNG yatim tidak boleh ikut dikemas")

        // Uji jika file PNG body yang dirujuk tidak tersedia di imageProvider -> ditolak oleh Writer
        assertFailsWith<IllegalArgumentException> {
            KitPackageWriter.toByteArray(
                kit = kitWithBody,
                wavProvider = { null },
                imageProvider = { path -> if (path == "images/sedang.png") skinPng else null }
            )
        }

        // Uji jika file PNG body yang dirujuk tidak ada di ZIP -> ditolak oleh Reader
        val corruptZipBaos = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(corruptZipBaos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("kit.json"))
            zos.write(com.vrkendang.kitcore.parser.KitJsonParser.stringify(kitWithBody).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("images/sedang.png"))
            zos.write(skinPng)
            zos.closeEntry()
            // Tanpa images/sedang-body.png
        }

        val ex = assertFailsWith<IllegalArgumentException> {
            KitPackageReader.read(ByteArrayInputStream(corruptZipBaos.toByteArray()))
        }
        assertTrue(ex.message!!.contains("File gambar body yang dirujuk tidak ditemukan"))
    }
}
