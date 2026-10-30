package com.vrkendang.kitcore.parser

import com.vrkendang.kitcore.model.KitData
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Parser untuk file kit.json menggunakan kotlinx.serialization.
 * Mengurai format data kit dan mendukung zona unassigned (sound: null).
 */
object KitJsonParser {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    /**
     * Mem-parsing string JSON menjadi KitData.
     * Melempar IllegalArgumentException dengan pesan informatif jika format rusak atau field wajib hilang.
     */
    fun parse(jsonString: String): KitData {
        try {
            return json.decodeFromString<KitData>(jsonString)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Gagal mengurai kit.json: format JSON tidak valid atau field wajib hilang (${e.message})", e)
        } catch (e: Exception) {
            throw IllegalArgumentException("Gagal mengurai kit.json: ${e.message}", e)
        }
    }

    /**
     * Mengubah KitData menjadi string JSON dengan format rapi (pretty print).
     */
    fun stringify(kit: KitData): String {
        return prettyJson.encodeToString(KitData.serializer(), kit)
    }
}
