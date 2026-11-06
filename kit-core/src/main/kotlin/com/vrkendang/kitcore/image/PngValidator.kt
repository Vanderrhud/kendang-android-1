package com.vrkendang.kitcore.image

/**
 * Validasi signature dan IHDR PNG di Kotlin/JVM murni tanpa dependensi `android.*`.
 *
 * Digunakan untuk:
 * - Memastikan file yang dirujuk benar-benar PNG sebelum diekstrak/dikemas.
 * - Membaca dimensi width & height dari header IHDR tanpa dekode piksel.
 */
object PngValidator {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )

    data class Header(
        val width: Int,
        val height: Int,
        val bitDepth: Int,
        val colorType: Int
    ) {
        val isSquare: Boolean get() = width == height
    }

    /**
     * Memvalidasi bahwa [bytes] merupakan PNG valid dengan chunk IHDR yang utuh.
     * Mengembalikan [Header] jika valid, atau melempar [IllegalArgumentException] jika rusak atau bukan PNG.
     */
    fun validate(bytes: ByteArray): Header {
        if (bytes.size < 33) {
            throw IllegalArgumentException("Ukuran file terlalu kecil untuk format PNG (panjang: ${bytes.size} byte)")
        }

        for (i in PNG_SIGNATURE.indices) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                throw IllegalArgumentException("Bukan file PNG: signature tidak cocok pada indeks $i")
            }
        }

        // IHDR chunk:
        // offset 8..11: length (harus 13)
        // offset 12..15: chunk type ("IHDR")
        val ihdrLength = readIntBigEndian(bytes, 8)
        if (ihdrLength != 13) {
            throw IllegalArgumentException("Format PNG tidak valid: panjang chunk IHDR harus 13 (ditemukan: $ihdrLength)")
        }

        val type = String(bytes, 12, 4, Charsets.US_ASCII)
        if (type != "IHDR") {
            throw IllegalArgumentException("Format PNG tidak valid: chunk pertama harus IHDR (ditemukan: '$type')")
        }

        val width = readIntBigEndian(bytes, 16)
        val height = readIntBigEndian(bytes, 20)
        val bitDepth = bytes[24].toInt() and 0xFF
        val colorType = bytes[25].toInt() and 0xFF

        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Dimensi PNG tidak valid: ${width}x${height}")
        }

        return Header(width = width, height = height, bitDepth = bitDepth, colorType = colorType)
    }

    private fun readIntBigEndian(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }
}
