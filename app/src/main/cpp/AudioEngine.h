#ifndef VR_KENDANG_AUDIO_ENGINE_H
#define VR_KENDANG_AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <array>
#include <vector>
#include <cstdint>
#include "Voice.h"
#include "SoundSlot.h"
#include "Resampler.h"
#include "LockFreeQueue.h"

/**
 * AudioEngine mengelola stream audio Oboe berperforma rendah-latensi
 * untuk pemutaran instrumen kendang.
 *
 * Invariant & Batasan:
 * 1. Stream dibuka tanpa menetapkan sample rate tetap agar Oboe bernegosiasi
 *    langsung dengan hardware dan menghasilkan native sample rate perangkat.
 * 2. Native sample rate dibaca setelah stream terbuka dan digunakan sebagai
 *    target resampling PCM asli dari file WAV.
 * 3. Menggunakan PerformanceMode::LowLatency dan SharingMode::Exclusive (dengan
 *    fallback otomatis ke SharingMode::Shared bila hardware/OS menolak Exclusive).
 * 4. Pada Android 8.0 (API 26), Oboe mendeteksi ketiadaan dukungan AAudio yang
 *    stabil dan otomatis beralih menggunakan backend OpenSL ES.
 * 5. Audio callback (onAudioReady) berada pada thread audio real-time prioritas
 *    tinggi: sama sekali tidak boleh melakukan alokasi memori dinamis (malloc/new),
 *    tidak boleh memanggil JNI, dan tidak boleh menggunakan mutex/lock pemblokir.
 */
class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    virtual ~AudioEngine();

    // Mencegah penyalinan (copy/assignment) untuk menjaga kepemilikan unik resource stream
    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    /**
     * Membuka stream audio Oboe jika belum terbuka.
     * Mengembalikan true jika stream berhasil dibuka atau sudah terbuka sebelumnya.
     */
    bool openStream();

    /**
     * Memulai pemutaran audio stream.
     * Jika stream belum terbuka, method ini akan membukanya terlebih dahulu.
     */
    bool start();

    /**
     * Menghentikan sementara pemutaran audio stream tanpa menutup resource stream.
     */
    void stop();

    /**
     * Menghentikan dan menutup stream audio serta membebaskan resource native.
     */
    void close();

    /**
     * Mengembalikan native sample rate yang diberikan oleh stream aktif (dalam Hz, misal 48000).
     * Bernilai 0 jika stream belum dibuka.
     */
    int32_t getSampleRate() const;

    /**
     * Mengembalikan jumlah channel output stream (misal 2 untuk stereo).
     */
    int32_t getChannelCount() const;

    /**
     * Memeriksa apakah stream audio saat ini sedang berjalan aktif.
     */
    bool isStreamRunning() const;

    // --- Voice Pool (Tahap 5.2) ---
    // Kapasitas polyphony maksimum 32 suara simultan sesuai Desain D3 & Spesifikasi
    static constexpr size_t kMaxVoices = 32;

    /**
     * Mengembalikan referensi konstan ke Voice Pool (untuk inspeksi/pengujian).
     */
    const std::array<Voice, kMaxVoices>& getVoicePool() const;

    /**
     * Menghitung jumlah voice yang saat ini sedang aktif berbunyi.
     */
    size_t getActiveVoiceCount() const;

    /**
     * Menghentikan dan mereset seluruh voice dalam Voice Pool ke status nonaktif.
     */
    void resetVoicePool();

    // --- Sound Slots & Resampling (Tahap 5.3) ---
    static constexpr size_t kMaxSoundSlots = 32;

    /**
     * Menyalin data PCM 16-bit mono ke memori C++ dan me-resample ke target sample rate stream.
     * C++ sepenuhnya memiliki salinan buffer sehingga Kotlin bebas melepas buffernya.
     *
     * @param pcmData Pointer ke data buffer PCM 16-bit mono
     * @param frameCount Jumlah frame PCM dalam buffer
     * @param sampleRate Sample rate asli buffer PCM (misal 44100 Hz)
     * @param slotIndex Indeks slot target (0 hingga kMaxSoundSlots - 1)
     * @return true jika berhasil dimuat dan di-resample, false jika gagal
     */
    bool loadSound(
        const int16_t* pcmData,
        int32_t frameCount,
        int32_t sampleRate,
        int32_t slotIndex);

    /**
     * Menghapus sampel dan membebaskan memori pada slot tertentu.
     */
    void unloadSound(int32_t slotIndex);

    /**
     * Membebaskan seluruh slot sampel suara di memori C++.
     */
    void releaseAllSounds();

    /**
     * Me-resample ulang seluruh slot yang memiliki PCM asli ke targetSampleRate baru.
     * Dipanggil saat stream dibuka atau dibuka kembali (reconnect) agar pitch tetap benar.
     */
    void resampleAllSlots(int32_t targetSampleRate);

    /**
     * Mengembalikan pointer konstan ke slot suara untuk inspeksi/pengujian.
     */
    const SoundSlot* getSoundSlot(int32_t slotIndex) const;

    /**
     * Memasukkan event trigger suara ke lock-free SPSC ring buffer (Tahap 5.4/5.5).
     * Dipanggil dari thread UI / kontrol tanpa mutex dan tanpa memblokir audio callback.
     *
     * @param slotIndex Indeks slot suara yang ingin dibunyikan
     * @param gain Penguatan volume zona (rentang 0.0 hingga 2.0)
     * @return true jika berhasil masuk ke antrean, false jika antrean penuh
     */
    bool triggerSound(int32_t slotIndex, float gain);

    /**
     * Master limiter soft-clip menggunakan kurva saturasi analog berbasis tanh.
     * Mencegah hard clipping secara mulus saat banyak suara menumpuk dengan gain tinggi.
     */
    static float softClip(float x);

    // --- oboe::AudioStreamDataCallback ---

    /**
     * Callback audio real-time dari Oboe saat buffer output membutuhkan frame audio baru.
     *
     * Invariant Keamanan Real-Time:
     * - Dilarang malloc / new / free / delete.
     * - Dilarang mutex / locking / condition_variable.
     * - Dilarang memanggil method JNI.
     */
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override;

    // --- oboe::AudioStreamErrorCallback ---

    /**
     * Dipanggil oleh Oboe sebelum stream ditutup karena terjadi error/disconnect.
     */
    void onErrorBeforeClose(
        oboe::AudioStream *audioStream,
        oboe::Result error) override;

    /**
     * Dipanggil oleh Oboe setelah stream ditutup karena terjadi error/disconnect.
     */
    void onErrorAfterClose(
        oboe::AudioStream *audioStream,
        oboe::Result error) override;

private:
    /**
     * Membangun konfigurasi AudioStreamBuilder dan membuka stream.
     * Wajib dipanggil di bawah perlindungan mEngineMutex pada thread kontrol.
     */
    oboe::Result openStreamInternal();

    // Mutex kontrol untuk siklus hidup stream (open, start, stop, close).
    // Mutex ini HANYA dipakai di thread kontrol/UI dan TIDAK PERNAH dipakai di callback onAudioReady.
    mutable std::mutex mEngineMutex;

    std::shared_ptr<oboe::AudioStream> mStream;
    std::atomic<bool> mIsClosing{false};
    int32_t mSampleRate{0};
    int32_t mChannelCount{2};

    // Voice Pool: Array statis 32 elemen yang dialokasikan di konstruktor
    // Menjamin callback audio real-time tidak pernah memanggil malloc/new
    std::array<Voice, kMaxVoices> mVoicePool{};

    void resetVoicePoolInternal();

    // Slot penyimpanan sampel suara (maksimum 32 slot)
    std::array<SoundSlot, kMaxSoundSlots> mSoundSlots{};

    bool resampleSlotInternal(size_t slotIndex, int32_t targetSampleRate);

    // Antrean trigger event lock-free SPSC (kapasitas 128 event)
    static constexpr size_t kTriggerQueueCapacity = 128;
    SpscRingBuffer<TriggerEvent, kTriggerQueueCapacity> mTriggerQueue;

    // Sequence counter untuk menentukan usia voice (voice stealing tertua)
    uint64_t mAgeSequence{0};

    /**
     * Mengambil slot voice untuk membunyikan suara baru (Tahap 5.6):
     * - Mendukung hingga 32 suara simultan tanpa saling memotong.
     * - Bila seluruh 32 voice aktif, mengambil alih slot tertua (Voice Stealing)
     *   dengan micro-fade-out 1ms untuk mencegah bunyi pop.
     */
    int32_t findSlot();

    int32_t findAvailableVoiceIndex() { return findSlot(); }
};

#endif // VR_KENDANG_AUDIO_ENGINE_H
