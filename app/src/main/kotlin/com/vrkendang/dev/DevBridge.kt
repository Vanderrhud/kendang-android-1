package com.vrkendang.dev

import android.app.Activity
import com.vrkendang.view.KendangBoardView

/**
 * Jembatan antarmuka untuk menghubungkan fitur developer/pengujian ke Activity utama.
 *
 * Pada build release: dihubungkan ke no-op stub di src/release (tanpa kode dev).
 * Pada build debug: dihubungkan ke implementasi di src/debug (Task 8.1 & CI check 2.4).
 */
interface DevBridge {
    /**
     * Memasang entry point developer pada [activity] dan [boardView].
     */
    fun attach(activity: Activity, boardView: KendangBoardView)
}
