package com.vrkendang.audio

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
import kotlin.test.assertTrue

/**
 * Unit test untuk [KitAudioLoader].
 *
 * Tidak menyentuh JNI: engine palsu merekam pemanggilan load/release/stream,
 * sedangkan [KitSoundSource] palsu menyajikan WAV dari memori.
 */
class KitAudioLoaderTest {

    // -------------------------------------------------------------------------
    // Fake audio engine
    // -------------------------------------------------------------------------

    private class FakeAudioEngine(
        private val loadResult: Boolean = true,
        var openResult: Boolean = true,
        var startResult: Boolean = true
    ) : AudioEngineApi {
        val loadedSlots = mutableListOf<Int>()
        var releaseAllCount = 0
        var openStreamCount = 0
        var startStreamCount = 0

        override fun loadSound(
            pcmData: ByteBuffer,
            frameCount: Int,
            sampleRate: Int,
            slotIndex: Int
        ): Boolean {
            loadedSlots.add(slotIndex)
            return loadResult
        }

        override fun releaseAll() {
            releaseAllCount++
        }

        override fun openStream(): Boolean {
            openStreamCount++
            return openResult
        }

        override fun startStream(): Boolean {
            startStreamCount++
            return startResult
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * Membaca kit New Metro asli dari aset aplikasi agar test memakai data MVP
     * yang sebenarnya (4 drum, 10 zona, seluruh sound null).
     */
    private fun loadNewMetroKit(): KitData {
        val candidates = listOf(
            File("src/main/assets/kits/new-metro/kit.json"),
            File("app/src/main/assets/kits/new-metro/kit.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("kit.json New Metro tidak ditemukan dari direktori kerja test")
        return KitJsonParser.parse(file.readText())
    }

    /** Membangun WAV PCM 16-bit mono minimal yang valid untuk [WavParser]. */
    private fun buildWav(sampleRate: Int = 44100, samples: ShortArray = shortArrayOf(100, -100)): ByteArray {
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
        buffer.putShort(1) // PCM
        buffer.putShort(1) // mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // byte rate
        buffer.putShort(2) // block align
        buffer.putShort(16) // bits per sample
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmBytes.size)
        buffer.put(pcmBytes)

        return buffer.array()
    }

    // -------------------------------------------------------------------------
    // Test: kit tanpa WAV tetap aktif normal
    // -------------------------------------------------------------------------

    @Test
    fun `kit New Metro tanpa WAV aktif normal, stream dibuka, dan log mencatat nol WAV`() {
        val engine = FakeAudioEngine()
        val logs = mutableListOf<String>()
        val loader = KitAudioLoader(logSink = { message -> logs.add(message) })
        val kit = loadNewMetroKit()

        val activation = loader.activate(
            kit = kit,
            soundSource = KitSoundSource { path -> error("Tidak boleh membuka WAV: $path") },
            engine = engine
        )

        assertEquals(0, activation.loadedSoundCount)
        assertEquals(0, activation.failedSoundCount)
        assertTrue(activation.streamOpened, "Stream harus tetap dibuka untuk kit hening")
        assertTrue(activation.streamStarted, "Stream harus dimulai untuk kit hening")
        assertEquals(1, engine.releaseAllCount, "Sample kit lama harus dibebaskan")
        assertEquals(1, engine.openStreamCount)
        assertEquals(1, engine.startStreamCount)
        assertTrue(engine.loadedSlots.isEmpty(), "Tidak boleh ada load ke slot audio")
        assertTrue(
            logs.any { it.contains("tidak ada WAV yang dimuat") },
            "Log harus mencatat tidak ada WAV yang dimuat, actual: $logs"
        )
    }

    @Test
    fun `pemetaan slot mengikuti urutan drum lalu zona`() {
        val loader = KitAudioLoader(logSink = {})
        val activation = loader.activate(
            kit = loadNewMetroKit(),
            soundSource = KitSoundSource { error("tidak ada WAV") },
            engine = FakeAudioEngine()
        )

        assertEquals(10, activation.slotByZoneId.size)
        assertEquals(0, activation.slotByZoneId["bass-1"])
        assertEquals(4, activation.slotByZoneId["bass-5"])
        assertEquals(5, activation.slotByZoneId["kecil1-1"])
        assertEquals(6, activation.slotByZoneId["sedang-1"])
        assertEquals(9, activation.slotByZoneId["kecil2-2"])
    }

    // -------------------------------------------------------------------------
    // Test: WAV yang ada dimuat ke RAM sebelum stream dibuka
    // -------------------------------------------------------------------------

    @Test
    fun `semua WAV kit dimuat ke slot yang benar sebelum stream dibuka`() {
        val wav = buildWav()
        val kitWithSounds = kitWithSoundAssignments(
            mapOf(
                "bass-1" to "sounds/bass-1.wav",
                "kecil1-1" to "sounds/kecil1-1.wav"
            )
        )
        val engine = FakeAudioEngine()
        val loader = KitAudioLoader(logSink = {})

        val activation = loader.activate(
            kit = kitWithSounds,
            soundSource = arraySoundSource(mapOf(
                "sounds/bass-1.wav" to wav,
                "sounds/kecil1-1.wav" to wav
            )),
            engine = engine
        )

        assertEquals(2, activation.loadedSoundCount)
        assertEquals(0, activation.failedSoundCount)
        assertEquals(listOf(0, 5), engine.loadedSlots)
        assertEquals(1, engine.releaseAllCount, "releaseAll harus dipanggil sebelum load")
        assertTrue(activation.streamOpened)
        assertTrue(activation.streamStarted)
    }

    @Test
    fun `WAV yang hilang hanya membuat zona itu unassigned tanpa crash`() {
        val kitWithMissingSound = kitWithSoundAssignments(
            mapOf("bass-1" to "sounds/hilang.wav")
        )
        val engine = FakeAudioEngine()
        val logs = mutableListOf<String>()
        val loader = KitAudioLoader(logSink = { message -> logs.add(message) })

        val activation = loader.activate(
            kit = kitWithMissingSound,
            soundSource = arraySoundSource(emptyMap()),
            engine = engine
        )

        assertEquals(0, activation.loadedSoundCount)
        assertEquals(1, activation.failedSoundCount)
        assertTrue(engine.loadedSlots.isEmpty())
        assertTrue(activation.streamOpened, "Kit tetap aktif meski satu WAV gagal")
        assertTrue(logs.any { it.contains("bass-1") && it.contains("hilang.wav") })
    }

    @Test
    fun `WAV rusak ditolak per zona dan kit tetap aktif`() {
        val kitWithSound = kitWithSoundAssignments(mapOf("bass-1" to "sounds/bass-1.wav"))
        val engine = FakeAudioEngine()
        val loader = KitAudioLoader(logSink = {})

        val activation = loader.activate(
            kit = kitWithSound,
            soundSource = arraySoundSource(mapOf("sounds/bass-1.wav" to ByteArray(64) { 1 })),
            engine = engine
        )

        assertEquals(0, activation.loadedSoundCount)
        assertEquals(1, activation.failedSoundCount)
        assertTrue(activation.streamOpened)
    }

    // -------------------------------------------------------------------------
    // Test: kegagalan stream tidak membuat aktivasi crash
    // -------------------------------------------------------------------------

    @Test
    fun `kegagalan membuka stream dilaporkan tanpa crash`() {
        val engine = FakeAudioEngine(openResult = false)
        val logs = mutableListOf<String>()
        val loader = KitAudioLoader(logSink = { message -> logs.add(message) })

        val activation = loader.activate(
            kit = loadNewMetroKit(),
            soundSource = KitSoundSource { error("tidak ada WAV") },
            engine = engine
        )

        assertFalse(activation.streamOpened)
        assertFalse(activation.streamStarted)
        assertEquals(0, engine.startStreamCount)
        assertTrue(logs.any { it.contains("gagal membuka stream") })
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun kitWithSoundAssignments(assignments: Map<String, String>): KitData {
        val base = loadNewMetroKit()
        return base.copy(
            drums = base.drums.map { drum ->
                drum.copy(
                    zones = drum.zones.map { zone ->
                        val sound = assignments[zone.id]
                        if (sound != null) zone.copy(sound = sound) else zone
                    }
                )
            }
        )
    }

    private fun arraySoundSource(files: Map<String, ByteArray>): KitSoundSource =
        KitSoundSource { path ->
            val bytes = files[path] ?: throw IllegalArgumentException("file tidak ditemukan: $path")
            ByteArrayInputStream(bytes)
        }
}
