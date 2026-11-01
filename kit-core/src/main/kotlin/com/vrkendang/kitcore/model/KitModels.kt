package com.vrkendang.kitcore.model

import kotlinx.serialization.Serializable

@Serializable
data class Point(
    val x: Float,
    val y: Float
)

@Serializable
data class Radius(
    val rx: Float,
    val ry: Float
)

@Serializable
data class DividerData(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val r: Float = 0.80f,
    val rx: Float? = null,
    val ry: Float? = null,
    val splits: List<Float>? = null,
    val angle: Float? = null
) {
    val effectiveRx: Float get() = rx ?: r
    val effectiveRy: Float get() = ry ?: r
}

@Serializable
data class ZoneData(
    val id: String,
    val region: String,
    val band: Int? = null,
    val sound: String? = null,
    val gain: Float = 1.0f
)

@Serializable
data class DrumData(
    val id: String,
    val model: String,
    val center: Point,
    val radius: Radius,
    val divider: DividerData? = null,
    val zones: List<ZoneData>
)

@Serializable
data class KitData(
    val formatVersion: Int = 1,
    val kitId: String,
    val name: String,
    val version: Int = 1,
    val canvasAspect: Float = 2.0f,
    val drums: List<DrumData>
)

@Serializable
data class DrumOverride(
    val center: Point? = null,
    val scale: Float? = null
)

@Serializable
data class LayoutOverride(
    val kitId: String,
    val baseVersion: Int = 1,
    val drums: Map<String, DrumOverride> = emptyMap()
)
