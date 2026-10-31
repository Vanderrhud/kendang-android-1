package com.vrkendang.audio

import android.util.Log
import com.vrkendang.kitcore.audio.WavParser
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Memuat file audio WAV ke mesin audio C++ melalui JNI.
 *
 * Tanggung jawab kelas ini:
 *   1. Mendelegasikan validasi header WAV ke [WavParser] (modul :kit-core).
 *   2. Membungkus byte PCM mentah ke dalam [ByteBuffer.allocateDirect] agar
 *      C++ dapat mengakses pointer buffer tanpa salinan JNI tambahan.
 *   3. Menyerahkan buffer PCM beserta metadata (frameCount, sampleRate) ke
 *      [AudioEngine.loadSound] yang diteruskan ke C++.
 *   4. Menangani kegagalan secara aman: jika load gagal, zona diperlakukan
 *      sebagai unassigned (tanpa suara) dan tidak ada crash.
 *
 * Pembagian tanggung jawab (Desain D4):
 *   - Kotlin: parsing WAV, validasi format, alokasi DirectByteBuffer.
 *   - C++  : kepemilikan salinan PCM, resampling ke native rate, playback.
 *
 * Kelas ini tidak menyimpan state — seluruh kepemilikan PCM ada di C++.
 */
class SoundLoader {

    private val tag = "SoundLoader"

    /**
     * Hasil pemuatan satu file WAV ke slot audio engine.
     */
    sealed class Result {
        /** Berhasil dimuat; zona aktif dan siap dibunyikan. */
        object Success : Result()

        /**
         * Gagal karena format WAV tidak didukung atau stream tidak dapat dibaca.
         * Zona diperlakukan sebagai unassigned: tidak ada suara, tidak ada crash.
         *
         * @param message Pesan galat deskriptif untuk ditampilkan di log developer.
         */
        data class Failure(val message: String) : Result()
    }

    /**
     * Memuat satu file WAV dari [inputStream] ke slot [slotIndex] di audio engine.
     *
     * Alur:
     *   1. WavParser membaca dan memvalidasi header RIFF/WAVE. Melempar
     *      [IllegalArgumentException] jika bukan PCM 16-bit mono.
     *   2. Byte PCM disalin ke DirectByteBuffer little-endian.
     *   3. Buffer diserahkan ke JNI [AudioEngine.loadSound]; C++ menyalin PCM
     *      ke memorinya sendiri sehingga buffer dapat di-GC setelah fungsi ini kembali.
     *
     * @param inputStream Stream data file WAV; ditutup oleh pemanggil.
     * @param slotIndex   Indeks slot target (0 hingga 31).
     * @param engine      AudioEngine object untuk JNI dispatch (dapat di-mock saat uji).
     * @return [Result.Success] atau [Result.Failure] dengan pesan galat yang jelas.
     */
    fun load(inputStream: InputStream, slotIndex: Int, engine: AudioEngineApi = AudioEngineApiImpl): Result {
        val wavPcm = try {
            WavParser.parse(inputStream)
        } catch (e: IllegalArgumentException) {
            // Format WAV tidak didukung: bukan PCM, bukan mono, bukan 16-bit, atau header rusak.
            // Catat galat dengan detail agar developer dapat memperbaiki kit.
            val msg = "Slot $slotIndex: WAV ditolak — ${e.message}"
            Log.e(tag, msg)
            return Result.Failure(msg)
        } catch (e: Exception) {
            val msg = "Slot $slotIndex: gagal membaca stream WAV — ${e.message}"
            Log.e(tag, msg)
            return Result.Failure(msg)
        }

        // Bungkus PCM ke DirectByteBuffer agar GetDirectBufferAddress di JNI bekerja.
        // Little-endian sesuai format PCM 16-bit signed standar WAV.
        val directBuffer: ByteBuffer = ByteBuffer
            .allocateDirect(wavPcm.pcmData.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        directBuffer.put(wavPcm.pcmData)
        // Tidak perlu flip() — C++ membaca melalui GetDirectBufferAddress yang selalu
        // merujuk ke awal buffer native, bukan posisi Java position/limit.

        val ok = engine.loadSound(
            pcmData    = directBuffer,
            frameCount = wavPcm.frameCount,
            sampleRate = wavPcm.sampleRate,
            slotIndex  = slotIndex
        )

        return if (ok) {
            Log.d(tag, "Slot $slotIndex: ${wavPcm.frameCount} frame @ ${wavPcm.sampleRate} Hz dimuat")
            Result.Success
        } else {
            // C++ menolak load (misal slotIndex di luar batas atau engine null).
            val msg = "Slot $slotIndex: C++ loadSound mengembalikan false"
            Log.e(tag, msg)
            Result.Failure(msg)
        }
    }
}

/**
 * Antarmuka tipis untuk AudioEngine agar [SoundLoader] dapat diuji tanpa JNI.
 *
 * Alasan interface terpisah (bukan langsung gunakan AudioEngine object):
 *   - AudioEngine.loadSound bergantung pada System.loadLibrary yang tidak tersedia
 *     di JVM unit test tanpa Android NDK.
 *   - Dengan interface ini, test dapat menyuntikkan implementasi palsu (fake)
 *     yang merekam panggilan tanpa menyentuh native library.
 */
interface AudioEngineApi {
    fun loadSound(
        pcmData: ByteBuffer,
        frameCount: Int,
        sampleRate: Int,
        slotIndex: Int
    ): Boolean

    /** Membebaskan seluruh sample yang dimuat kit sebelumnya. */
    fun releaseAll()

    /** Membuka stream audio; mengembalikan false bila gagal. */
    fun openStream(): Boolean

    /** Memulai stream audio; mengembalikan false bila gagal. */
    fun startStream(): Boolean

    /** Memicu pemutaran suara pada slot tertentu dengan gain tertentu. */
    fun triggerSound(slotIndex: Int, gain: Float): Boolean = false
}

/**
 * Menghubungkan [AudioEngineApi] ke singleton [AudioEngine] JNI.
 * Digunakan sebagai default di produksi.
 */
internal object AudioEngineApiImpl : AudioEngineApi {
    override fun loadSound(
        pcmData: ByteBuffer,
        frameCount: Int,
        sampleRate: Int,
        slotIndex: Int
    ) = AudioEngine.loadSound(pcmData, frameCount, sampleRate, slotIndex)

    override fun releaseAll() = AudioEngine.releaseAll()

    override fun openStream() = AudioEngine.openStream()

    override fun startStream() = AudioEngine.startStream()

    override fun triggerSound(slotIndex: Int, gain: Float) =
        AudioEngine.triggerSound(slotIndex, gain)
}
