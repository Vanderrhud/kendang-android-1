package com.vrkendang.kitcore.factory

import com.vrkendang.kitcore.model.*
import com.vrkendang.kitcore.validation.KitValidator

/**
 * Pabrik kit untuk membuat kit baru dari template bawaan.
 */
object KitFactory {

    val BUILT_IN_KIT_IDS = setOf("new-metro")

    /**
     * Membuat kit baru dari template (tata letak New Metro) dengan seluruh zona sound: null.
     * Menolak jika kitId sama dengan kit bawaan yang dilindungi.
     */
    fun createFromTemplate(kitId: String, name: String): KitData {
        val normalizedKitId = kitId.trim().lowercase()
        if (BUILT_IN_KIT_IDS.contains(normalizedKitId)) {
            throw IllegalArgumentException("kitId '$kitId' tidak boleh sama dengan kit bawaan ('new-metro')")
        }

        val newKit = KitData(
            formatVersion = 1,
            kitId = normalizedKitId,
            name = name.trim(),
            version = 1,
            canvasAspect = 2.0f,
            drums = listOf(
                DrumData(
                    id = "bass",
                    model = "bass",
                    center = Point(0.25f, 0.55f),
                    radius = Radius(0.30f, 0.30f),
                    divider = DividerData(-0.30f, 0.0f, 0.80f),
                    zones = listOf(
                        ZoneData(id = "bass-1", region = "main", sound = null, gain = 1.0f),
                        ZoneData(id = "bass-2", region = "crescent", band = 0, sound = null, gain = 1.0f),
                        ZoneData(id = "bass-3", region = "crescent", band = 1, sound = null, gain = 1.0f),
                        ZoneData(id = "bass-4", region = "crescent", band = 2, sound = null, gain = 1.0f),
                        ZoneData(id = "bass-5", region = "crescent", band = 3, sound = null, gain = 1.0f)
                    )
                ),
                DrumData(
                    id = "kecil1",
                    model = "single",
                    center = Point(0.52f, 0.35f),
                    radius = Radius(0.17f, 0.17f),
                    zones = listOf(
                        ZoneData(id = "kecil1-1", region = "full", sound = null, gain = 1.0f)
                    )
                ),
                DrumData(
                    id = "sedang",
                    model = "split",
                    center = Point(0.66f, 0.60f),
                    radius = Radius(0.20f, 0.20f),
                    zones = listOf(
                        ZoneData(id = "sedang-1", region = "lower", sound = null, gain = 1.0f),
                        ZoneData(id = "sedang-2", region = "upper", sound = null, gain = 1.0f)
                    )
                ),
                DrumData(
                    id = "kecil2",
                    model = "split",
                    center = Point(0.82f, 0.38f),
                    radius = Radius(0.17f, 0.17f),
                    zones = listOf(
                        ZoneData(id = "kecil2-1", region = "lower", sound = null, gain = 1.0f),
                        ZoneData(id = "kecil2-2", region = "upper", sound = null, gain = 1.0f)
                    )
                )
            )
        )

        // Validasi agar kit yang dihasilkan terjamin valid
        KitValidator.validateOrThrow(newKit)
        return newKit
    }
}
