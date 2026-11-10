package com.vrkendang.kitauthoring

import android.content.ContentResolver
import android.content.res.AssetManager
import android.net.Uri
import com.vrkendang.audio.AudioEngineApi
import com.vrkendang.audio.AudioEngineApiImpl
import com.vrkendang.kitcore.factory.KitFactory
import com.vrkendang.kitcore.model.DividerData
import com.vrkendang.kitcore.model.DrumBodyData
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius
import com.vrkendang.kitcore.packageio.KitPackageReader
import com.vrkendang.kitcore.packageio.KitPackageWriter
import com.vrkendang.kitcore.parser.KitJsonParser
import com.vrkendang.kitcore.validation.KitValidator
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Pengelola sesi Mode Developer untuk penyusunan kit (Tahap 11).
 *
 * Mengimplementasikan:
 * - Pembukaan kit aktif dari penyimpanan internal (`filesDir/kits/<kitId>/`) atau kit bawaan APK ke `filesDir/dev-kit/`
 * - Simpan langsung menimpa kit aktif di dalam aplikasi (`✓ Simpan` -> `filesDir/kits/<kitId>/`) tanpa pemilih file HP (Task 11.5)
 * - Simpan sebagai Kit Baru di dalam aplikasi (`+ Kit Baru` -> `filesDir/kits/<newKitId>/`) tanpa pemilih file HP (Task 11.5)
 * - Impor dan Ekspor paket ZIP `.vrkendang` secara terpisah via SAF (Task 11.6)
 * - Mode UJI dan EDIT (Task 11.2)
 * - Kontrol edit zona per kendang, gain 0.0–2.0, pasang/kosongkan WAV (Task 11.3)
 * - Parameter divider Bass (dx/dy -3.0..3.0, r 0..3.0) (Task 11.4)
 * - Indikator dan pelacakan perubahan belum disimpan (Task 11.7)
 */
class DevKitAuthoringSession(
    val devKitDir: File,
    private val soundInstaller: DevSoundInstaller = DevSoundInstaller(),
    private val json: Json = Json { prettyPrint = true }
) {

    companion object {
        const val MIN_ASPECT_RATIO = 0.10f

        /**
         * Mengonversi (rx, ry) menjadi pasangan (Ukuran S, Kelonjongan E dalam %).
         * S = max(rx, ry)
         * E in -100.0..+100.0 (negatif = lonjong mendatar rx > ry, positif = lonjong tegak ry > rx, 0 = bulat).
         */
        fun toSizeAndAspect(rx: Float, ry: Float): Pair<Float, Float> {
            val s = max(rx, ry)
            if (s <= 0f) return Pair(0f, 0f)
            val aspect = if (rx >= ry) {
                val ratio = (ry / rx).coerceIn(MIN_ASPECT_RATIO, 1.0f)
                -((1.0f - ratio) / (1.0f - MIN_ASPECT_RATIO)) * 100.0f
            } else {
                val ratio = (rx / ry).coerceIn(MIN_ASPECT_RATIO, 1.0f)
                +((1.0f - ratio) / (1.0f - MIN_ASPECT_RATIO)) * 100.0f
            }
            return Pair(s, aspect.coerceIn(-100.0f, 100.0f))
        }

        /**
         * Mengonversi (Ukuran S, Kelonjongan E dalam %) menjadi Radius(rx, ry).
         */
        fun toRadius(size: Float, aspectPercent: Float): Radius {
            val s = size.coerceIn(0.01f, 5.0f)
            val e = aspectPercent.coerceIn(-100.0f, 100.0f)
            return when {
                e == 0f -> Radius(s, s)
                e < 0f -> {
                    val factor = 1.0f - (1.0f - MIN_ASPECT_RATIO) * (-e / 100.0f)
                    Radius(s, (s * factor).coerceAtLeast(0.001f))
                }
                else -> {
                    val factor = 1.0f - (1.0f - MIN_ASPECT_RATIO) * (e / 100.0f)
                    Radius((s * factor).coerceAtLeast(0.001f), s)
                }
            }
        }
    }

    enum class BoardMode {
        UJI,
        EDIT
    }

    var workingKit: KitData? = null
        private set

    var sourceUri: Uri? = null
        private set

    var hasUnsavedChanges: Boolean = false
        private set

    var mode: BoardMode = BoardMode.UJI

    val soundsDir: File get() = File(devKitDir, "sounds")
    val imagesDir: File get() = File(devKitDir, "images")

    private val devImageInstaller = DevImageInstaller()

    init {
        if (!devKitDir.exists()) devKitDir.mkdirs()
        if (!soundsDir.exists()) soundsDir.mkdirs()
        if (!imagesDir.exists()) imagesDir.mkdirs()
    }

    /**
     * Memuat salinan kerja yang ada di devKitDir jika sudah ada kit.json.
     */
    fun loadExistingWorkingCopy(): KitData? {
        val kitJsonFile = File(devKitDir, "kit.json")
        if (!kitJsonFile.exists()) return null

        return try {
            val kit = KitJsonParser.parse(kitJsonFile.readText())
            workingKit = kit
            hasUnsavedChanges = false
            kit
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Membuka kit aktif (dari `internalKitsDir` bila sudah pernah disimpan, atau dari `assets/kits/` APK)
     * ke dalam `devKitDir` sebagai salinan kerja.
     */
    fun openActiveKitAsWorkingCopy(
        kitId: String,
        internalKitsDir: File,
        assetManager: AssetManager? = null
    ): KitData {
        val savedDir = File(internalKitsDir, kitId)
        val savedJsonFile = File(savedDir, "kit.json")
        if (savedJsonFile.isFile) {
            val kit = KitJsonParser.parse(savedJsonFile.readText())
            soundsDir.deleteRecursively()
            soundsDir.mkdirs()

            val savedSoundsDir = File(savedDir, "sounds")
            if (savedSoundsDir.isDirectory) {
                savedSoundsDir.listFiles()?.forEach { wavFile ->
                    if (wavFile.isFile) {
                        wavFile.copyTo(File(soundsDir, wavFile.name), overwrite = true)
                    }
                }
            }

            imagesDir.deleteRecursively()
            imagesDir.mkdirs()

            val savedImagesDir = File(savedDir, "images")
            if (savedImagesDir.isDirectory) {
                savedImagesDir.listFiles()?.forEach { imgFile ->
                    if (imgFile.isFile) {
                        imgFile.copyTo(File(imagesDir, imgFile.name), overwrite = true)
                    }
                }
            }

            writeKitJsonToDisk(kit)
            workingKit = kit
            sourceUri = null
            hasUnsavedChanges = false
            return kit
        }
        val assets = assetManager ?: throw IllegalArgumentException("AssetManager diperlukan untuk memuat kit bawaan '$kitId'")
        return openBuiltInKit(kitId, assets)
    }

    /**
     * Membuat kit baru dari template bawaan (Task 11.1 / kompatibilitas unit test).
     *
     * @param kitId ID kit baru (hanya huruf kecil, angka, tanda hubung; dilarang sama dengan "new-metro").
     * @param name Nama tampilan kit baru.
     * @param targetUri URI berkas sumber .vrkendang opsional.
     */
    fun createNewKit(kitId: String, name: String, targetUri: Uri?): KitData {
        val newKit = KitFactory.createFromTemplate(kitId, name)

        // Bersihkan direktori sounds lama pada salinan kerja
        soundsDir.deleteRecursively()
        soundsDir.mkdirs()

        // Simpan kit.json baru ke salinan kerja
        writeKitJsonToDisk(newKit)

        workingKit = newKit
        sourceUri = targetUri
        hasUnsavedChanges = (targetUri == null)
        return newKit
    }

    /**
     * Membuka kit bawaan dari APK sebagai salinan kerja (Task 11.1).
     */
    fun openBuiltInKit(kitId: String, assetManager: AssetManager): KitData {
        val assetPath = "kits/$kitId/kit.json"
        val jsonText = assetManager.open(assetPath).bufferedReader().use { it.readText() }
        val kit = KitJsonParser.parse(jsonText)

        // Bersihkan sounds lama
        soundsDir.deleteRecursively()
        soundsDir.mkdirs()

        // Salin jika ada WAV bawaan
        val list = assetManager.list("kits/$kitId/sounds") ?: emptyArray()
        for (fileName in list) {
            try {
                assetManager.open("kits/$kitId/sounds/$fileName").use { input ->
                    File(soundsDir, fileName).writeBytes(input.readBytes())
                }
            } catch (_: Exception) {}
        }

        // Bersihkan images lama
        imagesDir.deleteRecursively()
        imagesDir.mkdirs()

        // Salin jika ada PNG bawaan
        val imageList = assetManager.list("kits/$kitId/images") ?: emptyArray()
        for (fileName in imageList) {
            try {
                assetManager.open("kits/$kitId/images/$fileName").use { input ->
                    File(imagesDir, fileName).writeBytes(input.readBytes())
                }
            } catch (_: Exception) {}
        }

        writeKitJsonToDisk(kit)
        workingKit = kit
        sourceUri = null
        hasUnsavedChanges = false
        return kit
    }

    /**
     * Menyimpan perubahan kit saat ini secara langsung menimpa kit di penyimpanan internal aplikasi
     * (`filesDir/kits/<kitId>/` dan `filesDir/dev-kit/`) tanpa membuka file picker HP (Task 11.5).
     */
    fun saveCurrentEditToInternalStorage(internalKitsDir: File): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val updatedKit = kit.copy(version = kit.version + 1)
        KitValidator.validateOrThrow(updatedKit)

        writeKitJsonToDisk(updatedKit)
        copyWorkingKitToInternalDir(updatedKit, internalKitsDir)

        workingKit = updatedKit
        hasUnsavedChanges = false
        return updatedKit
    }

    /**
     * Menyimpan konfigurasi yang sedang diedit menjadi Kit Baru di penyimpanan internal aplikasi
     * (`filesDir/kits/<newKitId>/`) dengan nama baru dan menyalin sampel suara aktif (Task 11.5).
     */
    fun saveAsNewInternalKit(
        newKitId: String,
        newName: String,
        internalKitsDir: File
    ): KitData {
        val current = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val trimmedId = newKitId.trim()
        val trimmedName = newName.trim()

        // Validasi format ID dan nama menggunakan KitFactory (menolak duplikasi 'new-metro' atau ID tidak valid)
        KitFactory.createFromTemplate(trimmedId, trimmedName)

        val newKit = current.copy(
            kitId = trimmedId,
            name = trimmedName,
            version = 1
        )
        KitValidator.validateOrThrow(newKit)

        writeKitJsonToDisk(newKit)
        copyWorkingKitToInternalDir(newKit, internalKitsDir)

        workingKit = newKit
        sourceUri = null
        hasUnsavedChanges = false
        return newKit
    }

    private fun copyWorkingKitToInternalDir(kit: KitData, internalKitsDir: File) {
        val targetDir = File(internalKitsDir, kit.kitId)
        val targetSoundsDir = File(targetDir, "sounds")
        if (!targetSoundsDir.exists()) targetSoundsDir.mkdirs()

        // Bersihkan WAV yatim di folder tujuan yang tidak lagi dirujuk oleh kit.json
        val referencedFileNames = kit.drums
            .flatMap { it.zones }
            .mapNotNull { it.sound?.substringAfterLast('/') }
            .toSet()

        targetSoundsDir.listFiles()?.forEach { existing ->
            if (existing.name !in referencedFileNames) {
                existing.delete()
            }
        }

        // Salin WAV yang dirujuk dari devKitDir ke filesDir/kits/<kitId>/sounds/
        for (drum in kit.drums) {
            for (zone in drum.zones) {
                val relPath = zone.sound ?: continue
                val srcFile = File(devKitDir, relPath)
                val dstFile = File(targetDir, relPath)
                if (srcFile.isFile) {
                    dstFile.parentFile?.mkdirs()
                    srcFile.copyTo(dstFile, overwrite = true)
                }
            }
        }

        val targetImagesDir = File(targetDir, "images")
        if (!targetImagesDir.exists()) targetImagesDir.mkdirs()

        // Bersihkan PNG yatim di folder tujuan yang tidak lagi dirujuk oleh kit.json
        val referencedImageFileNames = kit.drums
            .flatMap { listOfNotNull(it.image?.substringAfterLast('/'), it.body?.image?.substringAfterLast('/')) }
            .toSet()

        targetImagesDir.listFiles()?.forEach { existing ->
            if (existing.name !in referencedImageFileNames) {
                existing.delete()
            }
        }

        // Salin PNG yang dirujuk dari devKitDir ke filesDir/kits/<kitId>/images/
        for (drum in kit.drums) {
            listOfNotNull(drum.image, drum.body?.image).forEach { relPath ->
                val srcFile = File(devKitDir, relPath)
                val dstFile = File(targetDir, relPath)
                if (srcFile.isFile) {
                    dstFile.parentFile?.mkdirs()
                    srcFile.copyTo(dstFile, overwrite = true)
                }
            }
        }

        // Tulis kit.json secara atomik ke filesDir/kits/<kitId>/kit.json
        val targetJson = File(targetDir, "kit.json")
        val tmpJson = File(targetDir, "kit.json.tmp")
        val jsonText = json.encodeToString(KitData.serializer(), kit)
        tmpJson.writeText(jsonText)
        if (!tmpJson.renameTo(targetJson)) {
            targetJson.writeText(jsonText)
            tmpJson.delete()
        }
    }

    /**
     * Mengimpor file paket .vrkendang dari URI penyimpanan HP (Task 11.6).
     *
     * Mengekstrak ke direktori sementara, memvalidasi integritas,
     * lalu baru menggantikan salinan kerja di filesDir/dev-kit/.
     */
    fun openKitPackage(inputStream: InputStream, uri: Uri, cacheDir: File): KitData {
        val tempExtractDir = File(cacheDir, "temp-kit-extract-${System.currentTimeMillis()}").apply {
            mkdirs()
        }

        try {
            val extracted = KitPackageReader.read(inputStream)
            KitValidator.validateOrThrow(extracted.kitData)

            // Ekstrak file WAV dan kit.json ke salinan kerja
            soundsDir.deleteRecursively()
            soundsDir.mkdirs()

            for ((soundPath, bytes) in extracted.wavFiles) {
                val targetFile = File(devKitDir, soundPath)
                targetFile.parentFile?.mkdirs()
                targetFile.writeBytes(bytes)
            }

            imagesDir.deleteRecursively()
            imagesDir.mkdirs()

            for ((imagePath, bytes) in extracted.imageFiles) {
                val targetFile = File(devKitDir, imagePath)
                targetFile.parentFile?.mkdirs()
                targetFile.writeBytes(bytes)
            }

            writeKitJsonToDisk(extracted.kitData)
            workingKit = extracted.kitData
            sourceUri = uri
            hasUnsavedChanges = false
            return extracted.kitData
        } finally {
            tempExtractDir.deleteRecursively()
        }
    }

    /**
     * Memperbarui nama kit.
     */
    fun updateKitName(newName: String): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val updated = kit.copy(name = newName.trim())
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah posisi center kendang.
     */
    fun updateDrumCenter(drumId: String, newCenter: Point): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == drumId) drum.copy(center = newCenter) else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah ukuran radius kendang (rx, ry).
     */
    fun updateDrumRadius(drumId: String, newRadius: Radius): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == drumId) drum.copy(radius = newRadius) else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah bentuk kendang berdasarkan ukuran (S) dan kelonjongan (E dalam %).
     */
    fun updateDrumShape(drumId: String, size: Float, aspectPercent: Float): KitData {
        val newRadius = toRadius(size, aspectPercent)
        return updateDrumRadius(drumId, newRadius)
    }

    /**
     * Mengatur rotasi orientasi kendang (0.0 s.d. 360.0 derajat).
     */
    fun updateDrumRotation(drumId: String, rotation: Float): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val clampedRotation = ((rotation % 360f) + 360f) % 360f
        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == drumId) drum.copy(rotation = clampedRotation) else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengembalikan bentuk kendang menjadi bulat sempurna (kelonjongan 0%) dan rotasi 0 derajat.
     */
    fun resetDrumShape(drumId: String): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val targetDrum = kit.drums.firstOrNull { it.id == drumId } ?: return kit
        val s = max(targetDrum.radius.rx, targetDrum.radius.ry).takeIf { it > 0f } ?: 0.16f
        val resetRadius = Radius(s, s)
        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == drumId) {
                drum.copy(radius = resetRadius, rotation = 0f)
            } else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Memindahkan kendang ke lapisan teratas (indeks 0 pada array drums)
     * agar berada di lapisan visual paling atas dan diprioritaskan dalam hit-testing saat overlap.
     */
    fun bringDrumToFront(drumId: String): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val index = kit.drums.indexOfFirst { it.id == drumId }
        if (index <= 0) return kit

        val mutableList = kit.drums.toMutableList()
        val item = mutableList.removeAt(index)
        mutableList.add(0, item)

        val updated = kit.copy(drums = mutableList)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah gain suatu zona (rentang 0.0 s.d. 2.0) (Task 11.3).
     */
    fun updateZoneGain(zoneId: String, gain: Float): KitData {
        val clampedGain = gain.coerceIn(0.0f, 2.0f)
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")

        val updatedDrums = kit.drums.map { drum ->
            drum.copy(
                zones = drum.zones.map { zone ->
                    if (zone.id == zoneId) zone.copy(gain = clampedGain) else zone
                }
            )
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah parameter busur divider Bass (dx, dy, r, rx, ry, splits) (Task 11.4).
     *
     * Validasi rentang:
     * dx/dy: -3.0 s.d. 3.0
     * rx/ry: > 0.0 s.d. 3.0
     */
    fun updateBassDivider(
        dx: Float,
        dy: Float,
        r: Float = 0.80f,
        rx: Float = r,
        ry: Float = r,
        splits: List<Float>? = null
    ): KitData {
        if (dx !in -3.0f..3.0f || dy !in -3.0f..3.0f) {
            throw IllegalArgumentException("Parameter dx dan dy harus berada di rentang -3.0 sampai 3.0")
        }
        val effRx = if (rx > 0f) rx else r
        val effRy = if (ry > 0f) ry else r
        if (effRx <= 0.0f || effRx > 3.0f || effRy <= 0.0f || effRy > 3.0f) {
            throw IllegalArgumentException("Parameter rx dan ry harus > 0.0 dan <= 3.0")
        }

        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val updatedDrums = kit.drums.map { drum ->
            if (drum.model == "bass") {
                val currentSplits = splits ?: drum.divider?.splits
                drum.copy(
                    divider = DividerData(
                        dx = dx,
                        dy = dy,
                        r = r,
                        rx = effRx,
                        ry = effRy,
                        splits = currentSplits
                    )
                )
            } else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah jumlah zona aktif pada kendang Bass (1 sampai 5 zona).
     */
    fun setBassZoneCount(count: Int): KitData {
        val targetCount = count.coerceIn(1, 5)
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val bassDrum = kit.drums.firstOrNull { it.model == "bass" } ?: return kit

        // Ambil pemetaan sound dan gain lama agar tidak hilang saat berganti jumlah zona
        val oldZoneMap = bassDrum.zones.associateBy { it.id }

        val newZones = mutableListOf<com.vrkendang.kitcore.model.ZoneData>()
        // Zona 1: Selalu ada (main)
        val oldMain = oldZoneMap["bass-1"]
        newZones.add(
            com.vrkendang.kitcore.model.ZoneData(
                id = "bass-1",
                region = "main",
                sound = oldMain?.sound,
                gain = oldMain?.gain ?: 1.0f
            )
        )

        // Zona Tepi (crescent)
        val crescentCount = targetCount - 1
        for (i in 0 until crescentCount) {
            val zoneId = "bass-${i + 2}"
            val oldZone = oldZoneMap[zoneId]
            newZones.add(
                com.vrkendang.kitcore.model.ZoneData(
                    id = zoneId,
                    region = "crescent",
                    band = i,
                    sound = oldZone?.sound,
                    gain = oldZone?.gain ?: 1.0f
                )
            )
        }

        // Tentukan default splits sesuai jumlah zona crescent
        val defaultSplits = when (crescentCount) {
            2 -> listOf(0.50f)
            3 -> listOf(0.333f, 0.667f)
            4 -> listOf(0.25f, 0.50f, 0.75f)
            else -> emptyList()
        }

        val curDiv = bassDrum.divider ?: DividerData(dx = -0.30f, dy = 0.0f, r = 0.80f)
        val updatedDiv = curDiv.copy(
            splits = if (crescentCount > 1) (curDiv.splits?.takeIf { it.size == crescentCount - 1 } ?: defaultSplits) else emptyList()
        )

        val updatedDrums = kit.drums.map { drum ->
            if (drum.model == "bass") {
                drum.copy(
                    divider = updatedDiv,
                    zones = newZones
                )
            } else drum
        }

        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah posisi garis sekat tertentu pada zona crescent Bass.
     */
    fun updateBassSplit(splitIndex: Int, newSplitPos: Float): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val bassDrum = kit.drums.firstOrNull { it.model == "bass" } ?: return kit
        val div = bassDrum.divider ?: return kit
        val crescentCount = bassDrum.zones.count { it.region == "crescent" }
        if (crescentCount <= 1) return kit

        val defaultSplits = when (crescentCount) {
            2 -> listOf(0.50f)
            3 -> listOf(0.333f, 0.667f)
            4 -> listOf(0.25f, 0.50f, 0.75f)
            else -> emptyList()
        }

        val splitsList = (div.splits?.takeIf { it.size == crescentCount - 1 } ?: defaultSplits).toMutableList()
        if (splitIndex !in splitsList.indices) return kit

        val minBound = if (splitIndex > 0) splitsList[splitIndex - 1] + 0.05f else 0.05f
        val maxBound = if (splitIndex < splitsList.size - 1) splitsList[splitIndex + 1] - 0.05f else 0.95f
        val clamped = newSplitPos.coerceIn(minBound, maxBound)

        splitsList[splitIndex] = clamped

        val updatedDiv = div.copy(splits = splitsList)
        val updatedDrums = kit.drums.map { drum ->
            if (drum.model == "bass") {
                drum.copy(divider = updatedDiv)
            } else drum
        }

        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah jumlah zona aktif pada kendang non-bass (1 sampai 4 zona) atau bass (1 sampai 5 zona).
     */
    fun setDrumZoneCount(drumId: String, count: Int): KitData {
        if (drumId == "bass") {
            return setBassZoneCount(count)
        }

        val targetCount = count.coerceIn(1, 4)
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val targetDrum = kit.drums.firstOrNull { it.id == drumId } ?: return kit

        val oldZoneMap = targetDrum.zones.associateBy { it.id }
        val oldZonesList = targetDrum.zones

        val newZones = mutableListOf<com.vrkendang.kitcore.model.ZoneData>()
        val newModel: String
        val newDivider: DividerData?

        if (targetCount == 1) {
            newModel = "single"
            val zoneId = "${drumId}-1"
            val oldZone = oldZoneMap[zoneId] ?: oldZonesList.firstOrNull()
            newZones.add(
                com.vrkendang.kitcore.model.ZoneData(
                    id = zoneId,
                    region = "full",
                    sound = oldZone?.sound,
                    gain = oldZone?.gain ?: 1.0f
                )
            )
            newDivider = targetDrum.divider
        } else {
            newModel = "split"
            for (i in 0 until targetCount) {
                val zoneId = "${drumId}-${i + 1}"
                val oldZone = oldZoneMap[zoneId] ?: oldZonesList.getOrNull(i)
                val region = if (targetCount == 2) {
                    if (i == 0) "lower" else "upper"
                } else {
                    "sector"
                }
                newZones.add(
                    com.vrkendang.kitcore.model.ZoneData(
                        id = zoneId,
                        region = region,
                        band = if (targetCount > 2) i else null,
                        sound = oldZone?.sound,
                        gain = oldZone?.gain ?: 1.0f
                    )
                )
            }
            newDivider = targetDrum.divider ?: DividerData(angle = 0f)
        }

        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == drumId) {
                drum.copy(
                    model = newModel,
                    divider = newDivider,
                    zones = newZones
                )
            } else drum
        }

        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengatur sudut rotasi garis pembatas sekat juring kendang (0.0 s.d. 360.0 derajat).
     */
    fun updateDrumAngle(drumId: String, angle: Float): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val targetDrum = kit.drums.firstOrNull { it.id == drumId } ?: return kit

        val clampedAngle = ((angle % 360f) + 360f) % 360f
        val curDiv = targetDrum.divider ?: DividerData()
        val updatedDiv = curDiv.copy(angle = clampedAngle)

        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == drumId) {
                drum.copy(divider = updatedDiv)
            } else drum
        }

        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Menambahkan kendang baru dengan nama bebas dan jumlah zona awal (1..4 zona).
     */
    fun addDrum(name: String, initialZoneCount: Int = 1): Pair<KitData, com.vrkendang.kitcore.model.DrumData> {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")

        var baseSlug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (baseSlug.isBlank()) baseSlug = "drum"
        if (baseSlug == "bass") baseSlug = "drum-baru"

        var cleanId = baseSlug
        var counter = 2
        val existingIds = kit.drums.map { it.id }.toSet()
        while (existingIds.contains(cleanId)) {
            cleanId = "$baseSlug-$counter"
            counter++
        }

        val targetCount = initialZoneCount.coerceIn(1, 4)
        val model = if (targetCount == 1) "single" else "split"
        val zones = if (targetCount == 1) {
            listOf(
                com.vrkendang.kitcore.model.ZoneData(
                    id = "$cleanId-1",
                    region = "full",
                    sound = null,
                    gain = 1.0f
                )
            )
        } else {
            (0 until targetCount).map { i ->
                com.vrkendang.kitcore.model.ZoneData(
                    id = "$cleanId-${i + 1}",
                    region = if (targetCount == 2) (if (i == 0) "lower" else "upper") else "sector",
                    band = if (targetCount > 2) i else null,
                    sound = null,
                    gain = 1.0f
                )
            }
        }

        val newDrum = com.vrkendang.kitcore.model.DrumData(
            id = cleanId,
            model = model,
            center = Point(0.50f, 0.50f),
            radius = Radius(0.16f, 0.16f),
            divider = if (targetCount > 1) DividerData(angle = 0f) else null,
            zones = zones
        )

        val updatedDrums = kit.drums + newDrum
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return Pair(updated, newDrum)
    }

    /**
     * Menghapus kendang dari kit (Bass diproteksi agar tidak bisa dihapus).
     */
    fun deleteDrum(drumId: String): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        if (drumId == "bass") {
            throw IllegalArgumentException("Kendang Bass tidak boleh dihapus")
        }

        val targetDrum = kit.drums.firstOrNull { it.id == drumId } ?: return kit
        if (targetDrum.model == "bass") {
            throw IllegalArgumentException("Kendang model Bass tidak boleh dihapus")
        }

        // Hapus file WAV zona kendang ini jika ada di folder kerja
        for (z in targetDrum.zones) {
            val wavFile = File(soundsDir, "${z.id}.wav")
            if (wavFile.exists()) {
                wavFile.delete()
            }
        }

        // Hapus file PNG gambar kendang jika ada di folder images/
        val imageFile = File(imagesDir, "$drumId.png")
        if (imageFile.exists()) {
            imageFile.delete()
        }
        val bodyImageFile = File(imagesDir, "$drumId-body.png")
        if (bodyImageFile.exists()) {
            bodyImageFile.delete()
        }

        val updatedDrums = kit.drums.filter { it.id != drumId }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah urutan array drums (dipertahankan pada level API sesi untuk kompatibilitas unit test).
     */
    fun moveDrumOrder(fromIndex: Int, toIndex: Int): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        if (fromIndex !in kit.drums.indices || toIndex !in kit.drums.indices) {
            return kit
        }

        val mutableList = kit.drums.toMutableList()
        val item = mutableList.removeAt(fromIndex)
        mutableList.add(toIndex, item)

        val updated = kit.copy(drums = mutableList)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Memasang file WAV ke zona tertentu (Task 11.3).
     */
    fun installZoneWav(
        zoneId: String,
        inputStream: InputStream,
        slotIndex: Int,
        engine: AudioEngineApi = AudioEngineApiImpl
    ): DevSoundInstaller.Result {
        val result = soundInstaller.installWav(zoneId, inputStream, devKitDir, slotIndex, engine)
        if (result is DevSoundInstaller.Result.Success) {
            workingKit = result.updatedKit
            hasUnsavedChanges = true
        }
        return result
    }

    /**
     * Mengosongkan suara zona (Task 11.3).
     */
    fun clearZoneSound(zoneId: String): DevSoundInstaller.Result {
        val result = soundInstaller.clearSound(zoneId, devKitDir)
        if (result is DevSoundInstaller.Result.Success) {
            workingKit = result.updatedKit
            hasUnsavedChanges = true
        }
        return result
    }

    /**
     * Memasang gambar PNG untuk kendang.
     */
    fun installDrumImage(drumId: String, inputStream: InputStream): DevImageInstaller.Result {
        val result = devImageInstaller.installPng(drumId, inputStream, devKitDir)
        if (result is DevImageInstaller.Result.Success) {
            workingKit = result.updatedKit
            hasUnsavedChanges = true
        }
        return result
    }

    /**
     * Mengosongkan gambar PNG kendang (kembali ke render bawaan Canvas).
     */
    fun clearDrumImage(drumId: String): DevImageInstaller.Result {
        val result = devImageInstaller.clearImage(drumId, devKitDir)
        if (result is DevImageInstaller.Result.Success) {
            workingKit = result.updatedKit
            hasUnsavedChanges = true
        }
        return result
    }

    /**
     * Mengubah ID kendang dan me-rename file gambar PNG jika ada.
     */
    fun renameDrumId(oldDrumId: String, newDrumId: String): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        if (oldDrumId == newDrumId) return kit
        if (oldDrumId == "bass" || newDrumId == "bass") {
            throw IllegalArgumentException("ID kendang Bass tidak boleh diubah")
        }
        val target = kit.drums.firstOrNull { it.id == oldDrumId }
            ?: throw IllegalArgumentException("Kendang '$oldDrumId' tidak ditemukan")
        if (kit.drums.any { it.id == newDrumId }) {
            throw IllegalArgumentException("ID '$newDrumId' sudah digunakan kendang lain")
        }

        // Pindahkan file gambar jika ada
        val oldImageFile = File(imagesDir, "$oldDrumId.png")
        val newImagePath = if (target.image != null) "images/$newDrumId.png" else null
        if (oldImageFile.exists()) {
            val newImageFile = File(imagesDir, "$newDrumId.png")
            oldImageFile.renameTo(newImageFile)
        }

        // Pindahkan file gambar body jika ada
        val oldBodyFile = File(imagesDir, "$oldDrumId-body.png")
        val newBody = target.body?.let { b ->
            if (oldBodyFile.exists()) {
                val newBodyFile = File(imagesDir, "$newDrumId-body.png")
                oldBodyFile.renameTo(newBodyFile)
            }
            b.copy(image = "images/$newDrumId-body.png")
        }

        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == oldDrumId) {
                drum.copy(id = newDrumId, image = newImagePath, body = newBody)
            } else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Memasang gambar PNG body untuk kendang.
     */
    fun installDrumBodyImage(drumId: String, inputStream: InputStream): DevImageInstaller.Result {
        val result = devImageInstaller.installBodyPng(drumId, inputStream, devKitDir)
        if (result is DevImageInstaller.Result.Success) {
            workingKit = result.updatedKit
            hasUnsavedChanges = true
        }
        return result
    }

    /**
     * Mengosongkan gambar PNG body kendang.
     */
    fun clearDrumBodyImage(drumId: String): DevImageInstaller.Result {
        val result = devImageInstaller.clearBodyImage(drumId, devKitDir)
        if (result is DevImageInstaller.Result.Success) {
            workingKit = result.updatedKit
            hasUnsavedChanges = true
        }
        return result
    }

    /**
     * Memperbarui parameter geometri body kendang.
     */
    fun updateDrumBody(
        drumId: String,
        width: Float,
        height: Float,
        offsetX: Float,
        offsetY: Float,
        rotation: Float
    ): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val target = kit.drums.firstOrNull { it.id == drumId } ?: return kit
        val existingBody = target.body ?: return kit

        val updatedBody = existingBody.copy(
            width = width.coerceIn(0.01f, 5.0f),
            height = height.coerceIn(0.01f, 5.0f),
            offsetX = offsetX.coerceIn(-3.0f, 3.0f),
            offsetY = offsetY.coerceIn(-3.0f, 3.0f),
            rotation = rotation.coerceIn(0.0f, 360.0f)
        )
        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == drumId) drum.copy(body = updatedBody) else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Menyelaraskan body tepat di bawah cincin kulit kendang (Auto-Dock).
     * Lebar body diselaraskan dengan diameter horizontal kulit (2 * rx), offset X = 0, offset Y = height / 2, rotasi = 0.
     */
    fun dockDrumBodyToSkin(drumId: String): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val target = kit.drums.firstOrNull { it.id == drumId } ?: return kit
        val existingBody = target.body ?: return kit

        val newW = (target.radius.rx * 2f).coerceIn(0.08f, 1.00f)
        val newH = existingBody.height.coerceIn(0.08f, 1.00f)
        val updatedBody = existingBody.copy(
            width = newW,
            height = newH,
            offsetX = 0f,
            offsetY = newH / 2f,
            rotation = 0f
        )
        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == drumId) drum.copy(body = updatedBody) else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengembalikan parameter body ke nilai awal standar yang rapat ke kulit.
     */
    fun resetDrumBody(drumId: String): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val target = kit.drums.firstOrNull { it.id == drumId } ?: return kit
        val existingBody = target.body ?: return kit

        val stdSize = (target.radius.rx * 2f).coerceIn(0.08f, 1.00f)
        val updatedBody = existingBody.copy(
            width = stdSize,
            height = stdSize,
            offsetX = 0f,
            offsetY = stdSize / 2f,
            rotation = 0f
        )
        val updatedDrums = kit.drums.map { drum ->
            if (drum.id == drumId) drum.copy(body = updatedBody) else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengekspor kit ke paket .vrkendang (Task 11.6).
     *
     * Alur:
     * 1. Naikkan nilai version tepat 1 tingkat.
     * 2. Susun paket di memory/cache via KitPackageWriter (hanya WAV dan PNG yang dirujuk, tanpa file yatim).
     * 3. Baca ulang dan validasi via KitPackageReader & KitValidator.
     * 4. Salin ke target OutputStream.
     * 5. Set hasUnsavedChanges = false.
     */
    fun saveToPackage(
        targetUri: Uri,
        contentResolver: ContentResolver,
        cacheDir: File
    ): KitData {
        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")

        // 1. Naikkan version tepat satu tingkat
        val incrementedKit = kit.copy(version = kit.version + 1)
        KitValidator.validateOrThrow(incrementedKit)

        // 2. Susun paket di cache
        val packageBytesOut = ByteArrayOutputStream()
        val wavProvider = { soundPath: String ->
            val wavFile = File(devKitDir, soundPath)
            if (wavFile.exists()) wavFile.readBytes() else null
        }
        val imageProvider = { imagePath: String ->
            val imageFile = File(devKitDir, imagePath)
            if (imageFile.exists()) imageFile.readBytes() else null
        }
        KitPackageWriter.writePackage(incrementedKit, wavProvider, packageBytesOut, imageProvider)

        val packageBytes = packageBytesOut.toByteArray()

        // 3. Baca ulang dan validasi
        val verified = KitPackageReader.read(ByteArrayInputStream(packageBytes))
        KitValidator.validateOrThrow(verified.kitData)

        // 4. Tulis ke target URI
        val outputStream = contentResolver.openOutputStream(targetUri, "wt")
            ?: throw IllegalStateException("Gagal membuka stream untuk URI: $targetUri")

        outputStream.use { it.write(packageBytes) }

        // Perbarui kit.json lokal dengan version baru
        writeKitJsonToDisk(incrementedKit)
        workingKit = incrementedKit
        sourceUri = targetUri
        hasUnsavedChanges = false
        return incrementedKit
    }

    private fun writeKitJsonToDisk(kit: KitData) {
        val targetFile = File(devKitDir, "kit.json")
        val tmpFile = File(devKitDir, "kit.json.tmp")
        val jsonText = json.encodeToString(KitData.serializer(), kit)
        tmpFile.writeText(jsonText)
        if (!tmpFile.renameTo(targetFile)) {
            targetFile.writeText(jsonText)
            tmpFile.delete()
        }
    }
}
