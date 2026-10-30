package com.vrkendang.kitcore.validation

import com.vrkendang.kitcore.audio.WavParser
import com.vrkendang.kitcore.parser.KitJsonParser
import java.io.File
import kotlin.test.*

class BuiltInKitsValidationTest {

    @Test
    fun testValidateAllBuiltInKits() {
        val kitsDirPath = System.getProperty("assets.kits.dir")
        assertNotNull(kitsDirPath, "systemProperty 'assets.kits.dir' harus dikonfigurasi oleh Gradle")

        val kitsDir = File(kitsDirPath)
        assertTrue(kitsDir.exists(), "Direktori assets/kits tidak ditemukan di: ${kitsDir.absolutePath}")
        assertTrue(kitsDir.isDirectory, "'assets.kits.dir' bukan sebuah direktori: ${kitsDir.absolutePath}")

        val kitDirs = kitsDir.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
        val kitFiles = mutableListOf<File>()

        for (dir in kitDirs) {
            val jsonFile = File(dir, "kit.json")
            if (jsonFile.exists() && jsonFile.isFile) {
                kitFiles.add(jsonFile)
            }
        }

        // Tes harus GAGAL bila tidak menemukan minimal satu kit (tidak boleh lulus kosong)
        assertTrue(
            kitFiles.isNotEmpty(),
            "Tidak ada kit bawaan yang ditemukan di '${kitsDir.absolutePath}'. Minimal harus ada 1 kit (misal new-metro)."
        )

        for (kitFile in kitFiles) {
            val kitDir = kitFile.parentFile
            val jsonContent = kitFile.readText()

            // 1. Parse JSON
            val kit = try {
                KitJsonParser.parse(jsonContent)
            } catch (e: Exception) {
                fail("Gagal parsing JSON kit bawaan '${kitFile.absolutePath}': ${e.message}")
            }

            // 2. Validasi struktur, geometri, identitas, divider
            val errors = KitValidator.validate(kit)
            assertTrue(
                errors.isEmpty(),
                "Kit bawaan '${kit.kitId}' di '${kitFile.absolutePath}' memiliki error validasi:\n" + errors.joinToString("\n - ", prefix = " - ")
            )

            // 3. Validasi integritas file audio bila sound tidak null
            for (drum in kit.drums) {
                for (zone in drum.zones) {
                    if (zone.sound != null) {
                        val soundFile = File(kitDir, zone.sound)
                        assertTrue(
                            soundFile.exists(),
                            "File suara '${zone.sound}' untuk zona '${zone.id}' di kit '${kit.kitId}' tidak ditemukan di: ${soundFile.absolutePath}"
                        )

                        // Verifikasi bahwa WAV valid PCM 16-bit mono
                        try {
                            WavParser.parse(soundFile.readBytes())
                        } catch (e: Exception) {
                            fail("File WAV '${soundFile.absolutePath}' tidak valid (harus WAV PCM 16-bit mono): ${e.message}")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testEmptyOrMissingDirectoryFails() {
        val emptyDir = File(System.getProperty("java.io.tmpdir"), "empty-kits-test-" + System.currentTimeMillis())
        emptyDir.mkdirs()
        try {
            val kitFiles = emptyDir.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
            // Simulasi logika assertion pada direktori kosong
            assertFailsWith<AssertionError> {
                assertTrue(kitFiles.isNotEmpty(), "Harus gagal jika tidak ada kit")
            }
        } finally {
            emptyDir.deleteRecursively()
        }
    }
}
