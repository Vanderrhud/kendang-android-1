package com.vrkendang.kitcore.packageio

import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.parser.KitJsonParser
import com.vrkendang.kitcore.validation.KitValidator
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Penyusun paket .vrkendang (ZIP).
 * Mengemas kit.json dan hanya file WAV yang dirujuk oleh zona kit (tanpa file WAV yatim).
 */
object KitPackageWriter {

    /**
     * Menyusun paket .vrkendang ke OutputStream.
     * Hanya file WAV yang dirujuk oleh zona kit.json yang disertakan.
     */
    fun writePackage(
        kit: KitData,
        wavProvider: (soundPath: String) -> ByteArray?,
        outputStream: OutputStream
    ) {
        KitValidator.validateOrThrow(kit)

        val jsonString = KitJsonParser.stringify(kit)

        ZipOutputStream(outputStream).use { zos ->
            // 1. Tulis kit.json
            val jsonEntry = ZipEntry("kit.json")
            zos.putNextEntry(jsonEntry)
            zos.write(jsonString.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Kumpulkan path WAV yang dirujuk secara unik
            val referencedWavPaths = mutableSetOf<String>()
            for (drum in kit.drums) {
                for (zone in drum.zones) {
                    if (zone.sound != null) {
                        referencedWavPaths.add(zone.sound)
                    }
                }
            }

            // 3. Tulis file WAV yang dirujuk saja
            for (path in referencedWavPaths) {
                val wavBytes = wavProvider(path)
                    ?: throw IllegalArgumentException("File WAV yang dirujuk tidak ditemukan: '$path'")
                val entryName = if (path.startsWith("/")) path.substring(1) else path
                val wavEntry = ZipEntry(entryName)
                zos.putNextEntry(wavEntry)
                zos.write(wavBytes)
                zos.closeEntry()
            }
        }
    }

    /**
     * Menyusun paket .vrkendang dan mengembalikan byte array di memori.
     */
    fun toByteArray(
        kit: KitData,
        wavProvider: (soundPath: String) -> ByteArray?
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        writePackage(kit, wavProvider, baos)
        return baos.toByteArray()
    }
}
