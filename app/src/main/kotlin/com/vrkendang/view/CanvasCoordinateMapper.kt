package com.vrkendang.view

import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius

/**
 * Pemetaan koordinat sistem aspect-fit untuk kanvas papan kendang.
 *
 * Sesuai Desain D5 & Spesifikasi Kendang Board:
 * - Kanvas di-fit berdasarkan tinggi view (`canvasHeight = viewHeight`).
 * - Kanvas dipusatkan secara horizontal (`canvasLeft = (viewWidth - canvasWidth) / 2`).
 * - Lebar kanvas dihitung dari `canvasHeight * canvasAspect`.
 * - Sisa ruang horizontal kiri-kanan menjadi margin (sentuhan di luar kanvas diabaikan).
 * - Radius kendang (rx, ry) adalah pecahan dari tinggi kanvas, sehingga kendang lingkaran
 *   (rx == ry) selalu berbentuk lingkaran sejati di rasio layar apapun (misal 16:9, 20:9).
 */
class CanvasCoordinateMapper(
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
    val canvasAspect: Float = 2.0f
) {
    /** Tinggi kanvas dalam piksel (sama dengan tinggi view). */
    val canvasHeight: Float = if (viewHeight > 0f) viewHeight else 0f

    /** Lebar kanvas dalam piksel (canvasHeight * canvasAspect). */
    val canvasWidth: Float = canvasHeight * if (canvasAspect > 0f) canvasAspect else 2.0f

    /** Batas kiri kanvas dalam piksel (terpusat horizontal). */
    val canvasLeft: Float = (viewWidth - canvasWidth) / 2f

    /** Batas atas kanvas dalam piksel. */
    val canvasTop: Float = 0f

    /** Batas kanan kanvas dalam piksel. */
    val canvasRight: Float = canvasLeft + canvasWidth

    /** Batas bawah kanvas dalam piksel. */
    val canvasBottom: Float = canvasHeight

    /**
     * Memeriksa apakah suatu titik koordinat piksel berada di dalam batas kanvas.
     * Titik di area margin horizontal atau di luar vertikal mengembalikan false.
     */
    fun isInsideCanvas(pixelX: Float, pixelY: Float): Boolean {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return false
        return pixelX in canvasLeft..canvasRight && pixelY in canvasTop..canvasBottom
    }

    /**
     * Mengonversi koordinat piksel ke koordinat kanvas relatif [0.0..1.0].
     * Mengembalikan null jika kanvas belum memiliki dimensi valid.
     */
    fun toCanvasCoordinates(pixelX: Float, pixelY: Float): Point? {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return null
        val cx = (pixelX - canvasLeft) / canvasWidth
        val cy = (pixelY - canvasTop) / canvasHeight
        return Point(cx, cy)
    }

    /**
     * Mengonversi koordinat kanvas relatif [0.0..1.0] ke koordinat piksel horizontal.
     */
    fun toPixelX(canvasX: Float): Float {
        return canvasLeft + canvasX * canvasWidth
    }

    /**
     * Mengonversi koordinat kanvas relatif [0.0..1.0] ke koordinat piksel vertikal.
     */
    fun toPixelY(canvasY: Float): Float {
        return canvasTop + canvasY * canvasHeight
    }

    /**
     * Mengonversi radius X relatif (pecahan tinggi kanvas) ke piksel.
     */
    fun toPixelRadiusX(rx: Float): Float {
        return rx * canvasHeight
    }

    /**
     * Mengonversi radius Y relatif (pecahan tinggi kanvas) ke piksel.
     */
    fun toPixelRadiusY(ry: Float): Float {
        return ry * canvasHeight
    }

    /**
     * Mengonversi objek [Radius] relatif ke pasangan radius piksel (rxPx, ryPx).
     */
    fun toPixelRadius(radius: Radius): Pair<Float, Float> {
        return Pair(toPixelRadiusX(radius.rx), toPixelRadiusY(radius.ry))
    }
}
