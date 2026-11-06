package com.vrkendang.kitcore.packageio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class KitPackageReaderTest {

    private fun createValidKitJson(): String {
        return """
            {
              "formatVersion": 1,
              "kitId": "test-kit",
              "name": "Test Kit",
              "version": 1,
              "canvasAspect": 2.0,
              "drums": [
                {
                  "id": "kecil1",
                  "model": "single",
                  "center": { "x": 0.5, "y": 0.5 },
                  "radius": { "rx": 0.2, "ry": 0.2 },
                  "zones": [
                    { "id": "kecil1-1", "region": "full", "sound": "sounds/kecil1-1.wav", "gain": 1.0 }
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun createZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun testValidPackageReadingAndExtraction() {
        val kitJson = createValidKitJson().toByteArray(Charsets.UTF_8)
        val dummyWav = ByteArray(100) { 0 }

        val zipBytes = createZip(mapOf(
            "kit.json" to kitJson,
            "sounds/kecil1-1.wav" to dummyWav
        ))

        val extracted = KitPackageReader.read(ByteArrayInputStream(zipBytes))
        assertEquals("test-kit", extracted.kitData.kitId)
        assertTrue(extracted.wavFiles.containsKey("sounds/kecil1-1.wav"))

        val tempDir = Files.createTempDirectory("vrkendang-test").toFile()
        try {
            val kitFromDisk = KitPackageReader.extract(ByteArrayInputStream(zipBytes), tempDir)
            assertEquals("test-kit", kitFromDisk.kitId)
            assertTrue(File(tempDir, "kit.json").exists())
            assertTrue(File(tempDir, "sounds/kecil1-1.wav").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPathTraversalRejected() {
        val kitJson = createValidKitJson().toByteArray(Charsets.UTF_8)
        val zipBytes = createZip(mapOf(
            "kit.json" to kitJson,
            "../evil.wav" to ByteArray(10)
        ))

        val ex = assertFailsWith<SecurityException> {
            KitPackageReader.read(ByteArrayInputStream(zipBytes))
        }
        assertTrue(ex.message!!.contains("path traversal"))
    }

    @Test
    fun testAbsolutePathRejected() {
        val kitJson = createValidKitJson().toByteArray(Charsets.UTF_8)
        val zipBytes = createZip(mapOf(
            "kit.json" to kitJson,
            "/etc/evil.wav" to ByteArray(10)
        ))

        val ex = assertFailsWith<SecurityException> {
            KitPackageReader.read(ByteArrayInputStream(zipBytes))
        }
        assertTrue(ex.message!!.contains("path traversal atau path absolut"))
    }

    @Test
    fun testExceedingMaxEntriesRejected() {
        val entries = mutableMapOf<String, ByteArray>()
        entries["kit.json"] = createValidKitJson().toByteArray(Charsets.UTF_8)
        for (i in 1..105) {
            entries["sounds/zone-$i.wav"] = ByteArray(10)
        }

        val zipBytes = createZip(entries)
        val ex = assertFailsWith<SecurityException> {
            KitPackageReader.read(ByteArrayInputStream(zipBytes))
        }
        assertTrue(ex.message!!.contains("jumlah entri melebihi batas"))
    }

    @Test
    fun testExceedingWavSizeRejected() {
        // WAV > 5 MB
        val kitJson = createValidKitJson().toByteArray(Charsets.UTF_8)
        val largeWav = ByteArray(6 * 1024 * 1024) // 6 MB

        val zipBytes = createZip(mapOf(
            "kit.json" to kitJson,
            "sounds/kecil1-1.wav" to largeWav
        ))

        val ex = assertFailsWith<SecurityException> {
            KitPackageReader.read(ByteArrayInputStream(zipBytes))
        }
        assertTrue(ex.message!!.contains("melebihi batas maksimum"))
    }

    @Test
    fun testZipBombRejected() {
        // Buat file terkompresi yang mengembang > 100 MB saat didekompresi
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).apply {
            setLevel(Deflater.BEST_COMPRESSION)
        }.use { zos ->
            zos.putNextEntry(ZipEntry("kit.json"))
            zos.write(createValidKitJson().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 110 MB data nol yang terkompresi menjadi sangat kecil
            zos.putNextEntry(ZipEntry("sounds/bomb.wav"))
            val zeroChunk = ByteArray(1024 * 1024) // 1 MB nol
            repeat(110) {
                zos.write(zeroChunk)
            }
            zos.closeEntry()
        }

        val zipBombBytes = baos.toByteArray()
        val ex = assertFailsWith<SecurityException> {
            KitPackageReader.read(ByteArrayInputStream(zipBombBytes))
        }
        assertTrue(ex.message!!.contains("total ukuran dekompresi melebihi batas") || ex.message!!.contains("melebihi batas"))
    }

    @Test
    fun testOversizedPngRejected() {
        val kitJson = createValidKitJson().toByteArray(Charsets.UTF_8)
        val largePng = ByteArray(3 * 1024 * 1024) // 3 MB (> 2 MB)

        val zipBytes = createZip(mapOf(
            "kit.json" to kitJson,
            "images/kecil1.png" to largePng
        ))

        val ex = assertFailsWith<SecurityException> {
            KitPackageReader.read(ByteArrayInputStream(zipBytes))
        }
        assertTrue(ex.message!!.contains("file PNG") && ex.message!!.contains("melebihi batas maksimum"))
    }

    @Test
    fun testImagesPathTraversalRejected() {
        val kitJson = createValidKitJson().toByteArray(Charsets.UTF_8)
        val dummyPng = ByteArray(100) { 0 }

        val zipBytes = createZip(mapOf(
            "kit.json" to kitJson,
            "images/../escape.png" to dummyPng
        ))

        val ex = assertFailsWith<SecurityException> {
            KitPackageReader.read(ByteArrayInputStream(zipBytes))
        }
        assertTrue(ex.message!!.contains("path traversal"))
    }
}
