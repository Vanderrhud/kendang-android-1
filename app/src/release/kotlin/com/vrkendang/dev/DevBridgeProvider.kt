package com.vrkendang.dev

import android.app.Activity
import com.vrkendang.view.KendangBoardView

/**
 * Penyedia DevBridge untuk build release.
 *
 * Pada build release, mengembalikan no-op stub agar tidak ada kode atau referensi
 * ke fitur penyusun kit di dalam APK release (memenuhi CI check 2.4).
 */
object DevBridgeProvider {
    fun get(): DevBridge = ReleaseDevBridge()
}

internal class ReleaseDevBridge : DevBridge {
    override fun attach(activity: Activity, boardView: KendangBoardView) {
        // No-op pada build release
    }
}
