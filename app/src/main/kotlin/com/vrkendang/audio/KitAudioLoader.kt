package com.vrkendang.audio

import android.util.Log
import com.vrkendang.kitcore.model.KitData
import java.io.InputStream

/**
 * Sumber pembacaan file WAV relatif terhadap akar sebuah kit.
 *
 * Kit bawaan membuka file dari `AssetManager`, sedangkan salinan kerja Mode
 * Developer membuka file dari `filesDir/dev-kit/`. Abstraksi ini menjaga
 * [KitAudioLoader] tetap dapat diuji JVM tanpa Android runtime.
 */
fun interface KitSoundSource {
    /**
     * @param soundPath Path relatif dari `kit.json`, misalnya `sounds/bass-1.wav`.
     * @throws Exception bila file tidak ada atau tidak dapat dibaca.
     */
    fun open(soundPath: String): InputStream
}

/**
 * Memuat seluruh WAV dari kit aktif ke RAM audio engine sebelum kit dimainkan.
 *
 * Sesuai Spesifikasi Audio Engine:
 * - Semua WAV dimuat saat kit diaktifkan agar ketukan tidak membaca disk.
 * - Kit dengan seluruh `sound == null` tetap aktif, stream tetap dibuka, dan
 *   sentuhan pada zona tersebut menjadi hening tanpa crash.
 * - Satu WAV yang gagal dimuat hanya membuat zona itu unassigned; kit lain
 *   tetap dimuat dan aplikasi tidak crash.
 *
 * Pemetaan zona ke slot audio engine dilakukan berdasarkan urutan drum lalu
 * zona di `kit.json`. Pemetaan ini harus dipakai juga oleh papan kendang saat
 * mengirim trigger agar suara yang berbunyi sesuai zona yang disentuh.
 */
class KitAudioLoader(
    private val soundLoader: SoundLoader = SoundLoader(),
    private val logSink: (String) -> Unit = { message -> Log.d(TAG, message) }
) {

    companion object {
        /** Batas slot suara di `SoundSlot.h` / `AudioEngine::kMaxSoundSlots`. */
        const val MAX_SOUND_SLOTS = 32

        private const val TAG = "KitAudioLoader"
    }

    /**
     * Hasil aktivasi kit.
     *
     * @param kit Kit yang diaktifkan.
     * @param slotByZoneId Pemetaan `zone.id` -> slot audio engine untuk seluruh
     *   zona yang masih berada dalam batas [MAX_SOUND_SLOTS]. Zona di luar batas
     *   tidak dipetakan sehingga trigger-nya diabaikan.
     * @param loadedSoundCount Jumlah WAV yang berhasil dimuat ke RAM.
     * @param failedSoundCount Jumlah WAV yang gagal dibuka/diurai/dimuat.
     * @param streamOpened True bila stream audio berhasil dibuka.
     * @param streamStarted True bila stream audio berhasil dimulai.
     */
    data class Activation(
        val kit: KitData,
        val slotByZoneId: Map<String, Int>,
        val loadedSoundCount: Int,
        val failedSoundCount: Int,
        val streamOpened: Boolean,
        val streamStarted: Boolean
    )

    /**
     * Mengaktifkan [kit]: membebaskan sample kit lama, memuat semua WAV ke RAM,
     * lalu membuka dan memulai stream audio.
     *
     * Urutan ini penting karena `AudioEngine.openStream()` melakukan resampling
     * ulang seluruh slot yang sudah dimuat ke native sample rate stream.
     */
    fun activate(
        kit: KitData,
        soundSource: KitSoundSource,
        engine: AudioEngineApi = AudioEngineApiImpl
    ): Activation {
        // Slot suara bersifat global di engine. Tanpa release, sample kit lama
        // dapat ikut berbunyi ketika kit diganti.
        engine.releaseAll()

        val slotByZoneId = linkedMapOf<String, Int>()
        var loadedSoundCount = 0
        var failedSoundCount = 0
        var nextSlot = 0

        for (drum in kit.drums) {
            for (zone in drum.zones) {
                if (nextSlot >= MAX_SOUND_SLOTS) {
                    logSink(
                        "Zona '${zone.id}' melewati batas $MAX_SOUND_SLOTS slot audio; " +
                            "zona diperlakukan tanpa suara"
                    )
                    continue
                }

                val slotIndex = nextSlot++
                slotByZoneId[zone.id] = slotIndex

                val soundPath = zone.sound ?: continue
                if (loadZoneSound(zone.id, soundPath, slotIndex, soundSource, engine)) {
                    loadedSoundCount++
                } else {
                    failedSoundCount++
                }
            }
        }

        if (loadedSoundCount == 0) {
            logSink("Kit '${kit.kitId}': tidak ada WAV yang dimuat (semua zona sound null atau gagal)")
        }

        val streamOpened = engine.openStream()
        if (!streamOpened) {
            logSink("Kit '${kit.kitId}': gagal membuka stream audio; kit tetap aktif tanpa suara")
        }
        val streamStarted = streamOpened && engine.startStream()
        if (streamOpened && !streamStarted) {
            logSink("Kit '${kit.kitId}': stream terbuka tetapi gagal dimulai")
        }

        return Activation(
            kit = kit,
            slotByZoneId = slotByZoneId,
            loadedSoundCount = loadedSoundCount,
            failedSoundCount = failedSoundCount,
            streamOpened = streamOpened,
            streamStarted = streamStarted
        )
    }

    /**
     * Memuat satu zona. Kegagalan apa pun dikembalikan sebagai `false` agar
     * pemanggil dapat memperlakukan zona sebagai unassigned tanpa exception.
     */
    private fun loadZoneSound(
        zoneId: String,
        soundPath: String,
        slotIndex: Int,
        soundSource: KitSoundSource,
        engine: AudioEngineApi
    ): Boolean {
        return try {
            soundSource.open(soundPath).use { stream ->
                when (val result = soundLoader.load(stream, slotIndex, engine)) {
                    is SoundLoader.Result.Success -> true
                    is SoundLoader.Result.Failure -> {
                        logSink("Zona '$zoneId': gagal memuat '$soundPath' — ${result.message}")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            // File hilang, izin dicabut, atau sumber aset rusak: jangan crash.
            logSink("Zona '$zoneId': gagal membuka '$soundPath' — ${e.message}")
            false
        }
    }
}
