package com.vrkendang.view

import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius

/**
 * Pemetaan koordinat sistem kanvas papan kendang.
 *
 * Mendukung:
 * - Mode standar (`fitInsideView = false`): `canvasHeight = viewHeight`, `canvasWidth = canvasHeight * canvasAspect`.
 *   Bila `canvasAspect == viewWidth / viewHeight` (mode full-bleed layar penuh), kanvas mengisi 100% view (`0..viewWidth`, `0..viewHeight`).
 * - Mode panel preview (`fitInsideView = true`): bila lebar panel lebih sempit dari `viewHeight * canvasAspect`,
 *   kanvas di-fit berdasarkan lebar panel (`canvasWidth = viewWidth`, `canvasHeight = viewWidth / canvasAspect`)
 *   dan dipusatkan secara vertikal agar keempat kendang tampil utuh tanpa terpotong.
 * - Radius kendang (`rx`, `ry`) selalu dikonversi menggunakan `canvasHeight` sehingga kendang lingkaran (`rx == ry`)
 *   selalu berbentuk lingkaran sejati di rasio layar apapun.
 */
class CanvasCoordinateMapper(
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
    val canvasAspect: Float = 2.0f,
    val fitInsideView: Boolean = false
) {
    private val safeAspect: Float = if (canvasAspect > 0f) canvasAspect else 2.0f

    /** Tinggi kanvas dalam piksel. */
    val canvasHeight: Float = when {
        viewHeight <= 0f -> 0f
        fitInsideView && viewWidth > 0f && viewWidth < viewHeight * safeAspect -> viewWidth / safeAspect
        else -> viewHeight
    }

    /** Lebar kanvas dalam piksel (canvasHeight * canvasAspect). */
    val canvasWidth: Float = canvasHeight * safeAspect

    /** Batas kiri kanvas dalam piksel (terpusat horizontal). */
    val canvasLeft: Float = (viewWidth - canvasWidth) / 2f

    /** Batas atas kanvas dalam piksel (terpusat vertikal bila fitInsideView aktif). */
    val canvasTop: Float = if (fitInsideView && viewHeight > canvasHeight) {
        (viewHeight - canvasHeight) / 2f
    } else {
        0f
    }

    /** Batas kanan kanvas dalam piksel. */
    val canvasRight: Float = canvasLeft + canvasWidth

    /** Batas bawah kanvas dalam piksel. */
    val canvasBottom: Float = canvasTop + canvasHeight

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
