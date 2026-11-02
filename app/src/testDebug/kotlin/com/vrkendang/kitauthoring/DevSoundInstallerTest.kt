package com.vrkendang.kitauthoring

import com.vrkendang.audio.AudioEngineApi
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.parser.KitJsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevSoundInstallerTest {

    private class FakeAudioEngine : AudioEngineApi {
        val loadedSlots = mutableListOf<Int>()

        override fun loadSound(pcmData: ByteBuffer, frameCount: Int, sampleRate: Int, slotIndex: Int): Boolean {
            loadedSlots.add(slotIndex)
            return true
        }

        override fun releaseAll() {}
        override fun openStream(): Boolean = true
        override fun startStream(): Boolean = true
    }

    private fun loadNewMetroJson(): String {
        val candidates = listOf(
            File("src/main/assets/kits/new-metro/kit.json"),
            File("app/src/main/assets/kits/new-metro/kit.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("kit.json New Metro tidak ditemukan")
        val parsed = KitJsonParser.parse(file.readText())
        val clean = parsed.copy(
            drums = parsed.drums.map { drum ->
                drum.copy(zones = drum.zones.map { it.copy(sound = null) })
            }
        )
        return KitJsonParser.stringify(clean)
    }

    /** Membangun WAV PCM 16-bit mono minimal yang valid. */
    private fun buildValidWav(sampleRate: Int = 44100, samples: ShortArray = shortArrayOf(1234, -4321)): ByteArray {
        val pcm = ByteArrayOutputStream()
        for (sample in samples) {
            pcm.write(
                ByteBuffer.allocate(2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(sample)
                    .array()
            )
        }
        val pcmBytes = pcm.toByteArray()
        val fmtSize = 16
        val riffSize = 4 + (8 + fmtSize) + (8 + pcmBytes.size)
        val buffer = ByteBuffer.allocate(8 + riffSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(riffSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(fmtSize)
        buffer.putShort(1.toShort()) // PCM
        buffer.putShort(1.toShort()) // Mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // byte rate
        buffer.putShort(2.toShort()) // block align
        buffer.putShort(16.toShort()) // 16-bit
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmBytes.size)
        buffer.put(pcmBytes)

        return buffer.array()
    }

    @Test
    fun testInstallValidWavUpdatesKitJsonAndLoadsToEngine() {
        val tempDir = File.createTempFile("dev-kit-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val kitJsonFile = File(tempDir, "kit.json").apply {
                writeText(loadNewMetroJson())
            }

            val fakeEngine = FakeAudioEngine()
            val installer = DevSoundInstaller()
            val validWav = buildValidWav(sampleRate = 48000)

            val result = installer.installWav(
                zoneId = "bass-1",
                inputStream = ByteArrayInputStream(validWav),
                devKitDir = tempDir,
                slotIndex = 0,
                engine = fakeEngine
            )

            assertTrue(result is DevSoundInstaller.Result.Success)
            assertEquals("bass-1", result.zoneId)
            assertEquals(48000, result.sampleRate)

            // File audio tersimpan di sounds/bass-1.wav
            val soundFile = File(tempDir, "sounds/bass-1.wav")
            assertTrue(soundFile.exists(), "File sounds/bass-1.wav harus ada")
            assertEquals(validWav.size.toLong(), soundFile.length())

            // kit.json di disk diperbarui
            val updatedDiskKit = KitJsonParser.parse(kitJsonFile.readText())
            val bassZone = updatedDiskKit.drums.first { it.id == "bass" }.zones.first { it.id == "bass-1" }
            assertEquals("sounds/bass-1.wav", bassZone.sound)

            // AudioEngine menerima pemanggilan load
            assertEquals(listOf(0), fakeEngine.loadedSlots)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testInstallNonWavIsRejectedAndKitJsonRemainsUntouched() {
        val tempDir = File.createTempFile("dev-kit-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val initialJson = loadNewMetroJson()
            val kitJsonFile = File(tempDir, "kit.json").apply {
                writeText(initialJson)
            }

            val fakeEngine = FakeAudioEngine()
            val installer = DevSoundInstaller()
            val invalidBytes = "INI_BUKAN_FILE_WAV_YANG_VALID_SEPERTI_RIFF".toByteArray()

            val result = installer.installWav(
                zoneId = "bass-1",
                inputStream = ByteArrayInputStream(invalidBytes),
                devKitDir = tempDir,
                slotIndex = 0,
                engine = fakeEngine
            )

            assertTrue(result is DevSoundInstaller.Result.Failure)
            assertTrue(result.message.contains("WAV PCM 16-bit mono") || result.message.contains("RIFF"))

            // File audio tidak tersimpan
            val soundFile = File(tempDir, "sounds/bass-1.wav")
            assertFalse(soundFile.exists(), "File WAV tidak boleh tersimpan jika format salah")

            // kit.json di disk tetap utuh (sound tetap null)
            val diskKit = KitJsonParser.parse(kitJsonFile.readText())
            val bassZone = diskKit.drums.first { it.id == "bass" }.zones.first { it.id == "bass-1" }
            assertNull(bassZone.sound, "Sound harus tetap null jika file ditolak")

            // AudioEngine tidak dipanggil
            assertTrue(fakeEngine.loadedSlots.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testInstallOversizedFileIsRejected() {
        val tempDir = File.createTempFile("dev-kit-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val initialJson = loadNewMetroJson()
            val kitJsonFile = File(tempDir, "kit.json").apply {
                writeText(initialJson)
            }

            val installer = DevSoundInstaller()
            // 5 MB + 1 byte
            val oversizedBytes = ByteArray(5 * 1024 * 1024 + 1)

            val result = installer.installWav(
                zoneId = "bass-1",
                inputStream = ByteArrayInputStream(oversizedBytes),
                devKitDir = tempDir,
                slotIndex = 0
            )

            assertTrue(result is DevSoundInstaller.Result.Failure)
            assertTrue(result.message.contains("5 MB"))

            val diskKit = KitJsonParser.parse(kitJsonFile.readText())
            val bassZone = diskKit.drums.first { it.id == "bass" }.zones.first { it.id == "bass-1" }
            assertNull(bassZone.sound)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testClearSoundSetsSoundToNullInKitJson() {
        val tempDir = File.createTempFile("dev-kit-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val installer = DevSoundInstaller()
            val kitJsonFile = File(tempDir, "kit.json").apply {
                writeText(loadNewMetroJson())
            }

            // Pasang WAV dulu
            val validWav = buildValidWav()
            installer.installWav("bass-1", ByteArrayInputStream(validWav), tempDir, 0, FakeAudioEngine())

            // Kosongkan suara
            val clearResult = installer.clearSound("bass-1", tempDir)
            assertTrue(clearResult is DevSoundInstaller.Result.Success)

            val diskKit = KitJsonParser.parse(kitJsonFile.readText())
            val bassZone = diskKit.drums.first { it.id == "bass" }.zones.first { it.id == "bass-1" }
            assertNull(bassZone.sound, "Sound harus kembali null setelah dikosongkan")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
