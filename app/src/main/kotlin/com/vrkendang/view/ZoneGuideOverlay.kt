package com.vrkendang.view

import android.graphics.Canvas
import com.vrkendang.kitcore.model.KitData

/**
 * Kontrak untuk menggambar overlay garis panduan batas zona akustik (busur C_div dan garis split).
 *
 * Sesuai Spesifikasi Kendang Board & Desain:
 * - Pada build debug, diimplementasikan penuh untuk mempermudah kalibrasi langsung di perangkat (Task 7.5).
 * - Pada build release, berupa stub kosong tanpa kode render sehingga tidak ada kode atau overhead overlay di APK rilis.
 */
interface ZoneGuideOverlay {
    /** Status aktif/tidaknya garis panduan zona. */
    var isEnabled: Boolean

    /**
     * Menggambar garis batas zona di atas kanvas kendang.
     */
    fun draw(
        canvas: Canvas,
        kit: KitData,
        mapper: CanvasCoordinateMapper
    )
}
