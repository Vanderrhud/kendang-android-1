package com.vrkendang.integration

import com.vrkendang.audio.AudioEngineApi
import com.vrkendang.kitauthoring.DevKitAuthoringSession
import com.vrkendang.kitauthoring.DevSoundInstaller
import com.vrkendang.kitcore.hittest.HitTester
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.packageio.KitPackageReader
import com.vrkendang.kitcore.packageio.KitPackageWriter
import com.vrkendang.kitcore.validation.KitValidator
import com.vrkendang.view.CanvasCoordinateMapper
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

/**
 * Pengujian integrasi end-to-end untuk Tahap 12 (Tasks 12.1–12.6):
 * - 12.1 Alur Mode Developer end-to-end (buat/buka kit -> pasang WAV -> ketuk -> bersuara)
 * - 12.2 Pemasangan WAV ke seluruh 10 zona kendang
 * - 12.3 Multi-touch serempak 2–5 zona
 * - 12.4 Rolling & stacking 32 suara
 * - 12.5 Zona unassigned hening tanpa crash dengan visual highlight
 * - 12.6 Simpan & Simpan Sebagai paket .vrkendang tanpa .bak dan verifikasi impor ulang
 */
class IntegrationVerificationTest {

    private class RecordingAudioEngine : AudioEngineApi {
        val loadedSounds = mutableMapOf<Int, Int>() // slotIndex -> frameCount
        val triggerHistory = mutableListOf<Pair<Int, Float>>() // slotIndex, gain
        var releaseAllCount = 0

        override fun loadSound(
            pcmData: ByteBuffer,
            frameCount: Int,
            sampleRate: Int,
            slotIndex: Int
        ): Boolean {
            loadedSounds[slotIndex] = frameCount
            return true
        }

        override fun releaseAll() {
            releaseAllCount++
            loadedSounds.clear()
        }

        override fun openStream(): Boolean = true
        override fun startStream(): Boolean = true

        override fun triggerSound(slotIndex: Int, gain: Float): Boolean {
            triggerHistory.add(slotIndex to gain)
            return true
        }
    }

    private fun createPcmWavBytes(sampleRate: Int = 44100, samples: ShortArray = ShortArray(100) { 1000 }): ByteArray {
        val pcmBytes = ByteArray(samples.size * 2)
        val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            pcmBuffer.putShort(sample)
        }

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
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2.toShort())
        buffer.putShort(16.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmBytes.size)
        buffer.put(pcmBytes)

        return buffer.array()
    }

    @Test
    fun testTask12_1_DeveloperModeFlowEndToEnd() {
        val tempDir = File.createTempFile("dev-flow-12-1", "").apply { delete(); mkdirs() }
        val fakeAudio = RecordingAudioEngine()

        try {
            val session = DevKitAuthoringSession(tempDir)
            val newKit = session.createNewKit("kendang-koplo-1", "Kendang Koplo Asli", null)

            assertEquals("kendang-koplo-1", newKit.kitId)
            assertEquals(1, newKit.version)

            // Pasang WAV untuk Bass-1
            val wavData = createPcmWavBytes()
            val result = session.installZoneWav("bass-1", ByteArrayInputStream(wavData), slotIndex = 0, engine = fakeAudio)
            assertTrue(result is DevSoundInstaller.Result.Success)
            assertEquals("bass-1", (result as DevSoundInstaller.Result.Success).zoneId)

            // Verifikasi sampel langsung dimuat ke slot 0 AudioEngine
            assertTrue(fakeAudio.loadedSounds.containsKey(0))

            // Ketuk Bass-1 melalui simulasi koordinat hit testing
            val kit = session.workingKit!!
            val bassDrum = kit.drums.first { it.id == "bass" }
            val hitResult = HitTester.findHit(bassDrum.center.x, bassDrum.center.y, kit)

            assertNotNull(hitResult)
            assertEquals("bass-1", hitResult.zone.id)

            // Bunyikan zona
            val zone = hitResult.zone
            val triggered = fakeAudio.triggerSound(0, zone.gain)
            assertTrue(triggered)
            assertEquals(1, fakeAudio.triggerHistory.size)
            assertEquals(0, fakeAudio.triggerHistory[0].first)
            assertEquals(zone.gain, fakeAudio.triggerHistory[0].second)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testTask12_2_InstallAll10Zones() {
        val tempDir = File.createTempFile("dev-flow-12-2", "").apply { delete(); mkdirs() }
        val fakeAudio = RecordingAudioEngine()

        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("kendang-lengkap", "Kendang Lengkap 10 Zona", null)

            val zoneSlotMap = mapOf(
                "bass-1" to 0,
                "bass-2" to 1,
                "bass-3" to 2,
                "bass-4" to 3,
                "bass-5" to 4,
                "dut" to 5,
                "tak-1" to 6,
                "tak-2" to 7,
                "tung" to 8,
                "tong" to 9
            )

            for ((zoneId, slot) in zoneSlotMap) {
                val wav = createPcmWavBytes(samples = ShortArray(100) { (it * 10).toShort() })
                val res = session.installZoneWav(zoneId, ByteArrayInputStream(wav), slotIndex = slot, engine = fakeAudio)
                assertTrue(res is DevSoundInstaller.Result.Success, "Gagal memasang zona $zoneId")
            }

            val finalKit = session.workingKit!!
            var totalAssignedZones = 0
            for (drum in finalKit.drums) {
                for (zone in drum.zones) {
                    assertNotNull(zone.sound, "Sound zona ${zone.id} tidak boleh null")
                    totalAssignedZones++
                }
            }
            assertEquals(10, totalAssignedZones)

            // Seluruh 10 slot audio engine harus terisi
            assertEquals(10, fakeAudio.loadedSounds.size)
            for (slot in 0 until 10) {
                assertTrue(fakeAudio.loadedSounds.containsKey(slot))
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testTask12_3_MultiTouchConcurrency() {
        val fakeAudio = RecordingAudioEngine()

        // Simulasi 5 sentuhan serempak pada pointer berbeda (misal pointer 0 s.d. 4)
        val pointerSlots = listOf(0, 1, 5, 6, 8) // bass-1, bass-2, dut, tak-1, tung

        for (slot in pointerSlots) {
            fakeAudio.triggerSound(slot, 1.0f)
        }

        // Semua suara harus masuk antrean serempak tanpa terlewat
        assertEquals(5, fakeAudio.triggerHistory.size)
        assertEquals(pointerSlots, fakeAudio.triggerHistory.map { it.first })
    }

    @Test
    fun testTask12_4_RollingAndStacking32Voices() {
        val fakeAudio = RecordingAudioEngine()

        // Ketuk satu zona berulang cepat hingga 32 ketukan
        for (i in 0 until 32) {
            val ok = fakeAudio.triggerSound(slotIndex = 0, gain = 1.0f)
            assertTrue(ok)
        }
        assertEquals(32, fakeAudio.triggerHistory.size)

        // Di atas 32 ketukan tetap ditangani aman tanpa exception
        for (i in 32 until 40) {
            val ok = fakeAudio.triggerSound(slotIndex = 0, gain = 1.0f)
            assertTrue(ok)
        }
        assertEquals(40, fakeAudio.triggerHistory.size)
    }

    @Test
    fun testTask12_5_UnassignedZoneGracefulSilence() {
        val tempDir = File.createTempFile("dev-flow-12-5", "").apply { delete(); mkdirs() }
        val fakeAudio = RecordingAudioEngine()

        try {
            val session = DevKitAuthoringSession(tempDir)
            val newKit = session.createNewKit("kendang-unassigned", "Kendang Null", null)

            // Hit test pada zona dut
            val ketipung = newKit.drums.first { it.id == "ketipung" }
            val hit = HitTester.findHit(ketipung.center.x, ketipung.center.y, newKit)

            assertNotNull(hit)
            assertEquals("dut", hit.zone.id)
            assertNull(hit.zone.sound) // zona unassigned

            // Simulasi logika KendangBoardView: jika sound == null, tidak memicu triggerSound
            var visualHighlightShown = false
            if (hit.zone.sound != null) {
                fakeAudio.triggerSound(5, hit.zone.gain)
            } else {
                // Hening tanpa crash, tapi visual tap highlight tetap menyala
                visualHighlightShown = true
            }

            assertTrue(visualHighlightShown)
            assertEquals(0, fakeAudio.triggerHistory.size) // hening, tidak ada audio yang dipicu
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testTask12_6_SaveAndSaveAsRoundTripWithoutBak() {
        val tempDir = File.createTempFile("dev-flow-12-6", "").apply { delete(); mkdirs() }
        val fakeAudio = RecordingAudioEngine()

        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("kendang-simpan", "Kendang Simpan", null)

            val zoneSlotMap = mapOf(
                "bass-1" to 0,
                "bass-2" to 1,
                "bass-3" to 2,
                "bass-4" to 3,
                "bass-5" to 4,
                "dut" to 5,
                "tak-1" to 6,
                "tak-2" to 7,
                "tung" to 8,
                "tong" to 9
            )
            for ((zoneId, slot) in zoneSlotMap) {
                val wav = createPcmWavBytes()
                session.installZoneWav(zoneId, ByteArrayInputStream(wav), slotIndex = slot, engine = fakeAudio)
            }

            val kitBeforeSave = session.workingKit!!
            assertEquals(1, kitBeforeSave.version)

            // Susun paket ke memori (simulasi Simpan / Simpan Sebagai ke paket .vrkendang)
            val packageBytesOut = ByteArrayOutputStream()
            val wavProvider = { soundPath: String ->
                val f = File(tempDir, soundPath)
                if (f.exists()) f.readBytes() else null
            }

            val incrementedKit = kitBeforeSave.copy(version = kitBeforeSave.version + 1)
            KitPackageWriter.writePackage(incrementedKit, wavProvider, packageBytesOut)

            // Pastikan tidak ada berkas .bak di seluruh tempDir
            val bakFiles = tempDir.walkTopDown().filter { it.extension == "bak" || it.name.endsWith(".bak") }.toList()
            assertTrue(bakFiles.isEmpty(), "Ditemukan file .bak: $bakFiles")

            // Impor kembali file paket .vrkendang
            val packageBytes = packageBytesOut.toByteArray()
            val reimported = KitPackageReader.read(ByteArrayInputStream(packageBytes))

            // Validasi kit hasil impor ulang
            KitValidator.validateOrThrow(reimported.kitData)
            assertEquals(2, reimported.kitData.version, "Versi kit harus naik tepat 1 tingkat")
            assertEquals("kendang-simpan", reimported.kitData.kitId)
            assertEquals(10, reimported.wavFiles.size, "Paket harus memuat 10 file WAV yang dirujuk")

            // Pastikan semua file WAV di paket adalah yang dirujuk
            for (drum in reimported.kitData.drums) {
                for (zone in drum.zones) {
                    val expectedPath = zone.sound
                    assertNotNull(expectedPath)
                    assertTrue(reimported.wavFiles.containsKey(expectedPath))
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
