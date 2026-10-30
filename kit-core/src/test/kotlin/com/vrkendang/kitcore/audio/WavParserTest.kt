package com.vrkendang.kitcore.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class WavParserTest {

    private fun createWavBytes(
        format: Short = 1,
        channels: Short = 1,
        sampleRate: Int = 44100,
        bitsPerSample: Short = 16,
        pcmSamples: ShortArray = shortArrayOf(0, 1000, -1000, 2000),
        extraChunks: List<Pair<String, ByteArray>> = emptyList(),
        truncateBytes: Int = 0
    ): ByteArray {
        val pcmByteStream = ByteArrayOutputStream()
        for (sample in pcmSamples) {
            val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(sample)
            pcmByteStream.write(bb.array())
        }
        val pcmData = pcmByteStream.toByteArray()

        val fmtSize = 16
        val dataChunkSize = pcmData.size

        var totalChunksSize = (8 + fmtSize) + (8 + dataChunkSize)
        for ((id, data) in extraChunks) {
            val pad = if (data.size % 2 != 0) 1 else 0
            totalChunksSize += 8 + data.size + pad
        }

        val riffSize = 4 + totalChunksSize
        val out = ByteBuffer.allocate(riffSize + 8).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(riffSize)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))

        // Extra chunks before fmt
        for ((id, data) in extraChunks) {
            out.put(id.toByteArray(Charsets.US_ASCII))
            out.putInt(data.size)
            out.put(data)
            if (data.size % 2 != 0) out.put(0)
        }

        // fmt chunk
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(fmtSize)
        out.putShort(format)
        out.putShort(channels)
        out.putInt(sampleRate)
        out.putInt(byteRate)
        out.putShort(blockAlign)
        out.putShort(bitsPerSample)

        // data chunk
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(dataChunkSize)
        out.put(pcmData)

        val fullBytes = out.array()
        return if (truncateBytes > 0) {
            fullBytes.copyOf(fullBytes.size - truncateBytes)
        } else {
            fullBytes
        }
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
        // Potong 10 byte di akhir file sehingga data chunk tidak utuh
        val truncatedWav = createWavBytes(truncateBytes = 6)
        assertFailsWith<IllegalArgumentException> {
            WavParser.parse(truncatedWav)
        }

        // Terlalu pendek sama sekali
        assertFailsWith<IllegalArgumentException> {
            WavParser.parse(ByteArray(20))
        }
    }

    @Test
    fun testNonPcmFormatRejected() {
        // format 3 = IEEE float
        val floatWav = createWavBytes(format = 3)
        val exFloat = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(floatWav)
        }
        assertTrue(exFloat.message!!.contains("format code 0x3"))

        // format 0xFFFE = WAVE_FORMAT_EXTENSIBLE
        val extWav = createWavBytes(format = 0xFFFE.toShort())
        val exExt = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(extWav)
        }
        assertTrue(exExt.message!!.contains("format code"))
    }

    @Test
    fun testStereoRejected() {
        // channels = 2
        val stereoWav = createWavBytes(channels = 2)
        val ex = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(stereoWav)
        }
        assertTrue(ex.message!!.contains("stereo ditolak"))
    }

    @Test
    fun testNon16BitRejected() {
        // 24-bit
        val wav24 = createWavBytes(bitsPerSample = 24)
        val ex24 = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(wav24)
        }
        assertTrue(ex24.message!!.contains("24 bit"))

        // 8-bit
        val wav8 = createWavBytes(bitsPerSample = 8)
        val ex8 = assertFailsWith<IllegalArgumentException> {
            WavParser.parse(wav8)
        }
        assertTrue(ex8.message!!.contains("8 bit"))
    }

    @Test
    fun testInvalidRiffHeaderRejected() {
        val garbage = "NOT_A_RIFF_HEADER_AT_ALL_FOR_AUDIO_TESTING_1234567890".toByteArray()
        assertFailsWith<IllegalArgumentException> {
            WavParser.parse(garbage)
        }
    }
}
