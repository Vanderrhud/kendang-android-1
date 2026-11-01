package com.vrkendang.kitcore.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class WavParserTest {

    private val ksSubtypePcmGuid = byteArrayOf(
        0x01, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x10, 0x00,
        0x80.toByte(), 0x00, 0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71
    )

    private val ksSubtypeFloatGuid = byteArrayOf(
        0x03, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x10, 0x00,
        0x80.toByte(), 0x00, 0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71
    )

    /**
     * Membangun byte array WAV sintetis dengan konfigurasi fleksibel untuk pengujian bit-level.
     */
    private fun buildWavBytes(
        format: Int = 1,
        channels: Int = 1,
        sampleRate: Int = 44100,
        bitsPerSample: Int = 16,
        rawPcmBytes: ByteArray,
        isExtensible: Boolean = false,
        extensibleSubFormatGuid: ByteArray? = null,
        chunksBeforeFmt: List<Pair<String, ByteArray>> = emptyList(),
        chunksBetweenFmtAndData: List<Pair<String, ByteArray>> = emptyList(),
        chunksAfterData: List<Pair<String, ByteArray>> = emptyList(),
        truncateBytes: Int = 0
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val fmtBuf = ByteBuffer.allocate(if (isExtensible) 40 else 16).order(ByteOrder.LITTLE_ENDIAN)

        if (isExtensible) {
            fmtBuf.putShort(0xFFFE.toShort())
            fmtBuf.putShort(channels.toShort())
            fmtBuf.putInt(sampleRate)
            fmtBuf.putInt(byteRate)
            fmtBuf.putShort(blockAlign)
            fmtBuf.putShort(bitsPerSample.toShort())
            fmtBuf.putShort(22.toShort()) // cbSize
            fmtBuf.putShort(bitsPerSample.toShort()) // wValidBitsPerSample
            fmtBuf.putInt(if (channels == 1) 4 else 3) // dwChannelMask
            fmtBuf.put(extensibleSubFormatGuid ?: ksSubtypePcmGuid)
        } else {
            fmtBuf.putShort(format.toShort())
            fmtBuf.putShort(channels.toShort())
            fmtBuf.putInt(sampleRate)
            fmtBuf.putInt(byteRate)
            fmtBuf.putShort(blockAlign)
            fmtBuf.putShort(bitsPerSample.toShort())
        }
        val fmtBytes = fmtBuf.array()

        fun writeChunk(stream: ByteArrayOutputStream, chunkId: String, data: ByteArray) {
            stream.write(chunkId.toByteArray(Charsets.US_ASCII))
            val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size)
            stream.write(lenBuf.array())
            stream.write(data)
            if (data.size % 2 != 0) {
                stream.write(0) // RIFF word alignment pad
            }
        }

        val chunksStream = ByteArrayOutputStream()
        for ((id, data) in chunksBeforeFmt) {
            writeChunk(chunksStream, id, data)
        }
        writeChunk(chunksStream, "fmt ", fmtBytes)
        for ((id, data) in chunksBetweenFmtAndData) {
            writeChunk(chunksStream, id, data)
        }
        writeChunk(chunksStream, "data", rawPcmBytes)
        for ((id, data) in chunksAfterData) {
            writeChunk(chunksStream, id, data)
        }

        val chunksBytes = chunksStream.toByteArray()
        val riffSize = 4 + chunksBytes.size
        val out = ByteBuffer.allocate(riffSize + 8).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(riffSize)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put(chunksBytes)

        val fullBytes = out.array()
        return if (truncateBytes > 0) {
            fullBytes.copyOf(fullBytes.size - truncateBytes)
        } else {
            fullBytes
        }
    }

    private fun pcm16Bytes(samples: ShortArray): ByteArray {
        val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) buf.putShort(s)
        return buf.array()
    }

    private fun pcm24Bytes(samples: IntArray): ByteArray {
        val out = ByteArray(samples.size * 3)
        for (i in samples.indices) {
            val s = samples[i]
            val offset = i * 3
            out[offset] = (s and 0xFF).toByte()
            out[offset + 1] = ((s shr 8) and 0xFF).toByte()
            out[offset + 2] = ((s shr 16) and 0xFF).toByte()
        }
        return out
    }

    private fun float32Bytes(samples: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in samples) buf.putFloat(f)
        return buf.array()
    }

    private fun extractShorts(pcmData: ByteArray): ShortArray {
        val buf = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        val result = ShortArray(pcmData.size / 2)
        for (i in result.indices) {
            result[i] = buf.short
        }
        return result
    }

    private fun createWavBytes(
        format: Short = 1,
        channels: Short = 1,
        sampleRate: Int = 44100,
        bitsPerSample: Short = 16,
        pcmSamples: ShortArray = shortArrayOf(0, 1000, -1000, 2000),
        extraChunks: List<Pair<String, ByteArray>> = emptyList(),
        truncateBytes: Int = 0
    ): ByteArray {
        return buildWavBytes(
            format = format.toInt(),
            channels = channels.toInt(),
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample.toInt(),
            rawPcmBytes = pcm16Bytes(pcmSamples),
            chunksBeforeFmt = extraChunks,
            truncateBytes = truncateBytes
        )
    }

    @Test
    fun testValidPcm16BitMono() {
        val samples = shortArrayOf(10, 20, -30, 40)
        val wavBytes = createWavBytes(sampleRate = 48000, pcmSamples = samples)

        val result = WavParser.parse(wavBytes)
        assertEquals(48000, result.sampleRate)
        assertEquals(1, result.channels)
        assertEquals(16, result.bitsPerSample)
        assertEquals(4, result.frameCount)
        assertEquals(8, result.pcmData.size)
        assertContentEquals(samples, extractShorts(result.pcmData))
    }

    @Test
    fun testPcm16BitStereoDownmix() {
        // 4 stereo frames: (Left, Right)
        // Frame 0: (1000, 3000) -> 2000
        // Frame 1: (-2000, 4000) -> 1000
        // Frame 2: (30000, -10000) -> 10000
        // Frame 3: (0, 500) -> 250
        val stereoSamples = shortArrayOf(
            1000, 3000,
            -2000, 4000,
            30000, -10000,
            0, 500
        )
        val wav = buildWavBytes(
            channels = 2,
            bitsPerSample = 16,
            rawPcmBytes = pcm16Bytes(stereoSamples)
        )

        val result = WavParser.parse(wav)
        assertEquals(1, result.channels, "Output harus selalu mono (channels = 1)")
        assertEquals(16, result.bitsPerSample, "Output harus 16-bit PCM")
        assertEquals(4, result.frameCount, "Jumlah frame stereo 4 harus menjadi 4 frame mono")
        assertEquals(8, result.pcmData.size, "4 frame x 2 byte = 8 byte")

        val downmixed = extractShorts(result.pcmData)
        assertContentEquals(
            shortArrayOf(2000, 1000, 10000, 250),
            downmixed
        )
    }

    @Test
    fun testPcm24BitMonoConversion() {
        // Sampel 24-bit:
        // 0 -> 0
        // 1000 shl 8 -> 1000
        // -1000 shl 8 -> -1000
        // 8388607 (0x7FFFFF, max positif) -> 32767
        // -8388608 (0xFF800000, min negatif) -> -32768
        val samples24 = intArrayOf(
            0,
            1000 shl 8,
            -1000 shl 8,
            8388607,
            -8388608
        )
        val wav = buildWavBytes(
            channels = 1,
            bitsPerSample = 24,
            rawPcmBytes = pcm24Bytes(samples24)
        )

        val result = WavParser.parse(wav)
        assertEquals(1, result.channels)
        assertEquals(16, result.bitsPerSample)
        assertEquals(5, result.frameCount)
        assertEquals(10, result.pcmData.size)

        val converted = extractShorts(result.pcmData)
        assertContentEquals(
            shortArrayOf(0, 1000, -1000, 32767, -32768),
            converted
        )
    }

    @Test
    fun testPcm24BitStereoDownmixAndConversion() {
        // 4 stereo frames:
        // Frame 0: (2000 shl 8, 6000 shl 8) -> avg = 4000 shl 8 -> 4000
        // Frame 1: (-4000 shl 8, 2000 shl 8) -> avg = -1000 shl 8 -> -1000
        // Frame 2: (8388607, 8388607) -> avg = 8388607 -> 32767
        // Frame 3: (-8388608, -8388608) -> avg = -8388608 -> -32768
        val samples24 = intArrayOf(
            2000 shl 8, 6000 shl 8,
            -4000 shl 8, 2000 shl 8,
            8388607, 8388607,
            -8388608, -8388608
        )
        val wav = buildWavBytes(
            channels = 2,
            bitsPerSample = 24,
            rawPcmBytes = pcm24Bytes(samples24)
        )

        val result = WavParser.parse(wav)
        assertEquals(1, result.channels)
        assertEquals(16, result.bitsPerSample)
        assertEquals(4, result.frameCount)

        val converted = extractShorts(result.pcmData)
        assertContentEquals(
            shortArrayOf(4000, -1000, 32767, -32768),
            converted
        )
    }

    @Test
    fun testFloat32BitMonoConversion() {
        // Nilai floating point:
        // 0.0f -> 0
        // 1.0f -> 32767
        // -1.0f -> -32768
        // 0.5f -> 16384
        // -0.5f -> -16384
        // 1.5f (clamped ke 1.0) -> 32767
        // -2.0f (clamped ke -1.0) -> -32768
        // Float.NaN (dikonversi ke 0.0) -> 0
        val floats = floatArrayOf(0.0f, 1.0f, -1.0f, 0.5f, -0.5f, 1.5f, -2.0f, Float.NaN)
        val wav = buildWavBytes(
            format = 3,
            channels = 1,
            bitsPerSample = 32,
            rawPcmBytes = float32Bytes(floats)
        )

        val result = WavParser.parse(wav)
        assertEquals(1, result.channels)
        assertEquals(16, result.bitsPerSample)
        assertEquals(8, result.frameCount)

        val converted = extractShorts(result.pcmData)
        assertContentEquals(
            shortArrayOf(0, 32767, -32768, 16384, -16384, 32767, -32768, 0),
            converted
        )
    }

    @Test
    fun testFloat32BitStereoDownmixAndConversion() {
        // Frame 0: (0.2f, 0.6f) -> avg = 0.4f -> 13107
        // Frame 1: (-0.8f, 0.2f) -> avg = -0.3f -> -9830
        // Frame 2: (1.5f, 1.5f) -> avg = 1.5f (clamped 1.0f) -> 32767
        // Frame 3: (-2.0f, -2.0f) -> avg = -2.0f (clamped -1.0f) -> -32768
        val floats = floatArrayOf(
            0.2f, 0.6f,
            -0.8f, 0.2f,
            1.5f, 1.5f,
            -2.0f, -2.0f
        )
        val wav = buildWavBytes(
            format = 3,
            channels = 2,
            bitsPerSample = 32,
            rawPcmBytes = float32Bytes(floats)
        )

        val result = WavParser.parse(wav)
        assertEquals(1, result.channels)
        assertEquals(16, result.bitsPerSample)
        assertEquals(4, result.frameCount)

        val converted = extractShorts(result.pcmData)
        assertContentEquals(
            shortArrayOf(13107, -9830, 32767, -32768),
            converted
        )
    }

    @Test
    fun testExtensibleFormatPcmMonoAndStereo() {
        // 16-bit mono extensible
        val wavMono = buildWavBytes(
            channels = 1,
            bitsPerSample = 16,
            isExtensible = true,
            extensibleSubFormatGuid = ksSubtypePcmGuid,
            rawPcmBytes = pcm16Bytes(shortArrayOf(100, 200, -300))
        )
        val resMono = WavParser.parse(wavMono)
        assertEquals(1, resMono.channels)
        assertEquals(16, resMono.bitsPerSample)
        assertEquals(3, resMono.frameCount)
        assertContentEquals(shortArrayOf(100, 200, -300), extractShorts(resMono.pcmData))

        // 24-bit stereo extensible
        val samples24 = intArrayOf(1000 shl 8, 3000 shl 8) // L=1000, R=3000 -> avg=2000
        val wavStereo = buildWavBytes(
            channels = 2,
            bitsPerSample = 24,
            isExtensible = true,
            extensibleSubFormatGuid = ksSubtypePcmGuid,
            rawPcmBytes = pcm24Bytes(samples24)
        )
        val resStereo = WavParser.parse(wavStereo)
        assertEquals(1, resStereo.channels)
        assertEquals(16, resStereo.bitsPerSample)
        assertEquals(1, resStereo.frameCount)
        assertContentEquals(shortArrayOf(2000), extractShorts(resStereo.pcmData))
    }

    @Test
    fun testExtensibleFormatFloatMonoAndStereo() {
        // 32-bit float mono extensible
        val wavMono = buildWavBytes(
            channels = 1,
            bitsPerSample = 32,
            isExtensible = true,
            extensibleSubFormatGuid = ksSubtypeFloatGuid,
            rawPcmBytes = float32Bytes(floatArrayOf(0.5f, -0.5f))
        )
        val resMono = WavParser.parse(wavMono)
        assertEquals(1, resMono.channels)
        assertEquals(16, resMono.bitsPerSample)
        assertEquals(2, resMono.frameCount)
        assertContentEquals(shortArrayOf(16384, -16384), extractShorts(resMono.pcmData))

        // 32-bit float stereo extensible
        val wavStereo = buildWavBytes(
            channels = 2,
            bitsPerSample = 32,
            isExtensible = true,
            extensibleSubFormatGuid = ksSubtypeFloatGuid,
            rawPcmBytes = float32Bytes(floatArrayOf(0.4f, 0.6f)) // avg = 0.5f -> 16384
        )
        val resStereo = WavParser.parse(wavStereo)
        assertEquals(1, resStereo.channels)
        assertEquals(16, resStereo.bitsPerSample)
        assertEquals(1, resStereo.frameCount)
        assertContentEquals(shortArrayOf(16384), extractShorts(resStereo.pcmData))
    }

    @Test
    fun testArbitraryMetadataChunksSkipped() {
        val bextData = ByteArray(602) { 0x42.toByte() } // bext broadcast extension
        val junkData = "JunkOdd".toByteArray(Charsets.US_ASCII) // 7 bytes (odd-sized, menguji RIFF word-alignment pad)
        val factData = ByteArray(4) { 0 } // fact chunk (umum pada IEEE float)
        val acidData = ByteArray(24) { 1 } // acid loop chunk
        val listData = "INFOArtist=Kendang Dangdut".toByteArray(Charsets.US_ASCII) // LIST info chunk trailing

        val wav = buildWavBytes(
            channels = 1,
            bitsPerSample = 16,
            rawPcmBytes = pcm16Bytes(shortArrayOf(1234, -5678)),
            chunksBeforeFmt = listOf("bext" to bextData, "JUNK" to junkData),
            chunksBetweenFmtAndData = listOf("fact" to factData, "acid" to acidData),
            chunksAfterData = listOf("LIST" to listData)
        )

        val result = WavParser.parse(wav)
        assertEquals(44100, result.sampleRate)
        assertEquals(1, result.channels)
        assertEquals(16, result.bitsPerSample)
        assertEquals(2, result.frameCount)
        assertContentEquals(shortArrayOf(1234, -5678), extractShorts(result.pcmData))
    }

    @Test
    fun testUnknownChunksSkippedSafely() {
        val extraInfo = "INFOArtist=Kendang Dangdut".toByteArray(Charsets.US_ASCII)
        val wavBytes = createWavBytes(
            extraChunks = listOf("LIST" to extraInfo)
        )

        val result = WavParser.parse(wavBytes)
        assertEquals(44100, result.sampleRate)
        assertEquals(1, result.channels)
        assertNotNull(result.pcmData)
    }

    @Test
    fun testTruncatedFileThrowsException() {
        val truncatedWav = createWavBytes(truncateBytes = 6)
        assertFailsWith<IllegalArgumentException> {
            WavParser.parse(truncatedWav)
        }

        assertFailsWith<IllegalArgumentException> {
            WavParser.parse(ByteArray(20))
        }
    }

    @Test
    fun testUnsupportedCodecsRejected() {
        // Format 6: ITU G.711 A-law
        val alawWav = buildWavBytes(format = 6, rawPcmBytes = ByteArray(16))
        val exAlaw = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(alawWav)
        }
        assertTrue(exAlaw.message!!.contains("format code 0x6"))

        // Format 7: ITU G.711 mu-law
        val mulawWav = buildWavBytes(format = 7, rawPcmBytes = ByteArray(16))
        val exMulaw = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(mulawWav)
        }
        assertTrue(exMulaw.message!!.contains("format code 0x7"))

        // Extensible dengan GUID yang tidak didukung
        val invalidGuid = ByteArray(16) { 0xFF.toByte() }
        val invalidExtWav = buildWavBytes(
            isExtensible = true,
            extensibleSubFormatGuid = invalidGuid,
            rawPcmBytes = ByteArray(16)
        )
        val exExt = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(invalidExtWav)
        }
        assertTrue(exExt.message!!.contains("Sub-format GUID tidak didukung"))
    }

    @Test
    fun testUnsupportedChannelCountRejected() {
        // 3 channels (Surround 3.0)
        val surroundWav = buildWavBytes(channels = 3, rawPcmBytes = ByteArray(24))
        val ex3 = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(surroundWav)
        }
        assertTrue(ex3.message!!.contains("3 channel"))

        // 0 channels
        val zeroChanWav = buildWavBytes(channels = 0, rawPcmBytes = ByteArray(16))
        val ex0 = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(zeroChanWav)
        }
        assertTrue(ex0.message!!.contains("0 channel"))
    }

    @Test
    fun testUnsupportedBitDepthRejected() {
        // 8-bit PCM
        val wav8 = buildWavBytes(bitsPerSample = 8, rawPcmBytes = ByteArray(16))
        val ex8 = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(wav8)
        }
        assertTrue(ex8.message!!.contains("8 bit"))

        // 12-bit PCM
        val wav12 = buildWavBytes(bitsPerSample = 12, rawPcmBytes = ByteArray(16))
        val ex12 = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(wav12)
        }
        assertTrue(ex12.message!!.contains("12 bit"))

        // 16-bit Float (tidak valid, IEEE float harus 32-bit)
        val float16 = buildWavBytes(format = 3, bitsPerSample = 16, rawPcmBytes = ByteArray(16))
        val exFloat16 = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(float16)
        }
        assertTrue(exFloat16.message!!.contains("16 bit"))
    }

    @Test
    fun testInvalidRiffHeaderRejected() {
        val garbage = "NOT_A_RIFF_HEADER_AT_ALL_FOR_AUDIO_TESTING_1234567890".toByteArray()
        assertFailsWith<IllegalArgumentException> {
            WavParser.parse(garbage)
        }
    }
}
