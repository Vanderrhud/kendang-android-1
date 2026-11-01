package com.vrkendang.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit test untuk [SoundLoader].
 *
 * Strategi:
 * - Tidak bergantung pada JNI / Android runtime — menggunakan [FakeAudioEngine]
 *   yang merekam panggilan loadSound tanpa menyentuh native library.
 * - Helper [buildWav] membuat byte array WAV PCM 16-bit mono yang valid
 *   sesuai standar RIFF/WAVE agar WavParser dapat menguraikannya.
 * - Setiap kasus uji mencakup satu skenario penerimaan atau penolakan.
 */
class SoundLoaderTest {

    // -------------------------------------------------------------------------
    // Fake AudioEngine — merekam panggilan tanpa JNI
    // -------------------------------------------------------------------------

    /**
     * Implementasi palsu [AudioEngineApi] yang merekam argumen loadSound terakhir.
     * Dapat dikonfigurasi untuk mengembalikan true (berhasil) atau false (gagal).
     */
    private class FakeAudioEngine(private val returnValue: Boolean = true) : AudioEngineApi {
        var callCount = 0
        var lastFrameCount = 0
        var lastSampleRate = 0
        var lastSlotIndex = -1
        var lastBufferCapacity = 0

        override fun loadSound(
            pcmData: ByteBuffer,
            frameCount: Int,
            sampleRate: Int,
            slotIndex: Int
        ): Boolean {
            callCount++
            lastFrameCount    = frameCount
            lastSampleRate    = sampleRate
            lastSlotIndex     = slotIndex
            lastBufferCapacity = pcmData.capacity()
            return returnValue
        }

        override fun releaseAll() = Unit

        override fun openStream(): Boolean = true

        override fun startStream(): Boolean = true
    }

    // -------------------------------------------------------------------------
    // Helper: membuat byte array WAV PCM 16-bit mono yang valid
    // -------------------------------------------------------------------------

    /**
     * Membangun WAV mentah sesuai spesifikasi RIFF/WAVE.
     */
    private fun buildWavRaw(
        sampleRate: Int = 44100,
        rawBytes: ByteArray,
        audioFormat: Short = 1,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ): ByteArray {
        val fmtSize  = 16
        val dataSize = rawBytes.size
        val riffSize = 4 + (8 + fmtSize) + (8 + dataSize)

        val buf = ByteBuffer.allocate(8 + riffSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(riffSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        val byteRate   = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(fmtSize)
        buf.putShort(audioFormat)
        buf.putShort(channels)
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign)
        buf.putShort(bitsPerSample)

        // data chunk
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        buf.put(rawBytes)

        return buf.array()
    }

    /**
     * Membangun WAV PCM 16-bit mono sesuai spesifikasi RIFF/WAVE.
     *
     * @param sampleRate    Sample rate (Hz), default 44100.
     * @param samples       Data sampel 16-bit signed little-endian.
     * @param audioFormat   Format audio (1 = PCM, nilai lain = non-PCM untuk uji penolakan).
     * @param channels      Jumlah channel (1 = mono, nilai lain untuk uji penolakan).
     * @param bitsPerSample Bit depth (16 = valid, nilai lain untuk uji penolakan).
     */
    private fun buildWav(
        sampleRate: Int = 44100,
        samples: ShortArray = shortArrayOf(100, -100, 200, -200),
        audioFormat: Short = 1,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ): ByteArray {
        val pcmOut = ByteArrayOutputStream()
        for (s in samples) {
            val b = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(s)
            pcmOut.write(b.array())
        }
        return buildWavRaw(
            sampleRate = sampleRate,
            rawBytes = pcmOut.toByteArray(),
            audioFormat = audioFormat,
            channels = channels,
            bitsPerSample = bitsPerSample
        )
    }

    // -------------------------------------------------------------------------
    // Test: WAV valid PCM 16-bit mono → berhasil dimuat
    // -------------------------------------------------------------------------

    @Test
    fun `WAV PCM 16-bit mono valid berhasil dimuat ke slot yang benar`() {
        val fake   = FakeAudioEngine(returnValue = true)
        val loader = SoundLoader()

        val samples = shortArrayOf(1000, -1000, 2000, -2000)
        val wav     = buildWav(sampleRate = 44100, samples = samples)

        val result = loader.load(ByteArrayInputStream(wav), slotIndex = 3, engine = fake)

        assertIs<SoundLoader.Result.Success>(result)
        assertEquals(1, fake.callCount,       "loadSound harus dipanggil tepat sekali")
        assertEquals(3, fake.lastSlotIndex,   "slotIndex harus 3")
        assertEquals(44100, fake.lastSampleRate, "sampleRate harus 44100")
        // 4 sampel × 2 byte/sampel = 8 byte; frameCount = 4 (mono)
        assertEquals(4, fake.lastFrameCount,  "frameCount harus 4 untuk 4 sampel mono 16-bit")
        assertEquals(8, fake.lastBufferCapacity, "DirectByteBuffer capacity harus 8 byte")
    }

    @Test
    fun `WAV PCM 48000 Hz dimuat dengan sampleRate dan frameCount yang benar`() {
        val fake   = FakeAudioEngine(returnValue = true)
        val loader = SoundLoader()

        // 100 sampel mono 16-bit @ 48000 Hz
        val samples = ShortArray(100) { it.toShort() }
        val wav     = buildWav(sampleRate = 48000, samples = samples)

        loader.load(ByteArrayInputStream(wav), slotIndex = 0, engine = fake)

        assertEquals(48000, fake.lastSampleRate)
        assertEquals(100,   fake.lastFrameCount)
        assertEquals(200,   fake.lastBufferCapacity) // 100 × 2 byte
    }

    // -------------------------------------------------------------------------
    // Test: file bukan WAV → ditolak dengan pesan galat yang jelas
    // -------------------------------------------------------------------------

    @Test
    fun `file bukan WAV (byte acak) ditolak dengan pesan galat yang jelas`() {
        val fake   = FakeAudioEngine()
        val loader = SoundLoader()

        val garbage = ByteArray(64) { it.toByte() } // bukan RIFF
        val result  = loader.load(ByteArrayInputStream(garbage), slotIndex = 1, engine = fake)

        assertIs<SoundLoader.Result.Failure>(result)
        assertTrue(result.message.isNotBlank(), "Pesan galat harus ada")
        assertEquals(0, fake.callCount, "loadSound tidak boleh dipanggil untuk file non-WAV")
    }

    @Test
    fun `stream kosong ditolak dengan pesan galat`() {
        val fake   = FakeAudioEngine()
        val loader = SoundLoader()

        val result = loader.load(ByteArrayInputStream(ByteArray(0)), slotIndex = 2, engine = fake)

        assertIs<SoundLoader.Result.Failure>(result)
        assertEquals(0, fake.callCount)
    }

    // -------------------------------------------------------------------------
    // Test: format audio tidak didukung → ditolak
    // -------------------------------------------------------------------------

    @Test
    fun `WAV codec tidak didukung (misal A-law format 6) ditolak dengan pesan galat yang jelas`() {
        val fake   = FakeAudioEngine()
        val loader = SoundLoader()

        // audioFormat = 6 = A-law, bukan format PCM/Float yang didukung
        val wav    = buildWav(audioFormat = 6)
        val result = loader.load(ByteArrayInputStream(wav), slotIndex = 5, engine = fake)

        assertIs<SoundLoader.Result.Failure>(result)
        assertTrue(
            result.message.contains("5", ignoreCase = true) ||
            result.message.contains("WAV", ignoreCase = true),
            "Pesan galat harus menyebut slot atau konteks WAV"
        )
        assertEquals(0, fake.callCount, "loadSound tidak boleh dipanggil untuk format audio tidak didukung")
    }

    // -------------------------------------------------------------------------
    // Test: format channel tidak didukung (>2 channel) → ditolak
    // -------------------------------------------------------------------------

    @Test
    fun `WAV multi-channel (lebih dari 2 channel) ditolak dengan pesan galat yang jelas`() {
        val fake   = FakeAudioEngine()
        val loader = SoundLoader()

        val wav    = buildWav(channels = 3)
        val result = loader.load(ByteArrayInputStream(wav), slotIndex = 7, engine = fake)

        assertIs<SoundLoader.Result.Failure>(result)
        assertTrue(result.message.isNotBlank())
        assertEquals(0, fake.callCount, "loadSound tidak boleh dipanggil untuk WAV >2 channel")
    }

    // -------------------------------------------------------------------------
    // Test: bit depth tidak didukung → ditolak
    // -------------------------------------------------------------------------

    @Test
    fun `WAV 8-bit ditolak dengan pesan galat yang jelas`() {
        val fake   = FakeAudioEngine()
        val loader = SoundLoader()

        val wav    = buildWav(bitsPerSample = 8)
        val result = loader.load(ByteArrayInputStream(wav), slotIndex = 4, engine = fake)

        assertIs<SoundLoader.Result.Failure>(result)
        assertEquals(0, fake.callCount)
    }

    // -------------------------------------------------------------------------
    // Test: Format multi-format universal (Stereo, 24-bit, 32-bit Float) → berhasil dimuat
    // -------------------------------------------------------------------------

    @Test
    fun `WAV stereo (2 channel) berhasil dimuat dan di-downmix ke mono`() {
        val fake   = FakeAudioEngine(returnValue = true)
        val loader = SoundLoader()

        // 4 frame stereo (8 sampel short)
        val stereoSamples = shortArrayOf(100, 200, -300, -100, 500, 1500, -1000, 1000)
        val wav = buildWav(channels = 2, bitsPerSample = 16, samples = stereoSamples)
        val result = loader.load(ByteArrayInputStream(wav), slotIndex = 7, engine = fake)

        assertIs<SoundLoader.Result.Success>(result)
        assertEquals(1, fake.callCount, "loadSound harus dipanggil")
        assertEquals(7, fake.lastSlotIndex)
        assertEquals(4, fake.lastFrameCount, "4 frame stereo harus di-downmix menjadi 4 frame mono")
        assertEquals(8, fake.lastBufferCapacity, "4 frame x 2 byte = 8 byte DirectByteBuffer")
    }

    @Test
    fun `WAV 24-bit PCM berhasil dimuat dan dikonversi ke 16-bit mono`() {
        val fake   = FakeAudioEngine(returnValue = true)
        val loader = SoundLoader()

        // 4 sampel 24-bit (12 byte)
        val samples24 = intArrayOf(0, 1000 shl 8, -1000 shl 8, 8388607)
        val raw24 = ByteArray(12)
        for (i in samples24.indices) {
            val s = samples24[i]
            val offset = i * 3
            raw24[offset] = (s and 0xFF).toByte()
            raw24[offset + 1] = ((s shr 8) and 0xFF).toByte()
            raw24[offset + 2] = ((s shr 16) and 0xFF).toByte()
        }
        val wav = buildWavRaw(channels = 1, bitsPerSample = 24, rawBytes = raw24)
        val result = loader.load(ByteArrayInputStream(wav), slotIndex = 6, engine = fake)

        assertIs<SoundLoader.Result.Success>(result)
        assertEquals(1, fake.callCount)
        assertEquals(6, fake.lastSlotIndex)
        assertEquals(4, fake.lastFrameCount, "4 sampel 24-bit harus dikonversi menjadi 4 frame 16-bit mono")
        assertEquals(8, fake.lastBufferCapacity, "4 frame x 2 byte = 8 byte")
    }

    @Test
    fun `WAV 32-bit Float berhasil dimuat dan dikonversi ke 16-bit mono`() {
        val fake   = FakeAudioEngine(returnValue = true)
        val loader = SoundLoader()

        // 4 float sampel (16 byte)
        val floats = floatArrayOf(0.0f, 1.0f, -1.0f, 0.5f)
        val rawFloatBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) rawFloatBuf.putFloat(f)

        val wav = buildWavRaw(
            audioFormat = 3,
            channels = 1,
            bitsPerSample = 32,
            rawBytes = rawFloatBuf.array()
        )
        val result = loader.load(ByteArrayInputStream(wav), slotIndex = 5, engine = fake)

        assertIs<SoundLoader.Result.Success>(result)
        assertEquals(1, fake.callCount)
        assertEquals(5, fake.lastSlotIndex)
        assertEquals(4, fake.lastFrameCount, "4 sampel 32-bit float harus dikonversi menjadi 4 frame 16-bit mono")
        assertEquals(8, fake.lastBufferCapacity, "4 frame x 2 byte = 8 byte")
    }

    // -------------------------------------------------------------------------
    // Test: C++ loadSound mengembalikan false → Failure dilaporkan
    // -------------------------------------------------------------------------

    @Test
    fun `ketika C++ loadSound gagal (return false) hasil adalah Failure`() {
        // Simulasi engine yang menolak load (misal slot di luar batas atau engine null)
        val fake   = FakeAudioEngine(returnValue = false)
        val loader = SoundLoader()

        val wav    = buildWav()
        val result = loader.load(ByteArrayInputStream(wav), slotIndex = 0, engine = fake)

        assertIs<SoundLoader.Result.Failure>(result)
        assertEquals(1, fake.callCount, "loadSound tetap dipanggil sekali")
        assertTrue(result.message.isNotBlank())
    }

    // -------------------------------------------------------------------------
    // Test: pesan galat menyebut slotIndex yang benar
    // -------------------------------------------------------------------------

    @Test
    fun `pesan galat menyebut slotIndex yang gagal`() {
        val fake   = FakeAudioEngine()
        val loader = SoundLoader()

        val garbage = ByteArray(10) { 0 }
        val result  = loader.load(ByteArrayInputStream(garbage), slotIndex = 17, engine = fake)

        assertIs<SoundLoader.Result.Failure>(result)
        assertTrue(
            result.message.contains("17"),
            "Pesan galat harus menyebut slotIndex 17, actual: '${result.message}'"
        )
    }
}
