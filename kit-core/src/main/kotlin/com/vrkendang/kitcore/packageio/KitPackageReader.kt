package com.vrkendang.kitcore.packageio

import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.parser.KitJsonParser
import com.vrkendang.kitcore.validation.KitValidator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Pembaca dan ekstraktor paket .vrkendang (ZIP) dengan batas keamanan ketat:
 * - Maksimum 100 entri
 * - Total dekompresi maks 100 MB
 * - Maksimum ukuran per file WAV 5 MB
 * - Penolakan path traversal (.. atau path absolut) sebelum ekstraksi ke disk
 */
object KitPackageReader {

    const val MAX_ENTRIES = 100
    const val MAX_TOTAL_SIZE = 100 * 1024 * 1024L // 100 MB
    const val MAX_WAV_SIZE = 5 * 1024 * 1024L     // 5 MB

    data class ExtractedKit(
        val kitData: KitData,
        val wavFiles: Map<String, ByteArray>
    )

    /**
     * Membaca dan memvalidasi seluruh isi paket ZIP di memori tanpa menulis ke disk.
     */
    fun read(inputStream: InputStream): ExtractedKit {
        val bytes = inputStream.readBytes()
        validateZipStructure(bytes)

        var kitJsonContent: String? = null
        val wavMap = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryBytes = zis.readBytes()
                    val normalizedName = entry.name.replace('\\', '/')
                    if (normalizedName == "kit.json") {
                        kitJsonContent = entryBytes.decodeToString()
                    } else if (normalizedName.startsWith("sounds/") && normalizedName.endsWith(".wav")) {
                        wavMap[normalizedName] = entryBytes
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (kitJsonContent == null) {
            throw IllegalArgumentException("Paket tidak valid: 'kit.json' tidak ditemukan di akar paket")
        }

        val kitData = KitJsonParser.parse(kitJsonContent)
        KitValidator.validateOrThrow(kitData)

        return ExtractedKit(kitData, wavMap)
    }

    /**
     * Mengekstrak isi paket ke direktori tujuan setelah lolos validasi keamanan.
     */
    fun extract(inputStream: InputStream, targetDir: File): KitData {
        val bytes = inputStream.readBytes()
        validateZipStructure(bytes)

        val extracted = read(ByteArrayInputStream(bytes))

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // Tulis kit.json
        File(targetDir, "kit.json").writeText(KitJsonParser.stringify(extracted.kitData))

        // Tulis file WAV
        val soundsDir = File(targetDir, "sounds")
        if (extracted.wavFiles.isNotEmpty() && !soundsDir.exists()) {
            soundsDir.mkdirs()
        }

        for ((relPath, data) in extracted.wavFiles) {
            val destFile = File(targetDir, relPath)
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(data)
        }

        return extracted.kitData
    }

    /**
     * Memvalidasi struktur ZIP dan batas keamanan sebelum ekstraksi.
     */
    private fun validateZipStructure(zipBytes: ByteArray) {
        var entryCount = 0
        var totalDecompressedSize = 0L

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    throw SecurityException("Paket ditolak: jumlah entri melebihi batas maksimum ($MAX_ENTRIES entri)")
                }

                val name = entry.name
                // Pemeriksaan path traversal
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\") || (name.length >= 2 && name[1] == ':')) {
                    throw SecurityException("Paket ditolak: path traversal atau path absolut terdeteksi pada entri: '$name'")
                }

                if (!entry.isDirectory) {
                    var entryBytesRead = 0L
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (zis.read(buffer).also { read = it } != -1) {
                        entryBytesRead += read
                        totalDecompressedSize += read

                        if (totalDecompressedSize > MAX_TOTAL_SIZE) {
                            throw SecurityException("Paket ditolak: total ukuran dekompresi melebihi batas ($MAX_TOTAL_SIZE byte / 100 MB)")
                        }

                        val normalizedName = name.replace('\\', '/')
                        if (normalizedName.endsWith(".wav") && entryBytesRead > MAX_WAV_SIZE) {
                            throw SecurityException("Paket ditolak: file WAV '$name' melebihi batas maksimum ($MAX_WAV_SIZE byte / 5 MB)")
                        }
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (entryCount == 0) {
            throw IllegalArgumentException("Paket ditolak: file ZIP kosong")
        }
    }
}
