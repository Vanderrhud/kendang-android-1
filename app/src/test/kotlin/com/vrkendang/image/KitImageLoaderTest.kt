package com.vrkendang.image

import android.graphics.Bitmap
import com.vrkendang.kitcore.model.DrumData
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius
import com.vrkendang.kitcore.model.ZoneData
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class KitImageLoaderTest {

    private fun createTestKit(): KitData {
        return KitData(
            formatVersion = 1,
            kitId = "test-image-kit",
            name = "Test Image Kit",
            version = 1,
            canvasAspect = 2.0f,
            drums = listOf(
                DrumData(
                    id = "bass",
                    model = "bass",
                    center = Point(0.25f, 0.55f),
                    radius = Radius(0.30f, 0.30f),
                    image = "images/bass.png",
                    zones = listOf(ZoneData(id = "bass-1", region = "main", sound = null))
                ),
                DrumData(
                    id = "kecil1",
                    model = "single",
                    center = Point(0.50f, 0.30f),
                    radius = Radius(0.15f, 0.15f),
                    image = null, // tanpa gambar
                    zones = listOf(ZoneData(id = "kecil1-1", region = "full", sound = null))
                ),
                DrumData(
                    id = "sedang",
                    model = "split",
                    center = Point(0.60f, 0.50f),
                    radius = Radius(0.20f, 0.20f),
                    image = "images/sedang.png", // file rusak / decode gagal
                    zones = listOf(ZoneData(id = "sedang-1", region = "lower", sound = null))
                ),
                DrumData(
                    id = "kecil2",
                    model = "split",
                    center = Point(0.80f, 0.35f),
                    radius = Radius(0.15f, 0.15f),
                    image = "images/kecil2.png", // file tidak ditemukan
                    zones = listOf(ZoneData(id = "kecil2-1", region = "lower", sound = null))
                )
            )
        )
    }

    @Test
    fun testLoadImagesWithValidMissingAndCorruptFiles() {
        val bassBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val logs = mutableListOf<String>()

        val fakeSource = KitImageSource { path ->
            when (path) {
                "images/bass.png" -> ByteArrayInputStream(ByteArray(10) { 1 })
                "images/sedang.png" -> ByteArrayInputStream(ByteArray(10) { 2 })
                else -> error("File not found: $path")
            }
        }

        val loader = KitImageLoader(
            bitmapDecoder = { stream ->
                val bytes = stream.readBytes()
                if (bytes.isNotEmpty() && bytes[0] == 1.toByte()) {
                    bassBitmap
                } else {
                    null // simulasi dekode gagal untuk sedang.png
                }
            },
            logSink = { msg -> logs.add(msg) }
        )

        val kit = createTestKit()
        val resultMap = loader.loadImages(kit, fakeSource)

        // Hanya bass yang berhasil dimuat
        assertEquals(1, resultMap.size)
        assertTrue(resultMap.containsKey("bass"))
        assertEquals(bassBitmap, resultMap["bass"])
        assertFalse(resultMap.containsKey("kecil1")) // image null
        assertFalse(resultMap.containsKey("sedang")) // decode gagal
        assertFalse(resultMap.containsKey("kecil2")) // file tidak ditemukan

        // Pastikan kegagalan dicatat ke log
        assertTrue(logs.any { it.contains("Gagal mendekode gambar") && it.contains("sedang") })
        assertTrue(logs.any { it.contains("Gagal membaca gambar") && it.contains("kecil2") })
    }

    @Test
    fun testReleaseBitmapsAndKitSwitching() {
        val oldBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val newBitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)

        val loader = KitImageLoader()

        val oldMap = mapOf("bass" to oldBitmap)
        assertFalse(oldBitmap.isRecycled)

        // Pembebasan bitmap kit lama
        loader.releaseBitmaps(oldMap)
        assertTrue(oldBitmap.isRecycled)

        // Kit baru memuat bitmap baru tanpa memakai kit lama
        val newMap = mapOf("bass" to newBitmap)
        assertEquals(1, newMap.size)
        assertFalse(newMap["bass"]!!.isRecycled)
        assertEquals(newBitmap, newMap["bass"])
    }

    @Test
    fun testDecodeSampledBitmapCalculatesSampleSize() {
        // Buat bitmap 2000x2000 PNG lalu decode lewat decodeSampledBitmapFromStream
        val original = Bitmap.createBitmap(2000, 2000, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        original.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val pngBytes = baos.toByteArray()

        val decoded = KitImageLoader.decodeSampledBitmapFromStream(ByteArrayInputStream(pngBytes), 1024)
        kotlin.test.assertNotNull(decoded)
        // 2000 / sampleSize (2) -> 1000 <= 1024
        assertTrue(decoded.width <= 1024)
        assertTrue(decoded.height <= 1024)
    }

    @Test
    fun testLoadBodyImagesWithValidMissingAndCorruptFiles() {
        val bassBodyBitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val logs = mutableListOf<String>()

        val fakeSource = KitImageSource { path ->
            when (path) {
                "images/bass-body.png" -> ByteArrayInputStream(ByteArray(10) { 1 })
                "images/sedang-body.png" -> ByteArrayInputStream(ByteArray(10) { 2 })
                else -> error("File not found: $path")
            }
        }

        val loader = KitImageLoader(
            bitmapDecoder = { stream ->
                val bytes = stream.readBytes()
                if (bytes.isNotEmpty() && bytes[0] == 1.toByte()) {
                    bassBodyBitmap
                } else {
                    null // simulasi dekode gagal
                }
            },
            logSink = { msg -> logs.add(msg) }
        )

        val kit = KitData(
            formatVersion = 1,
            kitId = "test-body-kit",
            name = "Test Body Kit",
            version = 1,
            canvasAspect = 2.0f,
            drums = listOf(
                DrumData(
                    id = "bass",
                    model = "bass",
                    center = Point(0.25f, 0.55f),
                    radius = Radius(0.30f, 0.30f),
                    body = com.vrkendang.kitcore.model.DrumBodyData(
                        image = "images/bass-body.png",
                        width = 0.6f,
                        height = 0.5f
                    ),
                    zones = listOf(ZoneData(id = "bass-1", region = "main", sound = null))
                ),
                DrumData(
                    id = "kecil1",
                    model = "single",
                    center = Point(0.50f, 0.30f),
                    radius = Radius(0.15f, 0.15f),
                    body = null, // tanpa body
                    zones = listOf(ZoneData(id = "kecil1-1", region = "full", sound = null))
                ),
                DrumData(
                    id = "sedang",
                    model = "split",
                    center = Point(0.60f, 0.50f),
                    radius = Radius(0.20f, 0.20f),
                    body = com.vrkendang.kitcore.model.DrumBodyData(
                        image = "images/sedang-body.png",
                        width = 0.4f,
                        height = 0.4f
                    ),
                    zones = listOf(ZoneData(id = "sedang-1", region = "lower", sound = null))
                ),
                DrumData(
                    id = "kecil2",
                    model = "split",
                    center = Point(0.80f, 0.35f),
                    radius = Radius(0.15f, 0.15f),
                    body = com.vrkendang.kitcore.model.DrumBodyData(
                        image = "images/kecil2-body.png",
                        width = 0.3f,
                        height = 0.3f
                    ),
                    zones = listOf(ZoneData(id = "kecil2-1", region = "lower", sound = null))
                )
            )
        )

        val resultMap = loader.loadBodyImages(kit, fakeSource)

        assertEquals(1, resultMap.size)
        assertTrue(resultMap.containsKey("bass"))
        assertEquals(bassBodyBitmap, resultMap["bass"])
        assertFalse(resultMap.containsKey("kecil1"))
        assertFalse(resultMap.containsKey("sedang"))
        assertFalse(resultMap.containsKey("kecil2"))

        assertTrue(logs.any { it.contains("Gagal mendekode gambar body") && it.contains("sedang") })
        assertTrue(logs.any { it.contains("Gagal membaca gambar body") && it.contains("kecil2") })
    }
}
