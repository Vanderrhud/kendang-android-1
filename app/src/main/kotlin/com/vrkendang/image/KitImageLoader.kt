package com.vrkendang.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.vrkendang.kitcore.model.KitData
import java.io.InputStream

/**
 * Pemuat gambar kendang dari [KitImageSource] ke RAM sebagai [Bitmap] sebelum ditampilkan di [com.vrkendang.view.KendangBoardView].
 *
 * Sesuai Desain & Spesifikasi:
 * - Pemuatan dan pendekodean dilakukan di luar `onDraw` dan di luar jalur sentuh multi-touch.
 * - Menggunakan `inSampleSize` agar dimensi terpanjang tidak melebihi [MAX_IMAGE_DIMENSION] (1024 px).
 * - Menangani kegagalan secara aman (graceful fallback): jika file tidak ada atau rusak, kendang tersebut
 *   diperlakukan sebagai tanpa gambar, dicatat ke log, dan aplikasi tidak crash.
 * - Menyediakan [releaseBitmaps] untuk membebaskan memori saat kit berganti.
 */
class KitImageLoader(
    private val bitmapDecoder: (InputStream) -> Bitmap? = { stream ->
        decodeSampledBitmapFromStream(stream, MAX_IMAGE_DIMENSION)
    },
    private val logSink: (String) -> Unit = { message -> Log.w(TAG, message) }
) {

    companion object {
        const val MAX_IMAGE_DIMENSION = 1024
        private const val TAG = "KitImageLoader"

        /**
         * Mendekode bitmap dari stream dengan menghitung `inSampleSize` secara aman agar tidak memakan RAM berlebih.
         */
        fun decodeSampledBitmapFromStream(stream: InputStream, maxDimension: Int): Bitmap? {
            val bytes = try {
                stream.readBytes()
            } catch (e: Exception) {
                return null
            }
            if (bytes.isEmpty()) return null

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            var sampleSize = 1
            val maxOut = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while ((maxOut / sampleSize) > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        }
    }

    /**
     * Memuat seluruh gambar PNG untuk drum yang memiliki `image != null` dalam [kit].
     * Mengembalikan pemetaan `drum.id` -> [Bitmap].
     */
    fun loadImages(
        kit: KitData,
        imageSource: KitImageSource
    ): Map<String, Bitmap> {
        val loadedMap = linkedMapOf<String, Bitmap>()

        for (drum in kit.drums) {
            val imagePath = drum.image ?: continue
            try {
                imageSource.open(imagePath).use { stream ->
                    val bitmap = bitmapDecoder(stream)
                    if (bitmap != null) {
                        loadedMap[drum.id] = bitmap
                    } else {
                        logSink("Gagal mendekode gambar untuk drum '${drum.id}' pada path '$imagePath'")
                    }
                }
            } catch (e: Exception) {
                logSink("Gagal membaca gambar untuk drum '${drum.id}' pada path '$imagePath': ${e.message}")
            }
        }

        return loadedMap
    }

    /**
     * Membebaskan seluruh bitmap lama saat kit dinonaktifkan atau diganti.
     */
    fun releaseBitmaps(bitmaps: Map<String, Bitmap>) {
        for ((_, bmp) in bitmaps) {
            try {
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
            } catch (e: Throwable) {
                // Abaikan kesalahan recycle pada mock atau lingkungan pengujian
            }
        }
    }
}
