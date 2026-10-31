package com.vrkendang.repository

import com.vrkendang.kitcore.factory.KitFactory
import com.vrkendang.kitcore.model.KitData
import com.vrkendang.kitcore.parser.KitJsonParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit test untuk [KitRepository].
 *
 * [FakeAssetSource] meniru `AssetManager.list()`/`open()` di JVM sehingga test
 * dapat memverifikasi daftar kit, log kit invalid, deduplikasi, dan fallback
 * tanpa Android runtime.
 */
class KitRepositoryTest {

    // -------------------------------------------------------------------------
    // Fake AssetManager
    // -------------------------------------------------------------------------

    private class FakeAssetSource(
        private val files: Map<String, String>
    ) : KitAssetSource {

        override fun list(path: String): Array<String> {
            val prefix = if (path.endsWith("/")) path else "$path/"
            return files.keys
                .filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix).substringBefore('/') }
                .distinct()
                .toTypedArray()
        }

        override fun open(path: String): InputStream {
            val content = files[path]
                ?: throw IllegalArgumentException("Aset tidak ditemukan: $path")
            return ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private fun newMetroKit(): KitData =
        KitFactory.createFromTemplate("template-kit", "New Metro")
            .copy(kitId = KitRepository.DEFAULT_KIT_ID)

    private fun alphaKit(): KitData =
        KitFactory.createFromTemplate("alpha", "Alpha")

    private fun json(kit: KitData): String = KitJsonParser.stringify(kit)

    // -------------------------------------------------------------------------
    // Test: daftar kit valid + layout override
    // -------------------------------------------------------------------------

    @Test
    fun `daftar kit memuat kit valid dan menerapkan layout override pemain`() {
        val assets = FakeAssetSource(
            mapOf(
                "kits/new-metro/kit.json" to json(newMetroKit()),
                "kits/alpha/kit.json" to json(alphaKit())
            )
        )
        val overrideJson = """
            {
              "kitId": "new-metro",
              "baseVersion": 1,
              "drums": {
                "bass": {
                  "center": { "x": 0.28, "y": 0.55 },
                  "scale": 1.1
                }
              }
            }
        """.trimIndent()

        val repository = KitRepository(
            assetSource = assets,
            layoutOverrideSource = LayoutOverrideSource { kitId ->
                if (kitId == "new-metro") overrideJson else null
            },
            logSink = {}
        )

        val kits = repository.loadKits()

        assertEquals(listOf("alpha", "new-metro"), kits.map { it.kitId })
        val metro = kits.first { it.kitId == "new-metro" }
        val bass = metro.drums.first { it.id == "bass" }
        assertEquals(0.28f, bass.center.x, 0.0001f)
        assertEquals(0.55f, bass.center.y, 0.0001f)
        assertEquals(0.30f * 1.1f, bass.radius.rx, 0.0001f)
        assertEquals(0.30f * 1.1f, bass.radius.ry, 0.0001f)
    }

    @Test
    fun `layout override rusak diabaikan dan tata letak bawaan dipakai`() {
        val base = newMetroKit()
        val repository = KitRepository(
            assetSource = FakeAssetSource(mapOf("kits/new-metro/kit.json" to json(base))),
            layoutOverrideSource = LayoutOverrideSource { "{ bukan json" },
            logSink = {}
        )

        val loaded = repository.loadKits().single()

        assertEquals(base, loaded)
    }

    // -------------------------------------------------------------------------
    // Test: kit invalid dilewati, duplikasi ditolak, dev-kit disembunyikan
    // -------------------------------------------------------------------------

    @Test
    fun `kit rusak dilewati dan dicatat ke log`() {
        val logs = mutableListOf<String>()
        val repository = KitRepository(
            assetSource = FakeAssetSource(
                mapOf(
                    "kits/broken/kit.json" to "{ json rusak",
                    "kits/new-metro/kit.json" to json(newMetroKit())
                )
            ),
            logSink = { message -> logs.add(message) }
        )

        val kits = repository.loadKits()

        assertEquals(listOf("new-metro"), kits.map { it.kitId })
        assertTrue(
            logs.any { it.contains("broken") && it.contains("dilewati") },
            "Log harus mencatat kit rusak, actual: $logs"
        )
    }

    @Test
    fun `kitId duplikat ditolak dan hanya kit pertama yang didaftarkan`() {
        val logs = mutableListOf<String>()
        val duplicate = newMetroKit()
        val repository = KitRepository(
            assetSource = FakeAssetSource(
                mapOf(
                    "kits/a-first/kit.json" to json(duplicate),
                    "kits/b-second/kit.json" to json(duplicate)
                )
            ),
            logSink = { message -> logs.add(message) }
        )

        val kits = repository.loadKits()

        assertEquals(1, kits.size)
        assertEquals("new-metro", kits.single().kitId)
        assertTrue(
            logs.any { it.contains("duplikat") },
            "Log harus mencatat penolakan kitId duplikat, actual: $logs"
        )
    }

    @Test
    fun `folder kerja developer dev-kit tidak muncul di daftar kit pemain`() {
        val logs = mutableListOf<String>()
        val repository = KitRepository(
            assetSource = FakeAssetSource(
                mapOf(
                    "kits/new-metro/kit.json" to json(newMetroKit()),
                    "kits/dev-kit/kit.json" to json(alphaKit())
                )
            ),
            logSink = { message -> logs.add(message) }
        )

        val kits = repository.loadKits()

        assertEquals(listOf("new-metro"), kits.map { it.kitId })
        assertTrue(kits.none { it.kitId == "alpha" })
        assertTrue(logs.any { it.contains(KitRepository.DEV_KIT_DIR_NAME) })
    }

    // -------------------------------------------------------------------------
    // Test: fallback kit
    // -------------------------------------------------------------------------

    @Test
    fun `kit terpilih tidak valid fallback ke kit sebelumnya yang aktif`() {
        val logs = mutableListOf<String>()
        val repository = KitRepository(
            assetSource = FakeAssetSource(
                mapOf(
                    "kits/alpha/kit.json" to json(alphaKit()),
                    "kits/broken/kit.json" to "{ rusak",
                    "kits/new-metro/kit.json" to json(newMetroKit())
                )
            ),
            logSink = { message -> logs.add(message) }
        )

        val first = repository.selectKit("alpha")
        val fallback = repository.selectKit("broken")

        assertEquals("alpha", first.kitId)
        assertEquals("alpha", fallback.kitId)
        assertEquals("alpha", repository.activeKit?.kitId)
        assertTrue(
            logs.any { it.contains("fallback") && it.contains("alpha") },
            "Log harus mencatat fallback, actual: $logs"
        )
    }

    @Test
    fun `tanpa kit aktif, kit tidak valid fallback ke new-metro`() {
        val repository = KitRepository(
            assetSource = FakeAssetSource(
                mapOf(
                    "kits/alpha/kit.json" to json(alphaKit()),
                    "kits/new-metro/kit.json" to json(newMetroKit())
                )
            ),
            logSink = {}
        )

        val selected = repository.selectKit("tidak-ada")

        assertEquals("new-metro", selected.kitId)
        assertEquals("new-metro", repository.activeKit?.kitId)
    }
}
