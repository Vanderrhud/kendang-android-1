package com.vrkendang.kitcore.audio

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser audio WAV untuk memvalidasi dan mengekstrak data mentah PCM 16-bit mono.
 */
object WavParser {

    const val WAVE_FORMAT_PCM = 1

    data class WavPcm(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val pcmData: ByteArray
    ) {
        val frameCount: Int get() = pcmData.size / (channels * (bitsPerSample / 8))

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WavPcm) return false
            if (sampleRate != other.sampleRate) return false
            if (channels != other.channels) return false
            if (bitsPerSample != other.bitsPerSample) return false
            if (!pcmData.contentEquals(other.pcmData)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = sampleRate
            result = 31 * result + channels
            result = 31 * result + bitsPerSample
            result = 31 * result + pcmData.contentHashCode()
            return result
        }
    }

    fun parse(bytes: ByteArray): WavPcm {
        return parse(ByteArrayInputStream(bytes))
    }

    fun parse(inputStream: InputStream): WavPcm {
        val allBytes = inputStream.readBytes()
        if (allBytes.size < 44) {
            throw IllegalArgumentException("File WAV terpotong atau terlalu pendek (ukuran: ${allBytes.size} byte)")
        }

        val buffer = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Periksa header RIFF
        val riff = ByteArray(4)
        buffer.get(riff)
        if (String(riff, Charsets.US_ASCII) != "RIFF") {
            throw IllegalArgumentException("Header bukan RIFF yang valid")
        }

        val riffLength = buffer.int
        if (allBytes.size < riffLength + 8) {
            throw IllegalArgumentException("Ukuran file WAV tidak sesuai dengan deklarasi header RIFF (file terpotong)")
        }

        val wave = ByteArray(4)
        buffer.get(wave)
        if (String(wave, Charsets.US_ASCII) != "WAVE") {
            throw IllegalArgumentException("Format RIFF bukan WAVE")
        }

        var formatParsed = false
        var audioFormat = 0
        var numChannels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var pcmData: ByteArray? = null

        // 2. Iterasi chunks dalam file WAV
        while (buffer.remaining() >= 8) {
            val chunkIdBytes = ByteArray(4)
            buffer.get(chunkIdBytes)
            val chunkId = String(chunkIdBytes, Charsets.US_ASCII)
            val chunkSize = buffer.int

            if (chunkSize < 0 || buffer.remaining() < chunkSize) {
                throw IllegalArgumentException("Chunk '$chunkId' terpotong atau ukuran tidak valid ($chunkSize byte)")
            }

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16) {
                        throw IllegalArgumentException("Chunk fmt terlalu pendek ($chunkSize byte)")
                    }
                    audioFormat = buffer.short.toInt() and 0xFFFF
                    numChannels = buffer.short.toInt() and 0xFFFF
                    sampleRate = buffer.int
                    buffer.int // byteRate
                    buffer.short // blockAlign
                    bitsPerSample = buffer.short.toInt() and 0xFFFF

                    // Lewati sisa fmt chunk jika ada extension bytes
                    val extraFmtBytes = chunkSize - 16
                    if (extraFmtBytes > 0) {
                        buffer.position(buffer.position() + extraFmtBytes)
                    }

                    if (audioFormat != WAVE_FORMAT_PCM) {
                        throw IllegalArgumentException("Format audio tidak didukung: format code 0x${audioFormat.toString(16)} (hanya PCM standar yang didukung, float/extensible ditolak)")
                    }
                    if (numChannels != 1) {
                        throw IllegalArgumentException("Format channel tidak didukung: $numChannels channel (hanya mono yang didukung, stereo ditolak)")
                    }
                    if (bitsPerSample != 16) {
                        throw IllegalArgumentException("Resolusi bit tidak didukung: $bitsPerSample bit (hanya 16-bit mono yang didukung)")
                    }
                    if (sampleRate <= 0) {
                        throw IllegalArgumentException("Sample rate tidak valid: $sampleRate Hz")
                    }

                    formatParsed = true
                }
                "data" -> {
                    if (!formatParsed) {
                        throw IllegalArgumentException("Chunk data ditemukan sebelum fmt chunk")
                    }
                    pcmData = ByteArray(chunkSize)
                    buffer.get(pcmData)
                }
                else -> {
                    // Chunk tak dikenal (seperti LIST, INFO, JUNK): lewati secara aman
                    buffer.position(buffer.position() + chunkSize)
                }
            }

            // Chunk padding jika chunkSize ganjil
            if (chunkSize % 2 != 0 && buffer.hasRemaining()) {
                buffer.get()
            }
        }

        if (!formatParsed) {
            throw IllegalArgumentException("Chunk 'fmt ' tidak ditemukan di dalam file WAV")
        }
        if (pcmData == null) {
            throw IllegalArgumentException("Chunk 'data' tidak ditemukan atau terpotong di dalam file WAV")
        }

        return WavPcm(
            sampleRate = sampleRate,
            channels = numChannels,
            bitsPerSample = bitsPerSample,
            pcmData = pcmData
        )
    }
}
