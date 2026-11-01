package com.vrkendang.kitauthoring

import com.vrkendang.audio.AudioEngineApi
import com.vrkendang.audio.AudioEngineApiImpl
import com.vrkendang.audio.SoundLoader
import com.vrkendang.kitcore.audio.WavParser
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.packageio.KitPackageReader
import com.vrkendang.kitcore.parser.KitJsonParser
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Pengelola pemasangan file suara WAV langsung dari penyimpanan perangkat ke salinan kerja kit.
 *
 * Sesuai Task 8.2 & Spesifikasi Kit Authoring:
 * - Memvalidasi format header WAV harus PCM 16-bit mono via [WavParser].
 * - Memvalidasi batas ukuran maksimum per file WAV (5 MB).
 * - Menolak file non-WAV, format salah, atau melebihi batas dengan pesan yang jelas.
 * - Menyalin file ke `filesDir/dev-kit/sounds/<zoneId>.wav`.
 * - Memperbarui kolom `sound` zona terkait di `kit.json`.
 * - Langsung memuat sampel audio ke AudioEngine agar zona seketika berbunyi saat diketuk.
 * - Pembatalan atau kegagalan tidak mengubah file lama maupun `kit.json`.
 */
class DevSoundInstaller(
    private val soundLoader: SoundLoader = SoundLoader(),
    private val json: Json = Json { prettyPrint = true }
) {

    sealed class Result {
        data class Success(
            val zoneId: String,
            val sampleRate: Int,
            val frameCount: Int,
            val updatedKit: KitData
        ) : Result()

        data class Failure(
            val message: String
        ) : Result()
    }

    /**
     * Memasang file WAV dari [inputStream] ke zona [zoneId].
     *
     * @param zoneId ID zona target (misal "bass-1").
     * @param inputStream Aliran data WAV dari file picker sistem.
     * @param devKitDir Direktori kerja kit (`filesDir/dev-kit`).
     * @param slotIndex Indeks slot target di AudioEngine.
     * @param engine JNI audio engine API.
     */
    fun installWav(
        zoneId: String,
        inputStream: InputStream,
        devKitDir: File,
        slotIndex: Int,
        engine: AudioEngineApi = AudioEngineApiImpl
    ): Result {
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            return Result.Failure("Gagal membaca file: ${e.message}")
        }

        // 1. Validasi batas ukuran file (maksimum 5 MB)
        if (bytes.size > KitPackageReader.MAX_WAV_SIZE) {
            val sizeMb = bytes.size / (1024 * 1024.0)
            return Result.Failure("Ukuran file melebihi batas 5 MB (terdeteksi: ${String.format("%.1f", sizeMb)} MB)")
        }

        // 2. Validasi format WAV dan konversi otomatis ke 16-bit mono via WavParser
        val wavPcm = try {
            WavParser.parse(bytes)
        } catch (e: IllegalArgumentException) {
            return Result.Failure("Format harus WAV PCM 16-bit mono (${e.message})")
        } catch (e: Exception) {
            return Result.Failure("Gagal mengurai file WAV: ${e.message}")
        }

        // 3. Pastikan direktori kerja dan sounds/ tersedia
        val soundsDir = File(devKitDir, "sounds")
        if (!soundsDir.exists() && !soundsDir.mkdirs()) {
            return Result.Failure("Gagal membuat direktori '${soundsDir.path}'")
        }

        val targetWavFile = File(soundsDir, "$zoneId.wav")
        try {
            targetWavFile.writeBytes(bytes)
        } catch (e: Exception) {
            return Result.Failure("Gagal menyimpan file audio: ${e.message}")
        }

        // 4. Perbarui kit.json di salinan kerja
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

        val relativeSoundPath = "sounds/$zoneId.wav"
        val updatedDrums = currentKit.drums.map { drum ->
            drum.copy(
                zones = drum.zones.map { zone ->
                    if (zone.id == zoneId) zone.copy(sound = relativeSoundPath) else zone
                }
            )
        }
        val updatedKit = currentKit.copy(drums = updatedDrums)

        try {
            val jsonString = json.encodeToString(KitData.serializer(), updatedKit)
            // Tulis atomik via file sementara
            val tmpFile = File(devKitDir, "kit.json.tmp")
            tmpFile.writeText(jsonString)
            if (!tmpFile.renameTo(kitJsonFile)) {
                kitJsonFile.writeText(jsonString)
                tmpFile.delete()
            }
        } catch (e: Throwable) {
            return Result.Failure("Gagal memperbarui kit.json: ${e.message}")
        }

        // 5. Muat sampel audio ke AudioEngine secara instan
        val loadResult = ByteArrayInputStream(bytes).use { stream ->
            soundLoader.load(stream, slotIndex, engine)
        }

        return when (loadResult) {
            is SoundLoader.Result.Success -> {
                Result.Success(
                    zoneId = zoneId,
                    sampleRate = wavPcm.sampleRate,
                    frameCount = wavPcm.frameCount,
                    updatedKit = updatedKit
                )
            }
            is SoundLoader.Result.Failure -> {
                Result.Failure("Gagal memuat sampel ke mesin audio: ${loadResult.message}")
            }
        }
    }

    /**
     * Mengosongkan suara pada zona tertentu (sound = null).
     */
    fun clearSound(
        zoneId: String,
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

        val updatedDrums = currentKit.drums.map { drum ->
            drum.copy(
                zones = drum.zones.map { zone ->
                    if (zone.id == zoneId) zone.copy(sound = null) else zone
                }
            )
        }
        val updatedKit = currentKit.copy(drums = updatedDrums)

        try {
            val jsonString = json.encodeToString(KitData.serializer(), updatedKit)
            val tmpFile = File(devKitDir, "kit.json.tmp")
            tmpFile.writeText(jsonString)
            if (!tmpFile.renameTo(kitJsonFile)) {
                kitJsonFile.writeText(jsonString)
                tmpFile.delete()
            }
        } catch (e: Throwable) {
            return Result.Failure("Gagal memperbarui kit.json: ${e.message}")
        }

        return Result.Success(
            zoneId = zoneId,
            sampleRate = 0,
            frameCount = 0,
            updatedKit = updatedKit
        )
    }
}
