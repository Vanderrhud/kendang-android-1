package com.vrkendang.kitcore.validation

import com.vrkendang.kitcore.model.KitData

/**
 * Validator integritas data KitData sesuai spesifikasi dan aturan desain.
 */
object KitValidator {

    private val KIT_ID_REGEX = Regex("^[a-z0-9-]+$")

    /**
     * Memvalidasi KitData dan mengembalikan daftar pesan kesalahan jika ditemukan pelanggaran.
     * Mengembalikan list kosong jika kit valid.
     */
    fun validate(kit: KitData): List<String> {
        val errors = mutableListOf<String>()

        // 1. Validasi Identitas
        if (kit.formatVersion != 1) {
            errors.add("formatVersion harus 1 (ditemukan: ${kit.formatVersion})")
        }
        if (!KIT_ID_REGEX.matches(kit.kitId)) {
            errors.add("kitId hanya boleh memuat huruf kecil, angka, dan tanda hubung: '${kit.kitId}'")
        }
        if (kit.name.isBlank()) {
            errors.add("name tidak boleh kosong")
        }
        if (kit.drums.isEmpty()) {
            errors.add("drums tidak boleh kosong")
        }

        // Keunikan ID drum
        val drumIds = mutableSetOf<String>()
        for (drum in kit.drums) {
            if (!drumIds.add(drum.id)) {
                errors.add("Duplikasi id drum ditemukan: '${drum.id}'")
            }
        }

        // Keunikan ID zona
        val zoneIds = mutableSetOf<String>()
        for (drum in kit.drums) {
            for (zone in drum.zones) {
                if (!zoneIds.add(zone.id)) {
                    errors.add("Duplikasi id zona ditemukan: '${zone.id}'")
                }
            }
        }

        // 2. Validasi Geometri Kanvas
        if (kit.canvasAspect <= 0.0f) {
            errors.add("canvasAspect harus > 0 (ditemukan: ${kit.canvasAspect})")
        }

        for (drum in kit.drums) {
            // Center dan Radius
            if (drum.center.x !in 0.0f..1.0f || drum.center.y !in 0.0f..1.0f) {
                errors.add("Koordinat center drum '${drum.id}' harus di rentang 0.0..1.0 (ditemukan: x=${drum.center.x}, y=${drum.center.y})")
            }
            if (drum.radius.rx <= 0.0f || drum.radius.rx > 1.0f || drum.radius.ry <= 0.0f || drum.radius.ry > 1.0f) {
                errors.add("Radius rx dan ry drum '${drum.id}' harus > 0.0 dan <= 1.0 (ditemukan: rx=${drum.radius.rx}, ry=${drum.radius.ry})")
            }

            // 3. Validasi Model dan Region
            when (drum.model) {
                "bass" -> {
                    if (drum.divider == null) {
                        errors.add("Drum model 'bass' ('${drum.id}') wajib memiliki objek divider")
                    } else {
                        val div = drum.divider
                        if (div.dx !in -3.0f..3.0f || div.dy !in -3.0f..3.0f) {
                            errors.add("Parameter divider dx/dy drum '${drum.id}' harus di rentang -3.0..3.0 (ditemukan: dx=${div.dx}, dy=${div.dy})")
                        }
                        if (div.r <= 0.0f || div.r > 3.0f) {
                            errors.add("Parameter divider r drum '${drum.id}' harus > 0.0 dan <= 3.0 (ditemukan: r=${div.r})")
                        }
                    }
                    for (zone in drum.zones) {
                        if (zone.region != "main" && zone.region != "crescent") {
                            errors.add("Zona '${zone.id}' pada drum bass harus memiliki region 'main' atau 'crescent' (ditemukan: '${zone.region}')")
                        }
                        if (zone.region == "crescent") {
                            if (zone.band == null || zone.band !in 0..3) {
                                errors.add("Zona crescent '${zone.id}' wajib memiliki band di rentang 0..3 (ditemukan: ${zone.band})")
                            }
                        }
                    }
                }
                "split" -> {
                    for (zone in drum.zones) {
                        if (zone.region != "upper" && zone.region != "lower") {
                            errors.add("Zona '${zone.id}' pada drum split harus memiliki region 'upper' atau 'lower' (ditemukan: '${zone.region}')")
                        }
                    }
                }
                "single" -> {
                    for (zone in drum.zones) {
                        if (zone.region != "full") {
                            errors.add("Zona '${zone.id}' pada drum single harus memiliki region 'full' (ditemukan: '${zone.region}')")
                        }
                    }
                }
                else -> {
                    errors.add("Model drum tidak dikenal: '${drum.model}' pada drum '${drum.id}'")
                }
            }

            // 4. Validasi Zona: Gain dan Sound path
            for (zone in drum.zones) {
                if (zone.gain !in 0.0f..2.0f) {
                    errors.add("Nilai gain zona '${zone.id}' harus di rentang 0.0..2.0 (ditemukan: ${zone.gain})")
                }
                if (zone.sound != null) {
                    val s = zone.sound
                    if (s.startsWith("/") || s.startsWith("\\") || (s.length >= 2 && s[1] == ':')) {
                        errors.add("Path sound zona '${zone.id}' tidak boleh absolut: '$s'")
                    }
                    if (s.contains("..")) {
                        errors.add("Path sound zona '${zone.id}' tidak boleh mengandung '..': '$s'")
                    }
                }
            }
        }

        return errors
    }

    /**
     * Memvalidasi KitData dan melempar IllegalArgumentException jika terdapat kesalahan.
     */
    fun validateOrThrow(kit: KitData) {
        val errors = validate(kit)
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("Validasi kit gagal:\n" + errors.joinToString("\n - ", prefix = " - "))
        }
    }
}
