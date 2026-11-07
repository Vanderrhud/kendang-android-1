package com.vrkendang.kitcore.hittest

import com.vrkendang.kitcore.model.DrumData
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.model.ZoneData

/**
 * Mesin hit-testing geometri aljabar murni di :kit-core (tanpa dependensi android.*).
 * Menggunakan sistem koordinat satuan kanvas:
 * X_u = canvasX * canvasAspect, Y_u = canvasY
 */
object HitTester {

    data class HitResult(
        val drum: DrumData,
        val zone: ZoneData
    )

    /**
     * Mencari zona yang disentuh berdasarkan koordinat kanvas relatif (0.0..1.0).
     * Mengembalikan ZoneData jika menyentuh suatu zona, atau null jika di luar seluruh kendang.
     * Evaluasi memprioritaskan urutan array drums di kit.json (indeks lebih awal menang jika overlap).
     */
    fun findZone(kit: KitData, canvasX: Float, canvasY: Float): ZoneData? {
        return findHit(kit, canvasX, canvasY)?.zone
    }

    /**
     * Mencari hasil hit lengkap (DrumData dan ZoneData) untuk koordinat kanvas relatif.
     */
    fun findHit(kit: KitData, canvasX: Float, canvasY: Float): HitResult? {
        val xu = canvasX * kit.canvasAspect
        val yu = canvasY

        for (drum in kit.drums) {
            val cxu = drum.center.x * kit.canvasAspect
            val cyu = drum.center.y
            val rx = drum.radius.rx
            val ry = drum.radius.ry

            if (rx <= 0f || ry <= 0f) continue

            // 0. Transformasi rotasi balik jika kendang memiliki orientasi rotation
            val (localXu, localYu) = if (drum.rotation != 0f) {
                val rad = Math.toRadians(drum.rotation.toDouble())
                val cos = kotlin.math.cos(rad).toFloat()
                val sin = kotlin.math.sin(rad).toFloat()
                val dx = xu - cxu
                val dy = yu - cyu
                val rotDx = dx * cos + dy * sin
                val rotDy = -dx * sin + dy * cos
                Pair(cxu + rotDx, cyu + rotDy)
            } else {
                Pair(xu, yu)
            }

            // 1. Uji apakah berada di dalam oval/lingkaran kulit kendang:
            // ((X_u - CX_u) / rx)^2 + ((Y_u - CY_u) / ry)^2 <= 1.0
            val normDx = (localXu - cxu) / rx
            val normDy = (localYu - cyu) / ry
            val distanceSq = normDx * normDx + normDy * normDy

            if (distanceSq <= 1.0f) {
                // Berada di dalam kulit kendang, tentukan zona berdasarkan model
                val matchedZone = when (drum.model) {
                    "bass" -> findBassZone(drum, localXu, localYu, cxu, cyu, rx, ry)
                    "split" -> findSplitZone(drum, localXu, localYu, cxu, cyu, rx, ry)
                    "single" -> findSingleZone(drum)
                    else -> null
                }

                if (matchedZone != null) {
                    return HitResult(drum, matchedZone)
                }
            }
        }

        return null
    }

    /**
     * Evaluasi hit model bass:
     * - Jika hanya ada 1 zona di Bass, seluruh permukaan kendang memicu zona tersebut.
     * - C_div (busur oval pembatas): pusat (cx + dx*rx, cy + dy*ry), radius rx*rx dan ry*ry
     * - main (Bass-1): di dalam oval Bass DAN di dalam C_div
     * - crescent (zona tepi): di dalam oval Bass DAN di luar C_div, dibagi sesuai parameter splits
     */
    private fun findBassZone(
        drum: DrumData,
        xu: Float,
        yu: Float,
        cxu: Float,
        cyu: Float,
        rx: Float,
        ry: Float
    ): ZoneData? {
        if (drum.zones.size <= 1) {
            return drum.zones.firstOrNull()
        }

        val divider = drum.divider ?: return drum.zones.firstOrNull()

        val divCenterXu = cxu + (divider.dx * rx)
        val divCenterYu = cyu + (divider.dy * ry)
        val divRx = divider.effectiveRx * rx
        val divRy = divider.effectiveRy * ry

        val normX = if (divRx > 0f) (xu - divCenterXu) / divRx else 0f
        val normY = if (divRy > 0f) (yu - divCenterYu) / divRy else 0f
        val inDiv = (normX * normX + normY * normY) <= 1.0f

        return if (inDiv) {
            // Area utama (main / Bass-1)
            drum.zones.firstOrNull { it.region == "main" } ?: drum.zones.firstOrNull()
        } else {
            // Area bulan sabit (crescent)
            val crescentZones = drum.zones.filter { it.region == "crescent" }
            if (crescentZones.isEmpty()) {
                return drum.zones.firstOrNull()
            }
            if (crescentZones.size == 1) {
                return crescentZones.first()
            }

            val top = cyu - ry
            val totalHeight = 2.0f * ry
            val normalizedY = if (totalHeight > 0f) ((yu - top) / totalHeight).coerceIn(0f, 1f) else 0f

            val splits = divider.splits
            val bandIndex = if (splits != null && splits.isNotEmpty()) {
                var idx = 0
                for (s in splits) {
                    if (normalizedY >= s) {
                        idx++
                    } else {
                        break
                    }
                }
                idx.coerceIn(0, crescentZones.size - 1)
            } else {
                (normalizedY * crescentZones.size).toInt().coerceIn(0, crescentZones.size - 1)
            }

            crescentZones.getOrNull(bandIndex) ?: crescentZones.last()
        }
    }

    /**
     * Evaluasi hit model split / radial (Sedang, Kecil 2, dan kendang kustom 1..4 zona):
     * - Jika hanya 1 zona: langsung mengembalikan zona tersebut.
     * - Jika 2 zona default tanpa rotasi (angle == 0): upper (Y < cy), lower (Y >= cy).
     * - Jika dirotasi atau memiliki 3-4 zona: pembagian juring radial berdasarkan atan2 dan parameter angle.
     */
    private fun findSplitZone(
        drum: DrumData,
        xu: Float,
        yu: Float,
        cxu: Float,
        cyu: Float,
        rx: Float,
        ry: Float
    ): ZoneData? {
        if (drum.zones.isEmpty()) return null
        if (drum.zones.size == 1) return drum.zones.first()

        val angleOffset = drum.divider?.angle ?: 0f

        // Kompatibilitas mundur penuh untuk model split 2 zona horizontal standar tanpa rotasi
        if (drum.zones.size == 2 && angleOffset == 0f && drum.zones.any { it.region == "upper" }) {
            val targetRegion = if (yu < cyu) "upper" else "lower"
            return drum.zones.firstOrNull { it.region == targetRegion } ?: drum.zones.first()
        }

        // Pembagian juring radial (1/2, 1/3, 1/4 lingkaran)
        val normX = if (rx > 0f) (xu - cxu) / rx else 0f
        val normY = if (ry > 0f) (yu - cyu) / ry else 0f
        val rad = kotlin.math.atan2(normY.toDouble(), normX.toDouble())
        val deg = Math.toDegrees(rad).toFloat()
        val normalizedDeg = (deg + 360f) % 360f

        val relativeDeg = ((normalizedDeg - angleOffset) % 360f + 360f) % 360f
        val sliceDeg = 360f / drum.zones.size
        val index = (relativeDeg / sliceDeg).toInt().coerceIn(0, drum.zones.size - 1)

        return drum.zones.getOrNull(index) ?: drum.zones.first()
    }

    /**
     * Evaluasi hit model single (Kecil 1):
     * Langsung zona tunggal (region "full" atau zona pertama)
     */
    private fun findSingleZone(drum: DrumData): ZoneData? {
        return drum.zones.firstOrNull { it.region == "full" } ?: drum.zones.firstOrNull()
    }
}
