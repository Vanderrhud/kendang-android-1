/**
 * native-lib.cpp
 *
 * JNI bridge antara Kotlin AudioEngine object dan mesin audio C++ Oboe.
 * Semua fungsi di sini dipanggil dari thread Kotlin (UI/Coroutine),
 * bukan dari audio callback — sehingga boleh menggunakan mutex dan logging.
 *
 * Konvensi penamaan JNI:
 *   Java_com_vrkendang_audio_AudioEngine_<methodName>
 * sesuai dengan package com.vrkendang.audio.AudioEngine di Kotlin.
 *
 * Lifecycle singleton gEngine:
 *   - Dibuat saat JNI_OnLoad (statis, seumur proses).
 *   - Seluruh JNI function memeriksa gEngine != nullptr sebelum dispatch
 *     agar aman jika dipanggil sebelum library termuat sepenuhnya.
 */

#include <jni.h>
#include <android/log.h>
#include "AudioEngine.h"

#define LOG_TAG "KendangAudio"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Singleton mesin audio statis seumur proses.
// Menggunakan function-local static agar bebas alokasi heap di JNI_OnLoad dan selalu terinisialisasi aman.
static AudioEngine& getEngine() {
    static AudioEngine instance;
    return instance;
}

static AudioEngine* gEngine = &getEngine();

// ---------------------------------------------------------------------------
// Lifecycle library
// ---------------------------------------------------------------------------

/**
 * Dipanggil Android runtime saat libkendang_audio.so pertama kali dimuat.
 */
extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* /*vm*/, void* /*reserved*/) {
    gEngine = &getEngine();
    return JNI_VERSION_1_6;
}

/**
 * Dipanggil Android runtime saat library di-unload.
 */
extern "C" JNIEXPORT void JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
    if (gEngine != nullptr) {
        gEngine->close();
        gEngine = nullptr;
    }
}

// ---------------------------------------------------------------------------
// Stream lifecycle — dipanggil dari Kotlin AudioEngine object
// ---------------------------------------------------------------------------

/**
 * Membuka stream audio Oboe dan me-resample seluruh slot yang sudah dimuat
 * ke native sample rate perangkat.
 *
 * @return JNI_TRUE jika berhasil, JNI_FALSE jika gEngine null atau gagal buka stream.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_vrkendang_audio_AudioEngine_openStream(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gEngine == nullptr) {
        LOGE("openStream: gEngine null");
        return JNI_FALSE;
    }
    return gEngine->openStream() ? JNI_TRUE : JNI_FALSE;
}

/**
 * Memulai pemutaran audio stream.
 *
 * @return JNI_TRUE jika berhasil memulai, JNI_FALSE jika gagal.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_vrkendang_audio_AudioEngine_startStream(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gEngine == nullptr) {
        LOGE("startStream: gEngine null");
        return JNI_FALSE;
    }
    return gEngine->start() ? JNI_TRUE : JNI_FALSE;
}

/**
 * Menjeda stream saat aplikasi masuk background.
 * Menghentikan seluruh voice aktif agar tidak ada suara menggantung saat resume.
 * C++ stop() sudah memanggil resetVoicePoolInternal() secara internal.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_vrkendang_audio_AudioEngine_pauseStream(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gEngine == nullptr) {
        LOGE("pauseStream: gEngine null");
        return;
    }
    gEngine->stop();
}

/**
 * Melanjutkan stream saat aplikasi kembali ke foreground.
 * Menggunakan start() yang sudah memastikan stream terbuka sebelum dimulai.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_vrkendang_audio_AudioEngine_resumeStream(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gEngine == nullptr) {
        LOGE("resumeStream: gEngine null");
        return;
    }
    gEngine->start();
}

/**
 * Menghentikan stream tanpa menutup resource-nya.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_vrkendang_audio_AudioEngine_stopStream(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gEngine == nullptr) {
        LOGE("stopStream: gEngine null");
        return;
    }
    gEngine->stop();
}

/**
 * Menutup dan membebaskan resource stream audio native.
 * Sampel PCM yang sudah dimuat tetap ada di slot dan tidak dihapus.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_vrkendang_audio_AudioEngine_closeStream(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gEngine == nullptr) {
        LOGE("closeStream: gEngine null");
        return;
    }
    gEngine->close();
}

/**
 * Mengembalikan native sample rate aktual yang dinegosiasikan Oboe dengan hardware.
 *
 * @return Sample rate dalam Hz (misal 48000), atau 0 jika stream belum terbuka.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_vrkendang_audio_AudioEngine_getSampleRate(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(gEngine->getSampleRate());
}

// ---------------------------------------------------------------------------
// Sound management
// ---------------------------------------------------------------------------

/**
 * Memuat data PCM 16-bit mono dari DirectByteBuffer ke memori C++ pada slot tertentu.
 *
 * Kotlin harus memberikan DirectByteBuffer agar GetDirectBufferAddress dapat bekerja.
 * C++ menyalin isi buffer ke memorinya sendiri selama pemanggilan ini,
 * sehingga Kotlin boleh melepas buffer setelah fungsi ini kembali.
 *
 * Alasan menggunakan DirectByteBuffer (bukan ByteArray):
 *   - Nol-kopi akses pointer PCM tanpa pin/copy JNI yang mahal.
 *   - SoundLoader sudah mengalokasikan DirectByteBuffer via ByteBuffer.allocateDirect().
 *
 * @param pcmData    DirectByteBuffer berisi data PCM 16-bit mono
 * @param frameCount Jumlah frame PCM dalam buffer
 * @param sampleRate Sample rate asli WAV (misal 44100 atau 48000 Hz)
 * @param slotIndex  Indeks slot target (0 hingga 31)
 * @return JNI_TRUE jika berhasil, JNI_FALSE jika gagal (slot diperlakukan unassigned)
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_vrkendang_audio_AudioEngine_loadSound(
        JNIEnv* env,
        jobject /*thiz*/,
        jobject pcmData,
        jint frameCount,
        jint sampleRate,
        jint slotIndex) {

    if (gEngine == nullptr) {
        LOGE("loadSound: gEngine null");
        return JNI_FALSE;
    }

    if (pcmData == nullptr) {
        LOGE("loadSound: pcmData null untuk slot %d", slotIndex);
        return JNI_FALSE;
    }

    // GetDirectBufferAddress mengembalikan nullptr jika bukan DirectByteBuffer
    const auto* data = static_cast<const int16_t*>(env->GetDirectBufferAddress(pcmData));
    if (data == nullptr) {
        LOGE("loadSound: GetDirectBufferAddress gagal untuk slot %d — pastikan menggunakan ByteBuffer.allocateDirect()", slotIndex);
        return JNI_FALSE;
    }

    bool ok = gEngine->loadSound(data, frameCount, sampleRate, slotIndex);
    if (!ok) {
        LOGE("loadSound: C++ loadSound gagal untuk slot %d", slotIndex);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

/**
 * Memicu suara pada slot tertentu dengan gain yang diberikan.
 * Event dimasukkan ke SPSC ring buffer lock-free di C++ tanpa memblokir thread UI.
 *
 * @param slotIndex Indeks slot suara yang ingin dibunyikan
 * @param gain      Nilai gain zona (rentang 0.0 hingga 2.0)
 * @return JNI_TRUE jika event masuk antrean, JNI_FALSE jika antrean penuh atau engine null
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_vrkendang_audio_AudioEngine_triggerSound(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jint slotIndex,
        jfloat gain) {

    if (gEngine == nullptr) {
        return JNI_FALSE;
    }
    return gEngine->triggerSound(slotIndex, gain) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Membebaskan memori sampel PCM pada slot tertentu.
 * Slot yang dibebaskan akan menghasilkan silence jika masih di-trigger.
 *
 * @param slotIndex Indeks slot yang akan dikosongkan
 */
extern "C" JNIEXPORT void JNICALL
Java_com_vrkendang_audio_AudioEngine_unloadSound(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jint slotIndex) {

    if (gEngine == nullptr) {
        LOGE("unloadSound: gEngine null");
        return;
    }
    gEngine->unloadSound(slotIndex);
}

/**
 * Membebaskan seluruh slot sampel PCM di memori C++.
 * Dipanggil saat kit diganti atau saat Activity dihancurkan.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_vrkendang_audio_AudioEngine_releaseAll(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gEngine == nullptr) {
        LOGE("releaseAll: gEngine null");
        return;
    }
    gEngine->releaseAllSounds();
}
