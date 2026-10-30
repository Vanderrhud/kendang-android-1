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

            // 1. Uji apakah berada di dalam oval/lingkaran kulit kendang:
            // ((X_u - CX_u) / rx)^2 + ((Y_u - CY_u) / ry)^2 <= 1.0
            val normDx = (xu - cxu) / rx
            val normDy = (yu - cyu) / ry
            val distanceSq = normDx * normDx + normDy * normDy

            if (distanceSq <= 1.0f) {
                // Berada di dalam kulit kendang, tentukan zona berdasarkan model
                val matchedZone = when (drum.model) {
                    "bass" -> findBassZone(drum, xu, yu, cxu, cyu, rx, ry)
                    "split" -> findSplitZone(drum, yu, cyu)
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
     * - C_div (busur pembatas): pusat (cx + dx*rx, cy + dy*ry), radius r*rx
     * - main (Bass-1): di dalam oval DAN di dalam C_div
     * - crescent (Bass-2..5): di dalam oval DAN di luar C_div, dibagi 4 band vertikal (0-3 dari atas ke bawah)
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
        val divider = drum.divider ?: return drum.zones.firstOrNull()

        val divCenterXu = cxu + (divider.dx * rx)
        val divCenterYu = cyu + (divider.dy * ry)
        val divRadius = divider.r * rx

        val dDivX = xu - divCenterXu
        val dDivY = yu - divCenterYu
        val inDiv = (dDivX * dDivX + dDivY * dDivY) <= (divRadius * divRadius)

        return if (inDiv) {
            // Area utama (main / Bass-1)
            drum.zones.firstOrNull { it.region == "main" }
        } else {
            // Area bulan sabit (crescent / Bass-2..5)
            // Membagi tinggi oval Bass [cyu - ry, cyu + ry] menjadi 4 band vertikal
            val top = cyu - ry
            val totalHeight = 2.0f * ry
            val normalizedY = if (totalHeight > 0f) (yu - top) / totalHeight else 0f
            val bandIndex = (normalizedY * 4.0f).toInt().coerceIn(0, 3)

            drum.zones.firstOrNull { it.region == "crescent" && it.band == bandIndex }
        }
    }

    /**
     * Evaluasi hit model split (Sedang & Kecil 2):
     * upper bila Y_u < CY_u
     * lower bila Y_u >= CY_u (termasuk garis tengah Y_u == CY_u)
     */
    private fun findSplitZone(drum: DrumData, yu: Float, cyu: Float): ZoneData? {
        val targetRegion = if (yu < cyu) "upper" else "lower"
        return drum.zones.firstOrNull { it.region == targetRegion }
    }

    /**
     * Evaluasi hit model single (Kecil 1):
     * Langsung zona tunggal (region "full" atau zona pertama)
     */
    private fun findSingleZone(drum: DrumData): ZoneData? {
        return drum.zones.firstOrNull { it.region == "full" } ?: drum.zones.firstOrNull()
    }
}
