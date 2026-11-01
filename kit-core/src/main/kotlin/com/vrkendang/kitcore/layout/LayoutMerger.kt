package com.vrkendang.kitcore.layout

import com.vrkendang.kitcore.model.*
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min

/**
 * Penggabung tata letak override pemain ke data kit bawaan.
 * Menerapkan skala adaptif, penjepitan posisi ke kanvas, dan fallback aman jika berkas override rusak.
 */
object LayoutMerger {

    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 5.0f

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Menghitung batas skala maksimum adaptif untuk kendang.
     * Mengizinkan perbesaran skala hingga MAX_SCALE (5.0f).
     */
    fun calculateMaxAdaptiveScale(
        center: Point,
        radius: Radius,
        canvasAspect: Float
    ): Float {
        return MAX_SCALE
    }

    /**
     * Menjepit koordinat center agar kendang tetap memiliki bagian yang berada di kanvas
     * (safety retention boundary) sekaligus mengizinkan kendang digeser sebagian ke luar kanvas.
     * Mempertahankan setidaknya 20% radius di dalam batas kanvas (80% boleh berada di luar).
     */
    fun clampCenter(
        center: Point,
        radius: Radius,
        canvasAspect: Float,
        scale: Float = 1.0f
    ): Point {
        val aspect = max(0.1f, canvasAspect)
        val rxEff = radius.rx * scale
        val ryEff = radius.ry * scale

        // Toleransi retensi pengaman (safety retention):
        // center.x + (rxEff / aspect) >= 0.2f * (rxEff / aspect) => center.x >= -0.8f * (rxEff / aspect)
        // center.x - (rxEff / aspect) <= 1.0f - 0.2f * (rxEff / aspect) => center.x <= 1.0f + 0.8f * (rxEff / aspect)
        val horizontalRetention = (rxEff / aspect) * 0.8f
        val minX = -horizontalRetention
        val maxX = 1.0f + horizontalRetention
        val clampedX = center.x.coerceIn(minX, maxX)

        // center.y + ryEff >= 0.2f * ryEff => center.y >= -0.8f * ryEff
        // center.y - ryEff <= 1.0f - 0.2f * ryEff => center.y <= 1.0f + 0.8f * ryEff
        val verticalRetention = ryEff * 0.8f
        val minY = -verticalRetention
        val maxY = 1.0f + verticalRetention
        val clampedY = center.y.coerceIn(minY, maxY)

        return Point(clampedX, clampedY)
    }

    /**
     * Menerapkan LayoutOverride ke KitData dan menghasilkan kit efektif yang lolos validasi.
     */
    fun apply(baseKit: KitData, override: LayoutOverride): KitData {
        if (baseKit.kitId != override.kitId) {
            return baseKit
        }

        val updatedDrums = baseKit.drums.map { drum ->
            val drumOverride = override.drums[drum.id]
            if (drumOverride != null) {
                // 1. Skala dijepit ke rentang 0.5..5.0
                val rawScale = drumOverride.scale ?: 1.0f
                val clampedScale = rawScale.coerceIn(MIN_SCALE, MAX_SCALE)

                val targetCenter = drumOverride.center ?: drum.center
                val maxAdaptive = calculateMaxAdaptiveScale(targetCenter, drum.radius, baseKit.canvasAspect)
                val finalScale = min(clampedScale, maxAdaptive)

                // 2. Center dijepit dengan batas retensi aman kanvas
                val clampedCenter = clampCenter(targetCenter, drum.radius, baseKit.canvasAspect, finalScale)

                drum.copy(
                    center = clampedCenter,
                    radius = Radius(drum.radius.rx * finalScale, drum.radius.ry * finalScale)
                )
            } else {
                drum
            }
        }

        return baseKit.copy(drums = updatedDrums)
    }

    /**
     * Mem-parsing JSON override string dan menggabungkannya ke baseKit.
     * Mengembalikan baseKit secara aman jika JSON null, kosong, atau rusak.
     */
    fun mergeFromJson(baseKit: KitData, overrideJson: String?): KitData {
        if (overrideJson.isNullOrBlank()) {
            return baseKit
        }

        return try {
            val parsedOverride = json.decodeFromString<LayoutOverride>(overrideJson)
            apply(baseKit, parsedOverride)
        } catch (_: Exception) {
            // Berkas override rusak, fallback aman ke kit bawaan
            baseKit
        }
    }
}
