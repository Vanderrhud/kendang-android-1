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
    const val MAX_SCALE = 1.5f

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Menghitung batas skala maksimum adaptif agar kendang tidak keluar dari batas kanvas
     * berdasarkan posisi center saat ini dan radius dasarnya.
     */
    fun calculateMaxAdaptiveScale(
        center: Point,
        radius: Radius,
        canvasAspect: Float
    ): Float {
        val aspect = max(0.1f, canvasAspect)
        val xu = center.x * aspect
        val yu = center.y

        val maxScaleLeft = if (radius.rx > 0f) xu / radius.rx else MAX_SCALE
        val maxScaleRight = if (radius.rx > 0f) (aspect - xu) / radius.rx else MAX_SCALE
        val maxScaleTop = if (radius.ry > 0f) yu / radius.ry else MAX_SCALE
        val maxScaleBottom = if (radius.ry > 0f) (1.0f - yu) / radius.ry else MAX_SCALE

        val boundaryMax = min(min(maxScaleLeft, maxScaleRight), min(maxScaleTop, maxScaleBottom))
        return min(MAX_SCALE, max(MIN_SCALE, boundaryMax))
    }

    /**
     * Menjepit koordinat center agar seluruh lingkaran/oval kendang tetap berada di dalam kanvas.
     */
    fun clampCenter(
        center: Point,
        radius: Radius,
        canvasAspect: Float,
        scale: Float
    ): Point {
        val aspect = max(0.1f, canvasAspect)
        val rxEff = radius.rx * scale
        val ryEff = radius.ry * scale

        // Batas horizontal (x dalam rentang 0.0..1.0)
        val minX = rxEff / aspect
        val maxX = 1.0f - (rxEff / aspect)
        val clampedX = if (minX >= maxX) 0.5f else center.x.coerceIn(minX, maxX)

        // Batas vertikal (y dalam rentang 0.0..1.0)
        val minY = ryEff
        val maxY = 1.0f - ryEff
        val clampedY = if (minY >= maxY) 0.5f else center.y.coerceIn(minY, maxY)

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
                // 1. Skala dijepit ke batas adaptif dan 0.5..1.5
                val rawScale = drumOverride.scale ?: 1.0f
                val clampedScale = rawScale.coerceIn(MIN_SCALE, MAX_SCALE)

                val targetCenter = drumOverride.center ?: drum.center
                val maxAdaptive = calculateMaxAdaptiveScale(targetCenter, drum.radius, baseKit.canvasAspect)
                val finalScale = min(clampedScale, maxAdaptive)

                // 2. Center dijepit ke kanvas
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
