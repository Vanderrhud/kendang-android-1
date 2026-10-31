#ifndef VR_KENDANG_LOCK_FREE_QUEUE_H
#define VR_KENDANG_LOCK_FREE_QUEUE_H

#include <atomic>
#include <array>
#include <cstddef>
#include <cstdint>

/**
 * Event pemicu suara sentuhan dari thread UI ke thread audio callback.
 */
struct TriggerEvent {
    int32_t slotIndex{-1};
    float gain{1.0f};
};

/**
 * Lock-free Single-Producer Single-Consumer (SPSC) Ring Buffer.
 *
 * Invariant Keamanan Real-Time (Desain D3 & Spesifikasi):
 * - Thread UI adalah produser tunggal (push).
 * - Thread audio callback adalah konsumen tunggal (pop).
 * - Bebas mutex, bebas locking, bebas alokasi dinamis (wait-free, O(1)).
 * - Ukuran kapasitas bernilai pangkat dua (power of 2) untuk efisiensi bitwise mask.
 */
template <typename T, size_t Capacity>
class SpscRingBuffer {
    static_assert((Capacity & (Capacity - 1)) == 0, "Kapasitas harus berupa pangkat dua");
public:
    SpscRingBuffer() : mHead(0), mTail(0) {}

    /**
     * Memasukkan item ke antrean (hanya dipanggil oleh thread produser / UI).
     * @return true jika berhasil, false jika antrean penuh (event di-drop agar tidak memblokir)
     */
    bool push(const T& item) {
        const size_t head = mHead.load(std::memory_order_relaxed);
        const size_t tail = mTail.load(std::memory_order_acquire);

        if ((head - tail) >= Capacity) {
            return false;
        }

        mBuffer[head & (Capacity - 1)] = item;
        mHead.store(head + 1, std::memory_order_release);
        return true;
    }

    /**
     * Mengambil item dari antrean (hanya dipanggil oleh thread konsumen / audio callback).
     * @return true jika ada item yang diambil, false jika antrean kosong
     */
    bool pop(T& item) {
        const size_t tail = mTail.load(std::memory_order_relaxed);
        const size_t head = mHead.load(std::memory_order_acquire);

        if (tail == head) {
            return false;
        }

        item = mBuffer[tail & (Capacity - 1)];
        mTail.store(tail + 1, std::memory_order_release);
        return true;
    }

    bool isEmpty() const {
        return mTail.load(std::memory_order_relaxed) == mHead.load(std::memory_order_relaxed);
    }

    size_t size() const {
        const size_t head = mHead.load(std::memory_order_relaxed);
        const size_t tail = mTail.load(std::memory_order_relaxed);
        return (head >= tail) ? (head - tail) : 0;
    }

    constexpr size_t capacity() const {
        return Capacity;
    }

    void clear() {
        mTail.store(mHead.load(std::memory_order_relaxed), std::memory_order_release);
    }

private:
    std::array<T, Capacity> mBuffer;
    std::atomic<size_t> mHead{0};
    std::atomic<size_t> mTail{0};
};

#endif // VR_KENDANG_LOCK_FREE_QUEUE_H
