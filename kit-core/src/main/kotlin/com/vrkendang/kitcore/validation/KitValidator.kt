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
            // Center dan Radius (mendukung hingga 5x scale dan off-canvas drag dengan batas retensi)
            if (drum.center.x !in -3.0f..3.0f || drum.center.y !in -3.0f..3.0f) {
                errors.add("Koordinat center drum '${drum.id}' harus di rentang -3.0..3.0 (ditemukan: x=${drum.center.x}, y=${drum.center.y})")
            }
            if (drum.radius.rx <= 0.0f || drum.radius.rx > 5.0f || drum.radius.ry <= 0.0f || drum.radius.ry > 5.0f) {
                errors.add("Radius rx dan ry drum '${drum.id}' harus > 0.0 dan <= 5.0 (ditemukan: rx=${drum.radius.rx}, ry=${drum.radius.ry})")
            }

            // 3. Validasi Model dan Region
            when (drum.model) {
                "bass" -> {
                    if (drum.zones.isEmpty() || drum.zones.size > 5) {
                        errors.add("Drum model 'bass' ('${drum.id}') harus memiliki antara 1 sampai 5 zona (ditemukan: ${drum.zones.size})")
                    }
                    if (drum.divider == null && drum.zones.size > 1) {
                        errors.add("Drum model 'bass' ('${drum.id}') dengan lebih dari 1 zona wajib memiliki objek divider")
                    } else if (drum.divider != null) {
                        val div = drum.divider
                        if (div.dx !in -3.0f..3.0f || div.dy !in -3.0f..3.0f) {
                            errors.add("Parameter divider dx/dy drum '${drum.id}' harus di rentang -3.0..3.0 (ditemukan: dx=${div.dx}, dy=${div.dy})")
                        }
                        if (div.r <= 0.0f || div.r > 3.0f) {
                            errors.add("Parameter divider r drum '${drum.id}' harus > 0.0 dan <= 3.0 (ditemukan: r=${div.r})")
                        }
                        if (div.rx != null && (div.rx <= 0.0f || div.rx > 3.0f)) {
                            errors.add("Parameter divider rx drum '${drum.id}' harus > 0.0 dan <= 3.0 (ditemukan: rx=${div.rx})")
                        }
                        if (div.ry != null && (div.ry <= 0.0f || div.ry > 3.0f)) {
                            errors.add("Parameter divider ry drum '${drum.id}' harus > 0.0 dan <= 3.0 (ditemukan: ry=${div.ry})")
                        }
                        if (div.splits != null) {
                            for ((sIdx, sVal) in div.splits.withIndex()) {
                                if (sVal !in 0.0f..1.0f) {
                                    errors.add("Parameter divider split index $sIdx drum '${drum.id}' harus di rentang 0.0..1.0 (ditemukan: $sVal)")
                                }
                            }
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
                    if (drum.zones.isEmpty() || drum.zones.size > 4) {
                        errors.add("Drum model 'split' ('${drum.id}') harus memiliki antara 1 sampai 4 zona (ditemukan: ${drum.zones.size})")
                    }
                    for (zone in drum.zones) {
                        if (zone.region != "upper" && zone.region != "lower" && zone.region != "sector" && zone.region != "full") {
                            errors.add("Zona '${zone.id}' pada drum split harus memiliki region 'upper' atau 'lower' atau 'sector' (ditemukan: '${zone.region}')")
                        }
                    }
                    val div = drum.divider
                    if (div?.angle != null && (div.angle < 0.0f || div.angle > 360.0f)) {
                        errors.add("Parameter divider angle drum '${drum.id}' harus di rentang 0.0..360.0 (ditemukan: ${div.angle})")
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

            // 4. Validasi Image drum
            if (drum.image != null) {
                val img = drum.image
                if (img.startsWith("/") || img.startsWith("\\") || (img.length >= 2 && img[1] == ':')) {
                    errors.add("Path image drum '${drum.id}' tidak boleh absolut: '$img'")
                }
                if (img.contains("..")) {
                    errors.add("Path image drum '${drum.id}' tidak boleh mengandung '..': '$img'")
                }
                val expectedPath = "images/${drum.id}.png"
                if (img != expectedPath) {
                    errors.add("Path image drum '${drum.id}' harus persis '$expectedPath' (ditemukan: '$img')")
                }
            }

            // 5. Validasi Zona: Gain dan Sound path
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
