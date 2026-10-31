#include "Resampler.h"
#include <cmath>
#include <algorithm>

bool Resampler::resampleLinear(
    const int16_t* inputPcm,
    int32_t inputFrames,
    int32_t inputSampleRate,
    int32_t targetSampleRate,
    std::vector<float>& outputPcm) {
    if (inputPcm == nullptr || inputFrames <= 0 || inputSampleRate <= 0 || targetSampleRate <= 0) {
        outputPcm.clear();
        return false;
    }

    // Kasus 1: Sample rate input identik dengan target stream rate.
    // Lakukan konversi langsung dari int16 ke float ternormalisasi [-1.0f, 1.0f] tanpa interpolasi
    // untuk menghindari overhead dan menjaga akurasi bit-perfect.
    if (inputSampleRate == targetSampleRate) {
        outputPcm.resize(static_cast<size_t>(inputFrames));
        constexpr float kInv32768 = 1.0f / 32768.0f;
        for (int32_t i = 0; i < inputFrames; ++i) {
            outputPcm[static_cast<size_t>(i)] = static_cast<float>(inputPcm[i]) * kInv32768;
        }
        return true;
    }

    // Kasus 2: Sample rate berbeda (misal 44.1 kHz -> 48 kHz).
    // Hitung jumlah frame target berdasarkan rasio sample rate agar durasi dan pitch audio tetap sama.
    const double ratio = static_cast<double>(targetSampleRate) / static_cast<double>(inputSampleRate);
    const int64_t calculatedFrames = static_cast<int64_t>(std::round(static_cast<double>(inputFrames) * ratio));
    if (calculatedFrames <= 0) {
        outputPcm.clear();
        return false;
    }

    const size_t targetFrames = static_cast<size_t>(calculatedFrames);
    outputPcm.resize(targetFrames);

    // Langkah pembacaan pada buffer input untuk setiap 1 frame target output
    const double step = static_cast<double>(inputSampleRate) / static_cast<double>(targetSampleRate);
    constexpr float kInv32768 = 1.0f / 32768.0f;

    for (size_t i = 0; i < targetFrames; ++i) {
        const double srcPos = static_cast<double>(i) * step;
        const int32_t index0 = static_cast<int32_t>(srcPos);
        const float fraction = static_cast<float>(srcPos - static_cast<double>(index0));

        if (index0 >= inputFrames) {
            // Melewati batas akhir audio, isi dengan 0.0f
            outputPcm[i] = 0.0f;
            continue;
        }

        const int32_t index1 = index0 + 1;

        const float s0 = static_cast<float>(inputPcm[index0]) * kInv32768;
        const float s1 = (index1 < inputFrames) ? (static_cast<float>(inputPcm[index1]) * kInv32768) : s0;

        // Interpolasi linier: s0 + fraction * (s1 - s0)
        float interpolated = s0 + fraction * (s1 - s0);

        // Clamp defensif untuk memastikan amplitudo tidak pernah melebihi [-1.0f, 1.0f]
        interpolated = std::max(-1.0f, std::min(1.0f, interpolated));

        outputPcm[i] = interpolated;
    }

    return true;
}
