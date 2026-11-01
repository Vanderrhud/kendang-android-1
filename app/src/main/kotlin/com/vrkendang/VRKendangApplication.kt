package com.vrkendang

import android.app.Application
import com.vrkendang.audio.AudioLifecycleManager

/**
 * Kelas Application utama untuk VR Kendang.
 *
 * Menginisialisasi pelacakan siklus hidup audio global [AudioLifecycleManager]
 * agar stream audio Oboe tidak terputus saat bernavigasi antar-Activity internal.
 */
class VRKendangApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AudioLifecycleManager.init(this)
    }
}
