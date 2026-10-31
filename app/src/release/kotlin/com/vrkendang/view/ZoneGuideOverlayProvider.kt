package com.vrkendang.view

import android.graphics.Canvas
import com.vrkendang.kitcore.model.KitData

/**
 * Penyedia overlay garis panduan batas zona untuk build release.
 *
 * Sesuai Task 7.5 & Spesifikasi Kendang Board:
 * Pada build release, overlay tidak hadir di aplikasi. Kelas ini berupa stub
 * kosong sehingga tidak ada kode drawing, objek Paint/Path, atau overhead di APK rilis.
 */
object ZoneGuideOverlayProvider {
    fun create(): ZoneGuideOverlay = ReleaseZoneGuideOverlay()
}

/**
 * Implementasi no-op untuk build release.
 */
internal class ReleaseZoneGuideOverlay : ZoneGuideOverlay {
    override var isEnabled: Boolean
        get() = false
        set(_) {
            // No-op pada build release
        }

    override fun draw(
        canvas: Canvas,
        kit: KitData,
        mapper: CanvasCoordinateMapper
    ) {
        // No-op pada build release: tidak ada overlay garis zona yang dirender.
    }
}
