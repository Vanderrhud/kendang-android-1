#ifndef VR_KENDANG_VOICE_H
#define VR_KENDANG_VOICE_H

#include <cstdint>
#include <cstddef>

/**
 * Representasi instans suara (voice) aktif dalam Voice Pool.
 *
 * Sesuai Desain D3 & Spesifikasi Audio Engine:
 * - Voice Pool mengalokasikan array tetap 32 voice saat inisialisasi di konstruktor.
 * - Tidak ada alokasi memori dinamis di dalam audio callback saat memutar voice.
 * - Voice menyimpan pointer ke PCM asli (16-bit mono) dan PCM hasil resampling (float mono).
 * - C++ sepenuhnya memiliki memori PCM yang dirujuk oleh pointer ini (disalin saat loadSound).
 */
struct Voice {
    // Pointer ke buffer PCM asli 16-bit mono di memori C++
    const int16_t* originalPcm{nullptr};
    int32_t originalFrameCount{0};
    int32_t originalSampleRate{0};

    // Pointer ke buffer PCM yang telah di-resample ke sample rate native stream Oboe
    const float* resampledPcm{nullptr};
    int32_t resampledFrameCount{0};

    // Posisi pembacaan playback saat ini dalam satuan frame audio
    int32_t playbackPosition{0};

    // Status aktif: true jika voice sedang berbunyi dan diproses di audio callback
    bool isActive{false};

    // Penguatan (gain) per zona kendang (rentang 0.0 - 2.0)
    float gain{1.0f};

    // ID slot suara yang diputar oleh voice ini (-1 jika tidak terkait slot tertentu)
    int32_t slotIndex{-1};

    // Penghitung usia (age) untuk algoritma Voice Stealing tertua (Tahap 5.6)
    uint64_t ageCounter{0};

    // Parameter micro-fade-out (1 ms) untuk mencegah bunyi pop saat Voice Stealing
    bool isFadingOut{false};
    float fadeGain{1.0f};
    float fadeStep{0.0f};

    // Parameter Voice Stealing crossfade 1ms (Tahap 5.6)
    const float* fadeOutPcm{nullptr};
    int32_t fadeOutPosition{0};
    int32_t fadeOutFrameCount{0};
    float fadeOutGain{0.0f};
    bool isStealCrossfading{false};
    int32_t stealFadeFramesRemaining{0};
    int32_t totalStealFadeFrames{0};

    /**
     * Mengembalikan voice ke status nonaktif dan membersihkan seluruh pointer PCM.
     */
    void reset() {
        originalPcm = nullptr;
        originalFrameCount = 0;
        originalSampleRate = 0;
        resampledPcm = nullptr;
        resampledFrameCount = 0;
        playbackPosition = 0;
        isActive = false;
        gain = 1.0f;
        slotIndex = -1;
        ageCounter = 0;
        isFadingOut = false;
        fadeGain = 1.0f;
        fadeStep = 0.0f;
        fadeOutPcm = nullptr;
        fadeOutPosition = 0;
        fadeOutFrameCount = 0;
        fadeOutGain = 0.0f;
        isStealCrossfading = false;
        stealFadeFramesRemaining = 0;
        totalStealFadeFrames = 0;
    }
};

#endif // VR_KENDANG_VOICE_H
