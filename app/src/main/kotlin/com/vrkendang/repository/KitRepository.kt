package com.vrkendang.repository

import android.content.res.AssetManager
import android.util.Log
import com.vrkendang.kitcore.layout.LayoutMerger
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.parser.KitJsonParser
import com.vrkendang.kitcore.validation.KitValidator
import java.io.File
import java.io.InputStream

/**
 * Sumber aset kit yang dapat diganti implementasinya saat unit test JVM.
 *
 * [AssetManager] adalah kelas Android final sehingga tidak dapat di-subclass di
 * unit test. Abstraksi tipis ini memungkinkan test menyuntikkan aset palsu tanpa
 * membutuhkan Robolectric atau perangkat.
 */
interface KitAssetSource {
    /** Mengembalikan nama anak langsung dari [path], atau array kosong bila tidak ada. */
    fun list(path: String): Array<String>

    /** Membuka file aset pada [path]. */
    fun open(path: String): InputStream
}

/** Implementasi [KitAssetSource] yang membaca dari `assets/` APK. */
class AssetManagerKitAssetSource(
    private val assetManager: AssetManager
) : KitAssetSource {
    override fun list(path: String): Array<String> =
        assetManager.list(path) ?: emptyArray()

    override fun open(path: String): InputStream =
        assetManager.open(path)
}

/**
 * Sumber layout override pemain (`files/layouts/<kitId>.json`).
 * Dipisahkan agar [KitRepository] dapat diuji tanpa filesystem Android.
 */
fun interface LayoutOverrideSource {
    /** Mengembalikan isi JSON override, atau null bila belum ada. */
    fun read(kitId: String): String?
}

/**
 * Membaca layout override pemain dari direktori internal aplikasi.
 * Berkas yang tidak terbaca dianggap tidak ada agar tata letak bawaan dipakai.
 */
class FileLayoutOverrideSource(
    private val layoutsDir: File
) : LayoutOverrideSource {
    override fun read(kitId: String): String? {
        return try {
            val file = File(layoutsDir, "$kitId.json")
            if (file.isFile) file.readText() else null
        } catch (e: Exception) {
            // Izin/IO bermasalah: fallback ke tata letak bawaan tanpa crash.
            null
        }
    }
}

/**
 * Repositori kit dari `assets/kits/` dan penyimpanan internal `filesDir/kits/`.
 *
 * Tanggung jawab:
 * - Mendaftar seluruh kit yang lolos [KitValidator].
 * - Melewati kit rusak/invalid dan mencatat alasannya ke log.
 * - Menolak `kitId` duplikat antar aset, serta mengutamakan versi tersimpan di `internalKitsDir` bila developer menyimpan perubahan atas kit bawaan.
 * - Tidak memunculkan direktori kerja developer (`dev-kit`) di daftar pemain.
 * - Menerapkan layout override pemain melalui [LayoutMerger].
 * - Menyediakan fallback ke kit sebelumnya atau `new-metro` bila kit yang
 *   dipilih tidak valid/tidak ditemukan.
 */
class KitRepository(
    private val assetSource: KitAssetSource,
    private val layoutOverrideSource: LayoutOverrideSource = LayoutOverrideSource { null },
    private val logSink: (String) -> Unit = { message -> Log.e(TAG, message) },
    private val internalKitsDir: File? = null
) {

    companion object {
        /** Direktori kit bawaan di dalam APK. */
        const val KITS_ASSET_ROOT = "kits"

        /** Nama direktori kerja Mode Developer yang tidak boleh tampil ke pemain. */
        const val DEV_KIT_DIR_NAME = "dev-kit"

        /** Kit bawaan pertama yang menjadi fallback startup. */
        const val DEFAULT_KIT_ID = "new-metro"

        private const val TAG = "KitRepository"
        private const val KIT_JSON = "kit.json"
    }

    private var cachedKits: List<KitData> = emptyList()
    private var hasLoaded = false
    private var activeKitId: String? = null

    /** Kit yang sedang aktif, atau null sebelum [selectKit] dipanggil. */
    val activeKit: KitData? get() = activeKitId?.let { id -> cachedKits.firstOrNull { it.kitId == id } }

    /**
     * Memindai ulang `internalKitsDir` (jika ada) dan `assets/kits/`, lalu mengembalikan
     * kit valid yang sudah digabung dengan layout override pemain.
     */
    fun loadKits(): List<KitData> {
        val kits = mutableListOf<KitData>()
        val seenKitIds = mutableSetOf<String>()
        val internalKitIds = mutableSetOf<String>()

        // 1. Muat kit yang tersimpan di penyimpanan internal aplikasi (filesDir/kits/<kitId>/)
        if (internalKitsDir != null && internalKitsDir.isDirectory) {
            val internalFolders = internalKitsDir.listFiles()
                ?.filter { it.isDirectory && it.name != DEV_KIT_DIR_NAME }
                ?.sortedBy { it.name }
                ?: emptyList()

            for (dir in internalFolders) {
                val kitFile = File(dir, KIT_JSON)
                if (!kitFile.isFile) continue
                val baseKit = try {
                    KitJsonParser.parse(kitFile.readText())
                } catch (e: Exception) {
                    logSink("Kit internal '${dir.name}' dilewati: gagal mengurai kit.json — ${e.message}")
                    continue
                }
                val validationErrors = KitValidator.validate(baseKit)
                if (validationErrors.isNotEmpty()) {
                    logSink("Kit internal '${dir.name}' dilewati: validasi gagal — ${validationErrors.joinToString("; ")}")
                    continue
                }
                if (seenKitIds.add(baseKit.kitId)) {
                    internalKitIds.add(baseKit.kitId)
                    kits.add(applyPlayerLayoutOverride(baseKit))
                }
            }
        }

        // 2. Muat kit bawaan dari assets/kits/
        val folderNames = try {
            assetSource.list(KITS_ASSET_ROOT).sorted()
        } catch (e: Exception) {
            logSink("Gagal membaca daftar '$KITS_ASSET_ROOT': ${e.message}")
            emptyList<String>()
        }

        for (folderName in folderNames) {
            if (folderName == DEV_KIT_DIR_NAME) {
                // Direktori kerja developer tidak boleh muncul di daftar pemain.
                logSink("Folder '$DEV_KIT_DIR_NAME' diabaikan: bukan kit pemain")
                continue
            }

            val assetPath = "$KITS_ASSET_ROOT/$folderName/$KIT_JSON"
            val baseKit = try {
                assetSource.open(assetPath).bufferedReader().use { it.readText() }
                    .let(KitJsonParser::parse)
            } catch (e: Exception) {
                logSink("Kit '$folderName' dilewati: gagal membaca/mengurai '$assetPath' — ${e.message}")
                continue
            }

            val validationErrors = KitValidator.validate(baseKit)
            if (validationErrors.isNotEmpty()) {
                logSink(
                    "Kit '$folderName' dilewati: validasi gagal — " +
                        validationErrors.joinToString("; ")
                )
                continue
            }

            if (baseKit.kitId in internalKitIds) {
                // Kit ini sudah memiliki versi simpanan terbaru di internalKitsDir
                continue
            }

            if (!seenKitIds.add(baseKit.kitId)) {
                logSink(
                    "Kit '$folderName' ditolak: kitId '${baseKit.kitId}' duplikat dengan kit sebelumnya"
                )
                continue
            }

            kits.add(applyPlayerLayoutOverride(baseKit))
        }

        cachedKits = kits
        hasLoaded = true

        // Jaga referensi kit aktif tetap menunjuk data yang baru dimuat.
        if (activeKitId != null && kits.none { it.kitId == activeKitId }) {
            activeKitId = null
        }

        return kits
    }

    /** Mengembalikan daftar kit tervalidasi, memuat dari aset bila belum pernah. */
    fun getKits(): List<KitData> {
        if (!hasLoaded) {
            loadKits()
        }
        return cachedKits
    }

    /**
     * Membaca kit dasar tanpa penerapan layout override pemain.
     * Mengutamakan versi di `internalKitsDir` bila ada, lalu `assets/kits/`.
     */
    fun getBaseKit(kitId: String): KitData? {
        if (internalKitsDir != null && internalKitsDir.isDirectory) {
            val internalFile = File(File(internalKitsDir, kitId), KIT_JSON)
            if (internalFile.isFile) {
                try {
                    val parsed = KitJsonParser.parse(internalFile.readText())
                    if (parsed.kitId == kitId && KitValidator.validate(parsed).isEmpty()) {
                        return parsed
                    }
                } catch (_: Exception) {}
            }
        }

        val folderNames = try {
            assetSource.list(KITS_ASSET_ROOT)
        } catch (_: Exception) {
            return null
        }
        for (folder in folderNames) {
            if (folder == DEV_KIT_DIR_NAME) continue
            try {
                val baseKit = assetSource.open("$KITS_ASSET_ROOT/$folder/$KIT_JSON")
                    .bufferedReader().use { it.readText() }
                    .let(KitJsonParser::parse)
                if (baseKit.kitId == kitId && KitValidator.validate(baseKit).isEmpty()) {
                    return baseKit
                }
            } catch (_: Exception) {
                // Lewati folder yang gagal dibaca
            }
        }
        return null
    }

    /**
     * Membuka stream file suara milik [kitId] pada [relativePath] (misal `sounds/bass-1.wav`).
     * Mengutamakan file di `internalKitsDir` bila ada, lalu jatuh ke `assets/kits/`.
     */
    fun openSoundStream(kitId: String, relativePath: String): InputStream {
        if (internalKitsDir != null) {
            val internalSound = File(File(internalKitsDir, kitId), relativePath)
            if (internalSound.isFile) {
                return internalSound.inputStream()
            }
        }
        return assetSource.open("$KITS_ASSET_ROOT/$kitId/$relativePath")
    }

    /**
     * Membuka stream file gambar milik [kitId] pada [relativePath] (misal `images/bass.png`).
     * Mengutamakan file di `internalKitsDir` bila ada, lalu jatuh ke `assets/kits/`.
     */
    fun openImageStream(kitId: String, relativePath: String): InputStream {
        if (internalKitsDir != null) {
            val internalImage = File(File(internalKitsDir, kitId), relativePath)
            if (internalImage.isFile) {
                return internalImage.inputStream()
            }
        }
        return assetSource.open("$KITS_ASSET_ROOT/$kitId/$relativePath")
    }

    /**
     * Memilih kit berdasarkan [kitId].
     *
     * Bila kit tidak valid/tidak ditemukan, pertahankan kit yang sebelumnya
     * aktif. Bila belum ada kit aktif, fallback ke `new-metro`, lalu kit valid
     * pertama. Kit yang dikembalikan selalu berasal dari daftar tervalidasi.
     */
    fun selectKit(kitId: String): KitData {
        val kits = getKits()
        val requested = kits.firstOrNull { it.kitId == kitId }
        if (requested != null) {
            activeKitId = requested.kitId
            return requested
        }

        val previous = activeKit
        val fallback = previous
            ?: kits.firstOrNull { it.kitId == DEFAULT_KIT_ID }
            ?: kits.firstOrNull()
            ?: throw IllegalStateException(
                "Tidak ada kit valid di '$KITS_ASSET_ROOT'; periksa kit bawaan APK"
            )

        logSink(
            "Kit '$kitId' tidak valid/tidak ditemukan; fallback ke '${fallback.kitId}'"
        )
        activeKitId = fallback.kitId
        return fallback
    }

    /**
     * Menggabungkan layout override pemain ke kit dasar.
     *
     * Override yang rusak atau menghasilkan kit tidak valid diabaikan agar
     * tata letak bawaan tetap aman dipakai.
     */
    private fun applyPlayerLayoutOverride(baseKit: KitData): KitData {
        val overrideJson = try {
            layoutOverrideSource.read(baseKit.kitId)
        } catch (e: Exception) {
            logSink("Override layout '${baseKit.kitId}' gagal dibaca: ${e.message}")
            null
        }

        val merged = LayoutMerger.mergeFromJson(baseKit, overrideJson)
        if (merged == baseKit) {
            return baseKit
        }

        val errors = KitValidator.validate(merged)
        if (errors.isNotEmpty()) {
            logSink(
                "Override layout '${baseKit.kitId}' diabaikan: kit efektif tidak valid — " +
                    errors.joinToString("; ")
            )
            return baseKit
        }

        return merged
    }
}
