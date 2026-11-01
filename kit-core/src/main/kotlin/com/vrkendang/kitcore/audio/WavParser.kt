package com.vrkendang.kitcore.audio

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Parser audio WAV universal untuk memvalidasi dan mengonversi format audio WAV ke PCM 16-bit mono standar.
 *
 * Mendukung:
 * - PCM standar (format 0x0001): 16-bit dan 24-bit, mono dan stereo.
 * - IEEE Float (format 0x0003): 32-bit float, mono dan stereo.
 * - WAVE_FORMAT_EXTENSIBLE (format 0xFFFE): sub-format PCM (00000001-...) dan IEEE Float (00000003-...).
 * - Downmixing otomatis stereo (2 channel) ke mono: (left + right) / 2.
 * - Konversi bit depth otomatis ke 16-bit PCM:
 *   - 24-bit PCM dikonversi via arithmetic shift right 8 (`sample24 shr 8`).
 *   - 32-bit Float dikonversi dengan clamping rentang [-1.0f, 1.0f], penanganan NaN, dan penskalaan ke int16.
 * - Mengabaikan chunk metadata arbitrer (seperti bext, LIST, acid, JUNK, PAD, fact) dan RIFF padding.
 *
 * Menghasilkan objek [WavPcm] dengan channels = 1 dan bitsPerSample = 16 untuk kompatibilitas penuh
 * dengan pipeline downstream (SoundLoader pada :app dan native AudioEngine Oboe).
 */
object WavParser {

    const val WAVE_FORMAT_PCM = 1
    const val WAVE_FORMAT_IEEE_FLOAT = 3
    const val WAVE_FORMAT_EXTENSIBLE = 0xFFFE

    /**
     * Microsoft KS DataFormat Subtype GUID untuk PCM dan IEEE Float:
     * KSDATAFORMAT_SUBTYPE_PCM:        00000001-0000-0010-8000-00aa00389b71
     * KSDATAFORMAT_SUBTYPE_IEEE_FLOAT: 00000003-0000-0010-8000-00aa00389b71
     */
    private val KS_SUBTYPE_PCM_GUID = byteArrayOf(
        0x01, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x10, 0x00,
        0x80.toByte(), 0x00, 0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71
    )

    private val KS_SUBTYPE_FLOAT_GUID = byteArrayOf(
        0x03, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x10, 0x00,
        0x80.toByte(), 0x00, 0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71
    )

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
        var effectiveFormat = 0
        var numChannels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var pcmRawBytes: ByteArray? = null

        // 2. Iterasi chunks dalam file WAV
        chunkLoop@ while (buffer.remaining() >= 8) {
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
                        throw IllegalArgumentException("Chunk fmt terlalu pendek ($chunkSize byte, minimum 16 byte)")
                    }
                    audioFormat = buffer.short.toInt() and 0xFFFF
                    numChannels = buffer.short.toInt() and 0xFFFF
                    sampleRate = buffer.int
                    buffer.int // byteRate diabaikan karena diturunkan dari format & sampleRate
                    buffer.short // blockAlign diabaikan
                    bitsPerSample = buffer.short.toInt() and 0xFFFF

                    effectiveFormat = audioFormat

                    if (audioFormat == WAVE_FORMAT_EXTENSIBLE) {
                        // Format extensible memerlukan minimal 40 byte pada chunk fmt:
                        // 16 byte base + 2 byte cbSize + 2 byte validBits + 4 byte channelMask + 16 byte SubFormat GUID
                        if (chunkSize < 40) {
                            throw IllegalArgumentException("Chunk fmt extensible terlalu pendek ($chunkSize byte, minimum 40 byte)")
                        }
                        val cbSize = buffer.short.toInt() and 0xFFFF
                        if (cbSize < 22) {
                            throw IllegalArgumentException("cbSize extensible tidak valid ($cbSize byte, minimum 22 byte)")
                        }
                        buffer.short // wValidBitsPerSample
                        buffer.int // dwChannelMask

                        val subFormatGuid = ByteArray(16)
                        buffer.get(subFormatGuid)

                        effectiveFormat = when {
                            subFormatGuid.contentEquals(KS_SUBTYPE_PCM_GUID) -> WAVE_FORMAT_PCM
                            subFormatGuid.contentEquals(KS_SUBTYPE_FLOAT_GUID) -> WAVE_FORMAT_IEEE_FLOAT
                            else -> throw IllegalArgumentException("Sub-format GUID tidak didukung pada WAVE_FORMAT_EXTENSIBLE")
                        }

                        val extraFmtBytes = chunkSize - 40
                        if (extraFmtBytes > 0) {
                            buffer.position(buffer.position() + extraFmtBytes)
                        }
                    } else {
                        val extraFmtBytes = chunkSize - 16
                        if (extraFmtBytes > 0) {
                            buffer.position(buffer.position() + extraFmtBytes)
                        }
                    }

                    if (sampleRate <= 0) {
                        throw IllegalArgumentException("Sample rate tidak valid: $sampleRate Hz")
                    }
                    if (numChannels != 1 && numChannels != 2) {
                        throw IllegalArgumentException("Format channel tidak didukung: $numChannels channel (hanya mono dan stereo yang didukung)")
                    }

                    when (effectiveFormat) {
                        WAVE_FORMAT_PCM -> {
                            if (bitsPerSample != 16 && bitsPerSample != 24) {
                                throw IllegalArgumentException("Resolusi bit PCM tidak didukung: $bitsPerSample bit (hanya 16-bit dan 24-bit yang didukung)")
                            }
                        }
                        WAVE_FORMAT_IEEE_FLOAT -> {
                            if (bitsPerSample != 32) {
                                throw IllegalArgumentException("Resolusi bit IEEE Float tidak didukung: $bitsPerSample bit (hanya 32-bit yang didukung)")
                            }
                        }
                        else -> {
                            throw IllegalArgumentException("Format audio tidak didukung: format code 0x${audioFormat.toString(16)}")
                        }
                    }

                    formatParsed = true
                }
                "data" -> {
                    if (!formatParsed) {
                        throw IllegalArgumentException("Chunk data ditemukan sebelum fmt chunk")
                    }
                    pcmRawBytes = ByteArray(chunkSize)
                    buffer.get(pcmRawBytes)
                    // Hentikan pemindaian chunk setelah 'data' berhasil dibaca untuk mengisolasi
                    // parser dari trailing metadata (seperti ID3v2 tag, chunk tak ber-padding, dll.)
                    break@chunkLoop
                }
                else -> {
                    // Chunk metadata/alignment non-audio (seperti bext, LIST, INFO, acid, JUNK, PAD, fact): lewati secara aman
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
        val nonNullPcm = pcmRawBytes
            ?: throw IllegalArgumentException("Chunk 'data' tidak ditemukan atau terpotong di dalam file WAV")

        val normalizedPcm = convertTo16BitMono(
            rawBytes = nonNullPcm,
            effectiveFormat = effectiveFormat,
            channels = numChannels,
            bitsPerSample = bitsPerSample
        )

        return WavPcm(
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
            pcmData = normalizedPcm
        )
    }

    /**
     * Mengonversi buffer PCM mentah (mono/stereo, 16/24-bit integer, atau 32-bit float)
     * menjadi buffer PCM 16-bit mono bertanda (signed little-endian).
     */
    private fun convertTo16BitMono(
        rawBytes: ByteArray,
        effectiveFormat: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val bytesPerSample = bitsPerSample / 8
        val bytesPerFrame = channels * bytesPerSample
        val frameCount = rawBytes.size / bytesPerFrame
        val output = ByteArray(frameCount * 2)
        val outBuf = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        val inBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)

        when (effectiveFormat) {
            WAVE_FORMAT_PCM -> {
                when (bitsPerSample) {
                    16 -> {
                        if (channels == 1) {
                            return if (rawBytes.size == frameCount * 2) rawBytes else rawBytes.copyOf(frameCount * 2)
                        } else if (channels == 2) {
                            for (i in 0 until frameCount) {
                                val left = inBuf.short.toInt()
                                val right = inBuf.short.toInt()
                                val avg = (left + right) / 2
                                outBuf.putShort(avg.toShort())
                            }
                        }
                    }
                    24 -> {
                        if (channels == 1) {
                            for (i in 0 until frameCount) {
                                val offset = i * 3
                                val b0 = rawBytes[offset].toInt() and 0xFF
                                val b1 = rawBytes[offset + 1].toInt() and 0xFF
                                val b2 = rawBytes[offset + 2].toInt()
                                val s24 = (b2 shl 16) or (b1 shl 8) or b0
                                outBuf.putShort((s24 shr 8).toShort())
                            }
                        } else if (channels == 2) {
                            for (i in 0 until frameCount) {
                                val offset = i * 6
                                val l0 = rawBytes[offset].toInt() and 0xFF
                                val l1 = rawBytes[offset + 1].toInt() and 0xFF
                                val l2 = rawBytes[offset + 2].toInt()
                                val left24 = (l2 shl 16) or (l1 shl 8) or l0

                                val r0 = rawBytes[offset + 3].toInt() and 0xFF
                                val r1 = rawBytes[offset + 4].toInt() and 0xFF
                                val r2 = rawBytes[offset + 5].toInt()
                                val right24 = (r2 shl 16) or (r1 shl 8) or r0

                                val avg24 = (left24 + right24) / 2
                                outBuf.putShort((avg24 shr 8).toShort())
                            }
                        }
                    }
                }
            }
            WAVE_FORMAT_IEEE_FLOAT -> {
                if (channels == 1) {
                    for (i in 0 until frameCount) {
                        val f = inBuf.float
                        outBuf.putShort(floatToShort(f))
                    }
                } else if (channels == 2) {
                    for (i in 0 until frameCount) {
                        val left = inBuf.float
                        val right = inBuf.float
                        val avg = (left + right) / 2.0f
                        outBuf.putShort(floatToShort(avg))
                    }
                }
            }
        }

        return output
    }

    /**
     * Mengonversi satu sampel 32-bit floating point [-1.0f, 1.0f] ke integer 16-bit bertanda [-32768, 32767].
     * Menangani kondisi NaN (dijadikan 0.0f) dan melakukan clamping defensif terhadap rentang sinyal audio.
     */
    private fun floatToShort(value: Float): Short {
        val f = if (value.isNaN()) 0.0f else value.coerceIn(-1.0f, 1.0f)
        return if (f < 0.0f) {
            (f * 32768.0f).roundToInt().coerceIn(-32768, 32767).toShort()
        } else {
            (f * 32767.0f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
