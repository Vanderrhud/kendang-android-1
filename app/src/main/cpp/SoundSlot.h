#ifndef VR_KENDANG_SOUND_SLOT_H
#define VR_KENDANG_SOUND_SLOT_H

#include <cstdint>
#include <vector>

/**
 * Penyimpanan buffer PCM suara pada sisi C++.
 *
 * Sesuai Desain D4:
 * - C++ menyalin dan memiliki sepenuhnya buffer PCM asli 16-bit mono.
 * - originalPcm disimpan secara permanen bersama originalSampleRate agar
 *   bisa di-resample ulang kapan saja stream audio dibuka kembali dengan
 *   sample rate native baru (misalnya saat headphone dicolok/dicabut).
 * - resampledPcm menyimpan hasil resampling float mono pada native stream rate.
 */
struct SoundSlot {
    // Data PCM asli (16-bit mono) yang disalin dari Kotlin
    std::vector<int16_t> originalPcm;
    int32_t originalFrameCount{0};
    int32_t originalSampleRate{0};

    // Data PCM hasil resampling (float mono) pada target native stream sample rate
    std::vector<float> resampledPcm;
    int32_t resampledFrameCount{0};

    // Penanda apakah slot ini telah dipasangi file suara (assigned)
    bool isAssigned{false};

    void reset() {
        originalPcm.clear();
        originalFrameCount = 0;
        originalSampleRate = 0;
        resampledPcm.clear();
        resampledFrameCount = 0;
        isAssigned = false;
    }
};

#endif // VR_KENDANG_SOUND_SLOT_H
