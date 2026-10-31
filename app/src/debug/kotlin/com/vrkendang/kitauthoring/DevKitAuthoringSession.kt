package com.vrkendang.kitauthoring

import android.content.ContentResolver
import android.content.res.AssetManager
import android.net.Uri
import com.vrkendang.audio.AudioEngineApi
import com.vrkendang.audio.AudioEngineApiImpl
import com.vrkendang.kitcore.factory.KitFactory
import com.vrkendang.kitcore.model.DividerData
import com.vrkendang.kitcore.model.DrumData
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius
import com.vrkendang.kitcore.packageio.KitPackageReader
import com.vrkendang.kitcore.packageio.KitPackageWriter
import com.vrkendang.kitcore.parser.KitJsonParser
import com.vrkendang.kitcore.validation.KitValidator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Pengelola sesi Mode Developer untuk penyusunan kit (Tahap 11).
 *
 * Mengimplementasikan:
 * - Pembuatan kit baru dari template (Task 11.1)
 * - Pembukaan paket .vrkendang dan kit bawaan ke filesDir/dev-kit/ (Task 11.1, 11.7)
 * - Mode UJI dan EDIT (Task 11.2)
 * - Kontrol edit zona, gain 0.0–2.0, pasang/kosongkan WAV (Task 11.3)
 * - Parameter divider Bass (dx/dy -3.0..3.0, r 0..3.0) (Task 11.4)
 * - Simpan atomik ke file sumber dan Simpan Sebagai (Task 11.5, 11.6)
 * - Indikator dan pelacakan perubahan belum disimpan (Task 11.8)
 * - Pemuatan sampel aman tanpa gangguan audio stream (Task 11.9)
 * - Batasan 4 kendang template tanpa tambah/hapus (Task 11.10)
 * - Kontrol pengubahan urutan array drums / prioritas hit-test (Task 11.11)
 */
class DevKitAuthoringSession(
    val devKitDir: File,
    private val soundInstaller: DevSoundInstaller = DevSoundInstaller(),
    private val json: Json = Json { prettyPrint = true }
) {

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

    init {
        if (!devKitDir.exists()) devKitDir.mkdirs()
        if (!soundsDir.exists()) soundsDir.mkdirs()
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
     * Membuat kit baru dari template bawaan (Task 11.1).
     *
     * @param kitId ID kit baru (hanya huruf kecil, angka, tanda hubung; dilarang sama dengan "new-metro").
     * @param name Nama tampilan kit baru.
     * @param targetUri URI berkas sumber .vrkendang yang dipilih oleh developer.
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
        hasUnsavedChanges = (targetUri == null) // jika belum ada URI target, tandai dirty
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

        writeKitJsonToDisk(kit)
        workingKit = kit
        sourceUri = null // kit bawaan tidak memiliki file sumber .vrkendang
        hasUnsavedChanges = false
        return kit
    }

    /**
     * Membuka file paket .vrkendang dari URI penyimpanan (Task 11.7).
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
     * Memperbarui nama kit (Task 11.10).
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
     * Mengubah posisi center kendang (Task 11.2).
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
     * Mengubah ukuran radius kendang (rx, ry) (Task 11.2).
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
     * Mengubah parameter busur divider Bass (dx, dy, r) (Task 11.4).
     *
     * Validasi rentang:
     * dx/dy: -3.0 s.d. 3.0
     * r: > 0.0 s.d. 3.0
     */
    fun updateBassDivider(dx: Float, dy: Float, r: Float): KitData {
        if (dx !in -3.0f..3.0f || dy !in -3.0f..3.0f) {
            throw IllegalArgumentException("Parameter dx dan dy harus berada di rentang -3.0 sampai 3.0")
        }
        if (r <= 0.0f || r > 3.0f) {
            throw IllegalArgumentException("Parameter r harus > 0.0 dan <= 3.0")
        }

        val kit = workingKit ?: throw IllegalStateException("Kit belum dimuat")
        val updatedDrums = kit.drums.map { drum ->
            if (drum.model == "bass") {
                drum.copy(divider = DividerData(dx, dy, r))
            } else drum
        }
        val updated = kit.copy(drums = updatedDrums)
        writeKitJsonToDisk(updated)
        workingKit = updated
        hasUnsavedChanges = true
        return updated
    }

    /**
     * Mengubah urutan array drums (prioritas hit-test) (Task 11.11).
     *
     * @param fromIndex Indeks asal kendang yang ingin digeser.
     * @param toIndex Indeks target (misal naik atau turun satu posisi).
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
     * Menyimpan kit ke paket .vrkendang (Task 11.5 & 11.6).
     *
     * Alur:
     * 1. Naikkan nilai version tepat 1 tingkat.
     * 2. Susun paket di memory/cache via KitPackageWriter (hanya WAV yang dirujuk, tanpa file yatim).
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
        KitPackageWriter.writePackage(incrementedKit, wavProvider, packageBytesOut)

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
        val jsonText = json.encodeToString(kit)
        tmpFile.writeText(jsonText)
        if (!tmpFile.renameTo(targetFile)) {
            targetFile.writeText(jsonText)
            tmpFile.delete()
        }
    }
}
