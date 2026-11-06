package com.vrkendang.kitauthoring

import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.parser.KitJsonParser
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DevImageInstallerTest {

    private fun loadNewMetroJson(): String {
        val candidates = listOf(
            File("src/main/assets/kits/new-metro/kit.json"),
            File("app/src/main/assets/kits/new-metro/kit.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("kit.json New Metro tidak ditemukan")
        return file.readText()
    }

    private fun buildMinimalPng(width: Int, height: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        ))
        val dos = DataOutputStream(baos)
        dos.writeInt(13)
        dos.write("IHDR".toByteArray(Charsets.US_ASCII))
        dos.writeInt(width)
        dos.writeInt(height)
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
    fun testInstallValidSquarePngSucceeds() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "dev-image-test-" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            File(tempDir, "kit.json").writeText(loadNewMetroJson())

            val installer = DevImageInstaller()
            val pngBytes = buildMinimalPng(512, 512)

            val result = installer.installPng(
                drumId = "bass",
                inputStream = ByteArrayInputStream(pngBytes),
                devKitDir = tempDir
            )

            assertTrue(result is DevImageInstaller.Result.Success)
            assertEquals("bass", result.drumId)

            // Periksa file tersimpan di images/bass.png
            val savedFile = File(tempDir, "images/bass.png")
            assertTrue(savedFile.exists())

            // Periksa kit.json diperbarui
            val updatedKit = KitJsonParser.parse(File(tempDir, "kit.json").readText())
            val bassDrum = updatedKit.drums.first { it.id == "bass" }
            assertEquals("images/bass.png", bassDrum.image)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testInstallNonSquarePngRejected() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "dev-image-nonsquare-" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            File(tempDir, "kit.json").writeText(loadNewMetroJson())

            val installer = DevImageInstaller()
            val nonSquarePng = buildMinimalPng(800, 600)

            val result = installer.installPng(
                drumId = "bass",
                inputStream = ByteArrayInputStream(nonSquarePng),
                devKitDir = tempDir
            )

            assertTrue(result is DevImageInstaller.Result.Failure)
            assertTrue(result.message.contains("harus berbentuk persegi (rasio 1:1"))

            // File tidak boleh tersimpan
            val savedFile = File(tempDir, "images/bass.png")
            assertFalse(savedFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testInstallOversizedPngRejected() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "dev-image-oversize-" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            File(tempDir, "kit.json").writeText(loadNewMetroJson())

            val installer = DevImageInstaller()
            val largeBytes = ByteArray(3 * 1024 * 1024) // 3 MB > 2 MB

            val result = installer.installPng(
                drumId = "bass",
                inputStream = ByteArrayInputStream(largeBytes),
                devKitDir = tempDir
            )

            assertTrue(result is DevImageInstaller.Result.Failure)
            assertTrue(result.message.contains("melebihi batas 2 MB"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testInstallInvalidPngRejected() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "dev-image-invalid-" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            File(tempDir, "kit.json").writeText(loadNewMetroJson())

            val installer = DevImageInstaller()
            val badBytes = "Bukan file PNG".toByteArray()

            val result = installer.installPng(
                drumId = "bass",
                inputStream = ByteArrayInputStream(badBytes),
                devKitDir = tempDir
            )

            assertTrue(result is DevImageInstaller.Result.Failure)
            assertTrue(result.message.contains("PNG"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testClearImageDeletesFileAndUpdatesKitJson() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "dev-image-clear-" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            File(tempDir, "kit.json").writeText(loadNewMetroJson())
            val imagesDir = File(tempDir, "images").apply { mkdirs() }
            val existingPng = File(imagesDir, "bass.png").apply { writeBytes(ByteArray(10)) }

            val installer = DevImageInstaller()
            val result = installer.clearImage(
                drumId = "bass",
                devKitDir = tempDir
            )

            assertTrue(result is DevImageInstaller.Result.Success)
            assertFalse(existingPng.exists(), "File PNG harus dihapus saat clearImage")

            val updatedKit = KitJsonParser.parse(File(tempDir, "kit.json").readText())
            val bassDrum = updatedKit.drums.first { it.id == "bass" }
            assertNull(bassDrum.image)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
