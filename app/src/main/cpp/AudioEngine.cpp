#include "AudioEngine.h"
#include <android/log.h>
#include <cstring>
#include <cmath>

#define LOG_TAG "VRKendangAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    // Alokasikan & inisialisasi seluruh 32 elemen Voice Pool pada saat konstruksi
    // Memastikan memori teralokasi di awal tanpa malloc/new di audio callback (Tahap 5.2)
    for (size_t i = 0; i < kMaxVoices; ++i) {
        mVoicePool[i].reset();
    }
    LOGI("AudioEngine instance dibuat dengan Voice Pool 32 slot teralokasi");
}

AudioEngine::~AudioEngine() {
    close();
    LOGI("AudioEngine instance dihancurkan");
}

oboe::Result AudioEngine::openStreamInternal() {
    if (mStream) {
        LOGI("Oboe stream sudah terbuka, mengabaikan permintaan openStream");
        return oboe::Result::OK;
    }

    oboe::AudioStreamBuilder builder;

    // Arah output suara instrumen
    builder.setDirection(oboe::Direction::Output);

    // Fast Audio Path: Meminta latensi serendah mungkin
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);

    // Exclusive mode memberikan akses langsung ke hardware buffer / MMAP bila didukung
    builder.setSharingMode(oboe::SharingMode::Exclusive);

    // Format float memberikan dynamic range tinggi untuk pencampuran (mixing) multi-voice
    builder.setFormat(oboe::AudioFormat::Float);

    // Stereo channel untuk kecocokan optimal dengan output speaker/headphone
    builder.setChannelCount(oboe::ChannelCount::Stereo);

    // KETENTUAN TUGAS 5.1 & DESAIN D4:
    // Tidak menetapkan sample rate tetap (dibiarkan kUnspecified bawaan builder).
    // Oboe akan meminta native sample rate perangkat agar tidak terjadi resampling ganda di sistem Android.
    // Native rate ini kemudian dibaca dan menjadi target resampling PCM dari file WAV.

    // KETENTUAN DESAIN D2 & SPESIFIKASI:
    // AudioApi dibiarkan Unspecified.
    // Pada Android 8.0 (API 26), Oboe mendeteksi ketiadaan dukungan AAudio yang stabil
    // dan secara otomatis menggunakan OpenSL ES.
    // Pada Android 8.1+ (API 27+), Oboe otomatis memilih AAudio.
    builder.setAudioApi(oboe::AudioApi::Unspecified);

    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    // Upaya 1: Membuka dengan SharingMode::Exclusive untuk performa fast audio path terbaik
    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        // Fallback defensif: Jika perangkat menolak mode Exclusive (misalnya keterbatasan hardware/vendor),
        // gunakan SharingMode::Shared agar stream tetap dapat menyala tanpa crash.
        LOGW("Gagal membuka stream dalam mode Exclusive (%s). Mencoba fallback ke Shared mode.",
             oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(mStream);
    }

    if (result != oboe::Result::OK) {
        LOGE("Gagal total membuka Oboe stream: %s", oboe::convertToText(result));
        mStream.reset();
        return result;
    }

    // Membaca sample rate native dan channel count aktual yang dinegosiasikan oleh hardware
    mSampleRate = mStream->getSampleRate();
    mChannelCount = mStream->getChannelCount();

    // KETENTUAN TAHAP 5.3 & DESAIN D4:
    // Saat stream dibuka atau dibuka ulang (reconnect), buat salinan PCM yang di-resample
    // ke sample rate native stream dari PCM asli menggunakan interpolasi linier.
    // PCM asli tetap disimpan agar bisa di-resample ulang jika rate berubah dan pitch tetap benar.
    if (mSampleRate > 0) {
        resampleAllSlots(mSampleRate);
    }

    LOGI("Oboe stream berhasil dibuka: sampleRate=%d Hz, channels=%d, format=%s, api=%s, sharing=%s, perf=%s",
         mSampleRate,
         mChannelCount,
         oboe::convertToText(mStream->getFormat()),
         oboe::convertToText(mStream->getAudioApi()),
         oboe::convertToText(mStream->getSharingMode()),
         oboe::convertToText(mStream->getPerformanceMode()));

    return oboe::Result::OK;
}

bool AudioEngine::openStream() {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    return openStreamInternal() == oboe::Result::OK;
}

bool AudioEngine::start() {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    if (!mStream) {
        oboe::Result result = openStreamInternal();
        if (result != oboe::Result::OK) {
            return false;
        }
    }

    if (mStream) {
        oboe::Result result = mStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Gagal memulai Oboe stream: %s", oboe::convertToText(result));
            return false;
        }
        LOGI("Oboe stream aktif dan berjalan pada sampleRate=%d Hz", mSampleRate);
        return true;
    }
    return false;
}

void AudioEngine::stop() {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    if (mStream) {
        oboe::Result result = mStream->requestStop();
        if (result != oboe::Result::OK) {
            LOGW("Peringatan saat requestStop Oboe stream: %s", oboe::convertToText(result));
        } else {
            LOGI("Oboe stream dihentikan sementara (stopped)");
        }
    }
    // Hentikan seluruh voice aktif agar tidak ada bunyi menggantung saat resume (Desain D3)
    resetVoicePoolInternal();
}

void AudioEngine::close() {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
        LOGI("Oboe stream ditutup dan resource dilepaskan");
    }
    resetVoicePoolInternal();
}

void AudioEngine::resetVoicePoolInternal() {
    for (size_t i = 0; i < kMaxVoices; ++i) {
        mVoicePool[i].reset();
    }
}

const std::array<Voice, AudioEngine::kMaxVoices>& AudioEngine::getVoicePool() const {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    return mVoicePool;
}

size_t AudioEngine::getActiveVoiceCount() const {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    size_t count = 0;
    for (size_t i = 0; i < kMaxVoices; ++i) {
        if (mVoicePool[i].isActive) {
            count++;
        }
    }
    return count;
}

void AudioEngine::resetVoicePool() {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    resetVoicePoolInternal();
}

bool AudioEngine::resampleSlotInternal(size_t slotIndex, int32_t targetSampleRate) {
    if (slotIndex >= kMaxSoundSlots) {
        return false;
    }

    auto& slot = mSoundSlots[slotIndex];
    if (!slot.isAssigned || slot.originalPcm.empty() || slot.originalSampleRate <= 0) {
        return false;
    }

    std::vector<float> resampled;
    bool success = Resampler::resampleLinear(
        slot.originalPcm.data(),
        slot.originalFrameCount,
        slot.originalSampleRate,
        targetSampleRate,
        resampled);

    if (success) {
        slot.resampledPcm = std::move(resampled);
        slot.resampledFrameCount = static_cast<int32_t>(slot.resampledPcm.size());
        LOGI("Slot %zu berhasil di-resample: %d frames (%d Hz) -> %d frames (%d Hz)",
             slotIndex,
             slot.originalFrameCount,
             slot.originalSampleRate,
             slot.resampledFrameCount,
             targetSampleRate);
        return true;
    } else {
        LOGE("Gagal me-resample slot %zu ke rate %d Hz", slotIndex, targetSampleRate);
        return false;
    }
}

void AudioEngine::resampleAllSlots(int32_t targetSampleRate) {
    if (targetSampleRate <= 0) {
        return;
    }

    LOGI("Me-resample seluruh slot suara ke target sample rate native: %d Hz", targetSampleRate);
    for (size_t i = 0; i < kMaxSoundSlots; ++i) {
        if (mSoundSlots[i].isAssigned && !mSoundSlots[i].originalPcm.empty()) {
            resampleSlotInternal(i, targetSampleRate);
        }
    }
}

bool AudioEngine::loadSound(
    const int16_t* pcmData,
    int32_t frameCount,
    int32_t sampleRate,
    int32_t slotIndex) {
    std::lock_guard<std::mutex> lock(mEngineMutex);

    if (slotIndex < 0 || static_cast<size_t>(slotIndex) >= kMaxSoundSlots) {
        LOGE("loadSound gagal: slotIndex %d di luar rentang [0, %zu)",
             slotIndex, kMaxSoundSlots);
        return false;
    }

    if (pcmData == nullptr || frameCount <= 0 || sampleRate <= 0) {
        LOGE("loadSound gagal: parameter data tidak valid (pcm=%p, frames=%d, rate=%d)",
             pcmData, frameCount, sampleRate);
        return false;
    }

    auto& slot = mSoundSlots[static_cast<size_t>(slotIndex)];

    // Salin PCM 16-bit asli ke memori C++ (C++ memegang kepemilikan penuh)
    slot.originalPcm.assign(pcmData, pcmData + frameCount);
    slot.originalFrameCount = frameCount;
    slot.originalSampleRate = sampleRate;
    slot.isAssigned = true;

    // Jika native stream sample rate sudah diketahui, langsung lakukan resampling ke rate tersebut.
    // Jika belum dibuka, resample awal ke sampleRate asli agar buffer siap digunakan seketika.
    // Saat openStream() nanti dipanggil, resampleAllSlots() akan menyesuaikan ulang ke native rate.
    const int32_t targetRate = (mSampleRate > 0) ? mSampleRate : sampleRate;
    resampleSlotInternal(static_cast<size_t>(slotIndex), targetRate);

    return true;
}

void AudioEngine::unloadSound(int32_t slotIndex) {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    if (slotIndex >= 0 && static_cast<size_t>(slotIndex) < kMaxSoundSlots) {
        for (size_t i = 0; i < kMaxVoices; ++i) {
            if (mVoicePool[i].slotIndex == slotIndex) {
                mVoicePool[i].reset();
            }
        }
        mSoundSlots[static_cast<size_t>(slotIndex)].reset();
        LOGI("Slot %d dibersihkan (unloaded)", slotIndex);
    }
}

void AudioEngine::releaseAllSounds() {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    resetVoicePoolInternal();
    for (size_t i = 0; i < kMaxSoundSlots; ++i) {
        mSoundSlots[i].reset();
    }
    LOGI("Seluruh slot suara dibebaskan (releaseAllSounds)");
}

const SoundSlot* AudioEngine::getSoundSlot(int32_t slotIndex) const {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    if (slotIndex >= 0 && static_cast<size_t>(slotIndex) < kMaxSoundSlots) {
        return &mSoundSlots[static_cast<size_t>(slotIndex)];
    }
    return nullptr;
}

int32_t AudioEngine::getSampleRate() const {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    return mSampleRate;
}

int32_t AudioEngine::getChannelCount() const {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    return mChannelCount;
}

bool AudioEngine::isStreamRunning() const {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    return mStream && (mStream->getState() == oboe::StreamState::Started);
}

bool AudioEngine::triggerSound(int32_t slotIndex, float gain) {
    if (slotIndex < 0 || static_cast<size_t>(slotIndex) >= kMaxSoundSlots) {
        return false;
    }
    // Masukkan event trigger ke SPSC ring buffer lock-free
    return mTriggerQueue.push({slotIndex, gain});
}

int32_t AudioEngine::findSlot() {
    // 1. Cari voice yang sedang tidak aktif di voice pool (hingga 32 suara)
    for (size_t i = 0; i < kMaxVoices; ++i) {
        if (!mVoicePool[i].isActive) {
            return static_cast<int32_t>(i);
        }
    }

    // 2. Jika seluruh 32 voice aktif, pilih slot tertua berdasarkan ageCounter (Voice Stealing)
    uint64_t oldestAge = UINT64_MAX;
    int32_t oldestIndex = 0;
    for (size_t i = 0; i < kMaxVoices; ++i) {
        if (mVoicePool[i].ageCounter < oldestAge) {
            oldestAge = mVoicePool[i].ageCounter;
            oldestIndex = static_cast<int32_t>(i);
        }
    }
    return oldestIndex;
}

float AudioEngine::softClip(float x) {
    constexpr float kThreshold = 0.7f;
    constexpr float kHeadroom = 1.0f - kThreshold; // 0.3f
    constexpr float kInvHeadroom = 1.0f / kHeadroom;

    // Zona linier: tidak ada distorsi pada volume normal di bawah threshold
    if (x > kThreshold) {
        return kThreshold + kHeadroom * std::tanh((x - kThreshold) * kInvHeadroom);
    } else if (x < -kThreshold) {
        return -kThreshold + kHeadroom * std::tanh((x + kThreshold) * kInvHeadroom);
    }
    return x;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames) {
    // INVARIANT KRITIS REAL-TIME:
    // Method ini dieksekusi pada thread prioritas tinggi audio callback.
    // DILARANG: alokasi memori (malloc/new), penguncian mutex, dan pemanggilan JNI.
    if (audioData == nullptr || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }

    // (1) Baca event sentuhan dari SPSC ring buffer lock-free
    TriggerEvent event;
    while (mTriggerQueue.pop(event)) {
        if (event.slotIndex >= 0 && static_cast<size_t>(event.slotIndex) < kMaxSoundSlots) {
            const auto& slot = mSoundSlots[static_cast<size_t>(event.slotIndex)];
            // Zona tanpa suara (sound == null / unassigned): abaikan secara aman (graceful silence)
            if (slot.isAssigned && !slot.resampledPcm.empty() && slot.resampledFrameCount > 0) {
                int32_t voiceIdx = findSlot();
                if (voiceIdx >= 0) {
                    Voice& v = mVoicePool[static_cast<size_t>(voiceIdx)];

                    // KETENTUAN TAHAP 5.6 & DESAIN D3:
                    // Jika voice yang diambil alih sedang aktif (Voice Stealing),
                    // lakukan micro-fade-out 1ms untuk mencegah bunyi pop/klik.
                    if (v.isActive && v.resampledPcm != nullptr && v.playbackPosition < v.resampledFrameCount) {
                        v.fadeOutPcm = v.resampledPcm;
                        v.fadeOutPosition = v.playbackPosition;
                        v.fadeOutFrameCount = v.resampledFrameCount;
                        v.fadeOutGain = v.gain;
                        v.isStealCrossfading = true;
                        // 1 ms = 0.001 * sampleRate (misal 48 frame pada 48000 Hz)
                        const int32_t rate = (mSampleRate > 0) ? mSampleRate : 48000;
                        v.totalStealFadeFrames = std::max(1, static_cast<int32_t>(rate * 0.001f));
                        v.stealFadeFramesRemaining = v.totalStealFadeFrames;
                    } else {
                        v.isStealCrossfading = false;
                        v.fadeOutPcm = nullptr;
                    }

                    v.originalPcm = slot.originalPcm.data();
                    v.originalFrameCount = slot.originalFrameCount;
                    v.originalSampleRate = slot.originalSampleRate;
                    v.resampledPcm = slot.resampledPcm.data();
                    v.resampledFrameCount = slot.resampledFrameCount;
                    v.playbackPosition = 0;
                    // Batasi gain zona ke rentang 0.0 - 2.0 sesuai spesifikasi
                    v.gain = std::max(0.0f, std::min(2.0f, event.gain));
                    v.slotIndex = event.slotIndex;
                    v.ageCounter = ++mAgeSequence;
                    v.isActive = true;
                }
            }
        }
    }

    const int32_t channels = audioStream->getChannelCount();
    const bool isFloat = (audioStream->getFormat() == oboe::AudioFormat::Float);
    float* floatBuffer = isFloat ? static_cast<float*>(audioData) : nullptr;
    int16_t* int16Buffer = !isFloat ? static_cast<int16_t*>(audioData) : nullptr;

    // (2) & (3) Mix seluruh voice aktif, kalikan gain zona, dan akumulasi ke output buffer
    for (int32_t f = 0; f < numFrames; ++f) {
        float mixedSample = 0.0f;

        for (size_t i = 0; i < kMaxVoices; ++i) {
            Voice& v = mVoicePool[i];
            if (!v.isActive || v.resampledPcm == nullptr) {
                continue;
            }

            if (v.playbackPosition < v.resampledFrameCount) {
                float sample = v.resampledPcm[v.playbackPosition] * v.gain;

                // Micro-fade-out 1ms crossfade saat Voice Stealing (mencegah bunyi pop/klik)
                if (v.isStealCrossfading) {
                    const float fadeOutWeight = static_cast<float>(v.stealFadeFramesRemaining) /
                                                static_cast<float>(v.totalStealFadeFrames);
                    const float fadeInWeight = 1.0f - fadeOutWeight;

                    float oldSample = 0.0f;
                    if (v.fadeOutPcm != nullptr && v.fadeOutPosition < v.fadeOutFrameCount) {
                        oldSample = v.fadeOutPcm[v.fadeOutPosition] * v.fadeOutGain;
                        v.fadeOutPosition++;
                    }

                    // Transisi mulus: oldSample memudar ke 0, newSample naik ke 100%
                    sample = (oldSample * fadeOutWeight) + (sample * fadeInWeight);

                    v.stealFadeFramesRemaining--;
                    if (v.stealFadeFramesRemaining <= 0) {
                        v.isStealCrossfading = false;
                        v.fadeOutPcm = nullptr;
                    }
                }

                mixedSample += sample;
                v.playbackPosition++;

                if (v.playbackPosition >= v.resampledFrameCount) {
                    v.isActive = false;
                }
            } else {
                v.isActive = false;
            }
        }

        // (4) Terapkan master limiter (soft-clip) untuk mencegah hard clipping
        const float limitedSample = softClip(mixedSample);

        // Tulis ke output buffer (Stereo atau Mono)
        if (isFloat) {
            if (channels == 2) {
                floatBuffer[f * 2] = limitedSample;
                floatBuffer[f * 2 + 1] = limitedSample;
            } else {
                for (int32_t c = 0; c < channels; ++c) {
                    floatBuffer[f * channels + c] = limitedSample;
                }
            }
        } else {
            const int16_t sampleI16 = static_cast<int16_t>(limitedSample * 32767.0f);
            if (channels == 2) {
                int16Buffer[f * 2] = sampleI16;
                int16Buffer[f * 2 + 1] = sampleI16;
            } else {
                for (int32_t c = 0; c < channels; ++c) {
                    int16Buffer[f * channels + c] = sampleI16;
                }
            }
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorBeforeClose(
    oboe::AudioStream * /* audioStream */,
    oboe::Result error) {
    LOGW("Oboe onErrorBeforeClose: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(
    oboe::AudioStream * /* audioStream */,
    oboe::Result error) {
    LOGI("Oboe onErrorAfterClose: %s — memulai reconnect otomatis", oboe::convertToText(error));

    // Reconnect otomatis diperlukan saat routing audio berubah, misalnya:
    //   - Headphone dicolokkan → Android memindahkan output ke headphone jack/BT.
    //   - Headphone dicabut → Android mengembalikan output ke speaker internal.
    //   - Bluetooth audio device connect/disconnect.
    //
    // Alasan: Oboe menutup stream lama dan memanggil callback ini dari thread internalnya.
    // Karena onErrorAfterClose berjalan di luar audio callback (boleh mutex),
    // kita dapat langsung membuka ulang stream dengan openStreamInternal() yang
    // secara otomatis membaca native sample rate baru dan memanggil resampleAllSlots()
    // sehingga pitch tetap benar meskipun rate hardware berubah (misal speaker 48000 →
    // headphone DAC 44100 atau sebaliknya).
    //
    // Reconnect tidak dilakukan jika bukan error routing (misal error karena close() dipanggil
    // eksplisit dari Kotlin); namun Oboe tidak memanggil callback ini saat close() eksplisit,
    // sehingga heuristik ini cukup aman.

    std::lock_guard<std::mutex> lock(mEngineMutex);

    // Lepas pointer stream lama yang sudah ditutup Oboe agar openStreamInternal()
    // memulai fresh tanpa menyentuh stream yang sudah invalid.
    mStream.reset();

    oboe::Result openResult = openStreamInternal();
    if (openResult != oboe::Result::OK) {
        LOGE("Reconnect gagal membuka stream baru: %s", oboe::convertToText(openResult));
        // Tidak ada crash — engine tetap valid, aplikasi hanya tidak bisa berbunyi.
        // Kotlin/UI dapat memanggil openStream() + startStream() kembali saat user
        // mencolokkan perangkat audio atau melalui error callback di lapisan atas.
        return;
    }

    oboe::Result startResult = mStream->requestStart();
    if (startResult != oboe::Result::OK) {
        LOGE("Reconnect gagal memulai stream baru: %s", oboe::convertToText(startResult));
        // Stream terbuka tapi tidak bisa dimulai; biarkan Kotlin retry jika perlu.
        return;
    }

    // resampleAllSlots() sudah dipanggil di dalam openStreamInternal() saat mSampleRate dibaca.
    // Jika sample rate berubah (misal 48000 → 44100), seluruh slot PCM sudah di-resample ulang
    // ke rate baru sebelum requestStart() dipanggil, sehingga pitch langsung benar.
    LOGI("Reconnect berhasil: stream baru aktif pada sampleRate=%d Hz", mSampleRate);
}
