package com.vrkendang.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import com.vrkendang.kitcore.model.DrumData
import com.vrkendang.kitcore.model.ZoneData

/**
 * Data class untuk highlight visual sesaat pada zona yang disentuh.
 *
 * @param drum Data kendang yang disentuh.
 * @param zone Data zona akustik spesifik yang disentuh.
 * @param startTimeMs Waktu sentuhan dalam epoch uptime millisecond.
 * @param durationMs Durasi animasi pudar highlight (~100 ms sesuai spesifikasi).
 */
data class ActiveZoneHighlight(
    val drum: DrumData,
    val zone: ZoneData,
    val startTimeMs: Long,
    val durationMs: Long = 100L
)

/**
 * Menggambar highlight visual presisi per bentuk geometri spesifik zona akustik (Task 7.3).
 *
 * Sesuai Desain & Spesifikasi Kendang Board:
 * - Bentuk geometri presisi menutupi zona spesifik yang disentuh:
 *   - Model single: seluruh kulit oval kendang.
 *   - Model split: setengah oval (upper atau lower) terpotong tepat di garis tengah cy.
 *   - Model bass:
 *     - main (Bass-1): di dalam oval Bass DAN di dalam lingkaran C_div.
 *     - crescent (Bass-2..5): di dalam oval Bass DAN di luar lingkaran C_div,
 *       terpotong sesuai band vertikal (0-3 dari atas ke bawah).
 * - Highlight berwarna putih semi-transparan (~50% alpha awal) yang memudar selama ~100 ms.
 */
object ZoneHighlightDrawer {

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Menggambar highlight zona yang aktif pada [canvas].
     */
    fun drawHighlight(
        canvas: Canvas,
        drum: DrumData,
        zone: ZoneData,
        alpha: Int,
        mapper: CanvasCoordinateMapper
    ) {
        if (alpha <= 0) return

        highlightPaint.color = Color.argb(alpha.coerceIn(0, 255), 255, 255, 255)

        val cx = mapper.toPixelX(drum.center.x)
        val cy = mapper.toPixelY(drum.center.y)
        val rx = mapper.toPixelRadiusX(drum.radius.rx)
        val ry = mapper.toPixelRadiusY(drum.radius.ry)

        if (rx <= 0f || ry <= 0f) return

        val drumOval = RectF(cx - rx, cy - ry, cx + rx, cy + ry)

        when (drum.model) {
            "single" -> {
                canvas.drawOval(drumOval, highlightPaint)
            }

            "split" -> {
                canvas.save()
                if (zone.region == "upper") {
                    canvas.clipRect(cx - rx, cy - ry, cx + rx, cy)
                } else {
                    canvas.clipRect(cx - rx, cy, cx + rx, cy + ry)
                }
                canvas.drawOval(drumOval, highlightPaint)
                canvas.restore()
            }

            "bass" -> {
                val divider = drum.divider
                if (divider == null) {
                    canvas.drawOval(drumOval, highlightPaint)
                    return
                }

                val divCenterX = cx + (divider.dx * rx)
                val divCenterY = cy + (divider.dy * ry)
                val divRadius = divider.r * rx

                val circlePath = Path().apply {
                    addCircle(divCenterX, divCenterY, divRadius, Path.Direction.CW)
                }

                if (zone.region == "main") {
                    // Bass-1: di dalam oval Bass DAN di dalam lingkaran C_div
                    canvas.save()
                    canvas.clipPath(circlePath)
                    canvas.drawOval(drumOval, highlightPaint)
                    canvas.restore()
                } else {
                    // Bass-2..5: di dalam oval Bass DAN di luar C_div, terpotong band vertikal
                    val bandIndex = (zone.band ?: 0).coerceIn(0, 3)
                    val top = cy - ry
                    val bandHeight = (2f * ry) / 4f
                    val bandTop = top + bandIndex * bandHeight
                    val bandBottom = bandTop + bandHeight

                    canvas.save()
                    canvas.clipRect(cx - rx, bandTop, cx + rx, bandBottom)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        canvas.clipOutPath(circlePath)
                    } else {
                        @Suppress("DEPRECATION")
                        canvas.clipPath(circlePath, android.graphics.Region.Op.DIFFERENCE)
                    }
                    canvas.drawOval(drumOval, highlightPaint)
                    canvas.restore()
                }
            }

            else -> {
                canvas.drawOval(drumOval, highlightPaint)
            }
        }
    }
}
