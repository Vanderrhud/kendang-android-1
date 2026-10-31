package com.vrkendang.audio

import java.nio.ByteBuffer

/**
 * JNI bridge ke mesin audio native C++ Oboe (libkendang_audio.so).
 *
 * Sesuai Desain D3, D4, & Spesifikasi Audio Engine:
 * - Mengelola stream audio rendah-latensi Oboe.
 * - Memuat buffer PCM (DirectByteBuffer) mono 16-bit ke memori C++.
 * - Memicu suara via antrean lock-free SPSC ring buffer.
 * - Menangani unassigned zones dan kegagalan load secara aman (graceful silence, zero crash).
 */
object AudioEngine {
    init {
        System.loadLibrary("kendang_audio")
    }

    /**
     * Memuat data PCM 16-bit mono ke memori C++ pada slot tertentu.
     * C++ menyalin data PCM ke memorinya sendiri selama pemanggilan ini,
     * sehingga Kotlin bebas melepas buffer setelah pemanggilan selesai.
     *
     * @param pcmData DirectByteBuffer berisi data PCM 16-bit mono
     * @param frameCount Jumlah frame dalam buffer PCM
     * @param sampleRate Sample rate asli WAV (misal 44100 atau 48000 Hz)
     * @param slotIndex Indeks slot target (0 hingga 31)
     * @return true jika berhasil dimuat, false jika gagal (zona diperlakukan sebagai unassigned)
     */
    external fun loadSound(
        pcmData: ByteBuffer,
        frameCount: Int,
        sampleRate: Int,
        slotIndex: Int
    ): Boolean

    /**
     * Memicu suara pada slot tertentu dengan penguatan gain tertentu.
     * Event dimasukkan ke SPSC ring buffer lock-free di C++ tanpa memblokir thread UI.
     *
     * @param slotIndex Indeks slot suara
     * @param gain Nilai gain zona (rentang 0.0 hingga 2.0)
     * @return true jika event berhasil masuk antrean, false jika penuh
     */
    external fun triggerSound(slotIndex: Int, gain: Float): Boolean

    /**
     * Membebaskan memori sampel PCM pada slot tertentu.
     */
    external fun unloadSound(slotIndex: Int)

    /**
     * Membebaskan seluruh slot sampel PCM di memori C++.
     */
    external fun releaseAll()

    /**
     * Membuka stream audio Oboe.
     */
    external fun openStream(): Boolean

    /**
     * Memulai pemutaran audio stream.
     */
    external fun startStream(): Boolean

    /**
     * Menjeda (pause) audio stream saat aplikasi masuk ke background.
     * Menghentikan semua voice aktif agar tidak ada suara menggantung saat resume.
     */
    external fun pauseStream()

    /**
     * Melanjutkan (resume) audio stream saat aplikasi kembali ke foreground.
     */
    external fun resumeStream()

    /**
     * Menghentikan audio stream.
     */
    external fun stopStream()

    /**
     * Menutup dan melepaskan stream audio native.
     */
    external fun closeStream()

    /**
     * Mengembalikan native sample rate aktual yang digunakan hardware (dalam Hz).
     */
    external fun getSampleRate(): Int
}
