package com.vrkendang.kitcore.model

import kotlinx.serialization.Serializable

/**
 * Kelas contoh untuk memverifikasi plugin kotlinx-serialization di modul :kit-core.
 */
@Serializable
data class SampleSerializable(
    val id: String,
    val value: Int
)
