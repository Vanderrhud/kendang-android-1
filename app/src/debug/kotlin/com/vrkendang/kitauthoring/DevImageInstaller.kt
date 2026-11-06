package com.vrkendang.kitauthoring

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vrkendang.image.KitImageLoader
import com.vrkendang.kitcore.image.PngValidator
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.packageio.KitPackageReader
import com.vrkendang.kitcore.parser.KitJsonParser
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Pengelola pemasangan file gambar PNG kendang langsung dari penyimpanan perangkat ke salinan kerja kit.
 *
 * Sesuai Spesifikasi Kit Authoring & Tasks 4.1:
 * - Batas ukuran file maksimum 2 MB ([KitPackageReader.MAX_PNG_SIZE]).
 * - Validasi signature dan chunk IHDR PNG via [PngValidator].
 * - Validasi gambar wajib berbentuk persegi (rasio 1:1, lebar == tinggi). Gambar non-persegi ditolak.
 * - Downsample gambar agar sisi terpanjang <= 1024 px tanpa memperbesar.
 * - Disimpan sebagai `filesDir/dev-kit/images/<drumId>.png` secara atomik via file sementara.
 * - Memperbarui medan `image` kendang terkait di `kit.json`.
 * - Pembatalan atau kegagalan tidak mengubah file lama maupun `kit.json`.
 */
class DevImageInstaller(
    private val json: Json = Json { prettyPrint = true }
) {

    sealed class Result {
        data class Success(
            val drumId: String,
            val width: Int,
            val height: Int,
            val updatedKit: KitData
        ) : Result()

        data class Failure(
            val message: String
        ) : Result()
    }

    /**
     * Memasang file PNG dari [inputStream] ke kendang [drumId].
     */
    fun installPng(
        drumId: String,
        inputStream: InputStream,
        devKitDir: File
    ): Result {
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            return Result.Failure("Gagal membaca file gambar: ${e.message}")
        }

        // 1. Validasi batas ukuran file (maksimum 2 MB)
        if (bytes.size > KitPackageReader.MAX_PNG_SIZE) {
            val sizeMb = bytes.size / (1024 * 1024.0)
            return Result.Failure("Ukuran file melebihi batas 2 MB (terdeteksi: ${String.format("%.1f", sizeMb)} MB)")
        }

        // 2. Validasi format PNG via PngValidator
        val pngHeader = try {
            PngValidator.validate(bytes)
        } catch (e: Exception) {
            return Result.Failure("Format harus file PNG yang valid (${e.message})")
        }

        // 3. Validasi gambar wajib persegi (1:1)
        if (!pngHeader.isSquare) {
            return Result.Failure("Gambar harus berbentuk persegi (rasio 1:1, lebar dan tinggi sama; terdeteksi: ${pngHeader.width}x${pngHeader.height})")
        }

        // 4. Downsample gambar jika perlu (max 1024 px) dan encode ke PNG bytes
        val finalPngBytes = try {
            downsampleToPngBytes(bytes, KitImageLoader.MAX_IMAGE_DIMENSION)
        } catch (e: Exception) {
            return Result.Failure("Gagal memproses gambar: ${e.message}")
        }

        // 5. Pastikan direktori kerja dan images/ tersedia
        val imagesDir = File(devKitDir, "images")
        if (!imagesDir.exists() && !imagesDir.mkdirs()) {
            return Result.Failure("Gagal membuat direktori '${imagesDir.path}'")
        }

        val targetPngFile = File(imagesDir, "$drumId.png")
        val tmpPngFile = File(imagesDir, "$drumId.png.tmp")
        try {
            tmpPngFile.writeBytes(finalPngBytes)
            if (!tmpPngFile.renameTo(targetPngFile)) {
                targetPngFile.writeBytes(finalPngBytes)
                tmpPngFile.delete()
            }
        } catch (e: Exception) {
            return Result.Failure("Gagal menyimpan file gambar: ${e.message}")
        }

        // 6. Perbarui kit.json di salinan kerja
        val kitJsonFile = File(devKitDir, "kit.json")
        val currentKit = try {
            if (kitJsonFile.exists()) {
                KitJsonParser.parse(kitJsonFile.readText())
            } else {
                return Result.Failure("Berkas 'kit.json' tidak ditemukan di ${devKitDir.path}")
            }
        } catch (e: Exception) {
            return Result.Failure("Gagal membaca kit.json: ${e.message}")
        }

        val relativeImagePath = "images/$drumId.png"
        val updatedDrums = currentKit.drums.map { drum ->
            if (drum.id == drumId) drum.copy(image = relativeImagePath) else drum
        }
        val updatedKit = currentKit.copy(drums = updatedDrums)

        try {
            val jsonString = json.encodeToString(KitData.serializer(), updatedKit)
            val tmpJsonFile = File(devKitDir, "kit.json.tmp")
            tmpJsonFile.writeText(jsonString)
            if (!tmpJsonFile.renameTo(kitJsonFile)) {
                kitJsonFile.writeText(jsonString)
                tmpJsonFile.delete()
            }
        } catch (e: Throwable) {
            return Result.Failure("Gagal memperbarui kit.json: ${e.message}")
        }

        return Result.Success(
            drumId = drumId,
            width = minOf(pngHeader.width, KitImageLoader.MAX_IMAGE_DIMENSION),
            height = minOf(pngHeader.height, KitImageLoader.MAX_IMAGE_DIMENSION),
            updatedKit = updatedKit
        )
    }

    /**
     * Mengosongkan gambar kendang (image = null) dan menghapus file PNG-nya.
     */
    fun clearImage(
        drumId: String,
        devKitDir: File
    ): Result {
        val kitJsonFile = File(devKitDir, "kit.json")
        val currentKit = try {
            if (kitJsonFile.exists()) {
                KitJsonParser.parse(kitJsonFile.readText())
            } else {
                return Result.Failure("Berkas 'kit.json' tidak ditemukan di ${devKitDir.path}")
            }
        } catch (e: Exception) {
            return Result.Failure("Gagal membaca kit.json: ${e.message}")
        }

        // Hapus file gambar jika ada
        val imageFile = File(File(devKitDir, "images"), "$drumId.png")
        if (imageFile.exists()) {
            imageFile.delete()
        }

        val updatedDrums = currentKit.drums.map { drum ->
            if (drum.id == drumId) drum.copy(image = null) else drum
        }
        val updatedKit = currentKit.copy(drums = updatedDrums)

        try {
            val jsonString = json.encodeToString(KitData.serializer(), updatedKit)
            val tmpJsonFile = File(devKitDir, "kit.json.tmp")
            tmpJsonFile.writeText(jsonString)
            if (!tmpJsonFile.renameTo(kitJsonFile)) {
                kitJsonFile.writeText(jsonString)
                tmpJsonFile.delete()
            }
        } catch (e: Throwable) {
            return Result.Failure("Gagal memperbarui kit.json: ${e.message}")
        }

        return Result.Success(
            drumId = drumId,
            width = 0,
            height = 0,
            updatedKit = updatedKit
        )
    }

    private fun downsampleToPngBytes(originalBytes: ByteArray, maxDimension: Int): ByteArray {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, boundsOptions)
        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight

        // Jika sudah <= maxDimension, simpan langsung bytes aslinya
        if (origWidth <= maxDimension && origHeight <= maxDimension && origWidth > 0 && origHeight > 0) {
            return originalBytes
        }

        var sampleSize = 1
        val maxSide = maxOf(origWidth, origHeight)
        while ((maxSide / sampleSize) > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOptions)
            ?: throw IllegalStateException("Gagal mendekode bitmap")

        val targetWidth: Int
        val targetHeight: Int
        if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        } else {
            targetWidth = bitmap.width
            targetHeight = bitmap.height
        }

        val scaled = if (targetWidth != bitmap.width || targetHeight != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
        scaled.recycle()
        return baos.toByteArray()
    }
}
