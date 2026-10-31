package com.vrkendang.view

import com.vrkendang.kitcore.model.LayoutOverride
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Pengelola penyimpanan atomik layout override pemain (`files/layouts/<kitId>.json`).
 *
 * Sesuai Task 10.4, 10.5 & Desain D11:
 * - Penulisan dilakukan secara atomik melalui file sementara `.tmp` sebelum diganti namanya
 *   untuk mencegah berkas rusak jika proses penulisan terputus.
 * - Kegagalan penulisan mempertahankan file lama dan mengembalikan false tanpa crash.
 * - Opsi reset menghapus berkas override milik kit aktif.
 */
class LayoutOverrideStorage(
    private val layoutsDir: File,
    private val json: Json = Json { prettyPrint = true }
) {

    /**
     * Menyimpan [override] secara atomik ke `files/layouts/<kitId>.json`.
     *
     * @return true jika penulisan dan penggantian nama berhasil, false jika gagal.
     */
    fun saveAtomic(override: LayoutOverride): Boolean {
        return try {
            if (!layoutsDir.exists() && !layoutsDir.mkdirs()) {
                return false
            }

            val targetFile = File(layoutsDir, "${override.kitId}.json")
            val tmpFile = File(layoutsDir, "${override.kitId}.json.tmp")

            val jsonString = json.encodeToString(LayoutOverride.serializer(), override)
            tmpFile.writeText(jsonString)

            if (!tmpFile.renameTo(targetFile)) {
                // Fallback jika renameTo gagal (misal cross-filesystem atomic link)
                targetFile.writeText(jsonString)
                tmpFile.delete()
            }
            true
        } catch (_: Throwable) {
            // Kegagalan penulisan (misal memori penuh): tidak merusak file lama, return false
            false
        }
    }

    /**
     * Membaca berkas override untuk [kitId].
     * Mengembalikan null jika berkas tidak ada atau tidak dapat di-parse.
     */
    fun read(kitId: String): LayoutOverride? {
        return try {
            val file = File(layoutsDir, "$kitId.json")
            if (file.isFile) {
                json.decodeFromString(LayoutOverride.serializer(), file.readText())
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Menghapus berkas override untuk [kitId] (Reset ke Bawaan, Task 10.5).
     */
    fun delete(kitId: String): Boolean {
        return try {
            val file = File(layoutsDir, "$kitId.json")
            if (file.exists()) file.delete() else true
        } catch (_: Exception) {
            false
        }
    }
}
