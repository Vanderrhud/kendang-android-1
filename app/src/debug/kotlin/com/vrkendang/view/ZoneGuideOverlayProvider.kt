package com.vrkendang.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.vrkendang.kitcore.model.KitData

/**
 * Penyedia overlay garis panduan batas zona akustik untuk build debug.
 */
object ZoneGuideOverlayProvider {
    fun create(): ZoneGuideOverlay = DebugZoneGuideOverlay()
}

/**
 * Implementasi debug: merender garis batas zona akustik (busur C_div dan garis split)
 * untuk membantu kalibrasi di HP/tablet fisik (Task 7.5).
 */
internal class DebugZoneGuideOverlay : ZoneGuideOverlay {
    override var isEnabled: Boolean = false

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val bandDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    override fun draw(
        canvas: Canvas,
        kit: KitData,
        mapper: CanvasCoordinateMapper
    ) {
        if (!isEnabled) return

        for (drum in kit.drums.asReversed()) {
            val cx = mapper.toPixelX(drum.center.x)
            val cy = mapper.toPixelY(drum.center.y)
            val rx = mapper.toPixelRadiusX(drum.radius.rx)
            val ry = mapper.toPixelRadiusY(drum.radius.ry)

            if (rx <= 0f || ry <= 0f) continue

            val ovalRect = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
            val drumOvalPath = Path().apply {
                addOval(ovalRect, Path.Direction.CW)
            }

            when (drum.model) {
                "bass" -> {
                    val divider = drum.divider ?: continue
                    val divCenterX = cx + (divider.dx * rx)
                    val divCenterY = cy + (divider.dy * ry)
                    val divRadius = divider.r * rx

                    // 1. Busur C_div pembatas di dalam oval Bass
                    canvas.save()
                    canvas.clipPath(drumOvalPath)
                    canvas.drawCircle(divCenterX, divCenterY, divRadius, dividerPaint)

                    // 2. Garis pemisah 4 band vertikal bulan sabit (band 0..3)
                    val top = cy - ry
                    val bandHeight = (2f * ry) / 4f
                    for (i in 1..3) {
                        val y = top + i * bandHeight
                        canvas.drawLine(cx - rx, y, cx + rx, y, bandDividerPaint)
                    }
                    canvas.restore()
                }

                "split" -> {
                    val zoneCount = drum.zones.size
                    val angleOffset = drum.divider?.angle ?: 0f
                    if (zoneCount == 2 && angleOffset == 0f && drum.zones.any { it.region == "upper" }) {
                        canvas.save()
                        canvas.clipPath(drumOvalPath)
                        canvas.drawLine(cx - rx, cy, cx + rx, cy, dividerPaint)
                        canvas.restore()
                    } else if (zoneCount > 1) {
                        canvas.save()
                        canvas.clipPath(drumOvalPath)
                        val sliceDeg = 360f / zoneCount
                        for (i in 0 until zoneCount) {
                            val spokeAngleDeg = angleOffset + (i * sliceDeg)
                            val spokeRad = Math.toRadians(spokeAngleDeg.toDouble())
                            val targetX = cx + (rx * kotlin.math.cos(spokeRad)).toFloat()
                            val targetY = cy + (ry * kotlin.math.sin(spokeRad)).toFloat()
                            canvas.drawLine(cx, cy, targetX, targetY, dividerPaint)
                        }
                        canvas.restore()
                    }
                }
            }
        }
    }
}
