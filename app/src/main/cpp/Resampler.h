#ifndef VR_KENDANG_RESAMPLER_H
#define VR_KENDANG_RESAMPLER_H

#include <cstdint>
#include <vector>

/**
 * Resampler menyediakan fungsi resampling audio PCM menggunakan interpolasi linier.
 *
 * Sesuai Desain D4 & Spesifikasi Audio Engine:
 * 1. Menerima PCM 16-bit mono asli beserta sample rate aslinya (misal 44.1 kHz, 22.05 kHz).
 * 2. Mengonversi dan me-resample ke PCM float mono ternormalisasi [-1.0f, 1.0f]
 *    pada target sample rate native Oboe stream (misal 48 kHz).
 * 3. Mempertahankan pitch dan durasi audio asli secara akurat saat dimainkan di Fast Audio Path.
 * 4. Resampling dieksekusi di thread loader/kontrol (di luar thread audio callback real-time)
 *    sehingga tidak membebani pemutaran suara real-time.
 */
class Resampler {
public:
    /**
     * Me-resample buffer PCM 16-bit mono ke buffer float mono pada targetSampleRate
     * menggunakan interpolasi linier.
     *
     * @param inputPcm Pointer ke data PCM 16-bit mono asli
     * @param inputFrames Jumlah frame dalam inputPcm
     * @param inputSampleRate Sample rate input (dalam Hz, misal 44100)
     * @param targetSampleRate Sample rate target native Oboe stream (dalam Hz, misal 48000)
     * @param outputPcm Vektor keluaran yang akan diisi dengan PCM float hasil resampling
     * @return true jika proses resampling berhasil, false jika input tidak valid
     */
    static bool resampleLinear(
        const int16_t* inputPcm,
        int32_t inputFrames,
        int32_t inputSampleRate,
        int32_t targetSampleRate,
        std::vector<float>& outputPcm);
};

#endif // VR_KENDANG_RESAMPLER_H
