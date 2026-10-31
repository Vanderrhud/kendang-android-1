package com.vrkendang.kitauthoring

import android.app.Activity
import android.content.Intent
import com.vrkendang.dev.DevBridge
import com.vrkendang.view.KendangBoardView

/**
 * Jembatan Mode Developer untuk build debug (Tahap 11).
 *
 * Membuka halaman penuh khusus [DevAuthoringActivity] agar antarmuka penyusunan kit
 * memiliki halaman terpisah dan tidak menumpuk di atas papan permainan utama.
 */
class DevKitAuthoringBridge : DevBridge {

    override fun attach(activity: Activity, boardView: KendangBoardView) {
        val intent = Intent(activity, DevAuthoringActivity::class.java)
        activity.startActivity(intent)
    }
}
