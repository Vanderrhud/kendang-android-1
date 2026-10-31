package com.vrkendang.view

import com.vrkendang.kitcore.model.DrumOverride
import com.vrkendang.kitcore.model.LayoutOverride
import com.vrkendang.kitcore.model.Point
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutOverrideStorageTest {

    @Test
    fun testSaveAtomicWritesValidFileAndNoTmpRemains() {
        val tempDir = File.createTempFile("layouts-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val storage = LayoutOverrideStorage(tempDir)
            val override = LayoutOverride(
                kitId = "new-metro",
                baseVersion = 1,
                drums = mapOf(
                    "bass" to DrumOverride(center = Point(0.3f, 0.6f), scale = 1.2f)
                )
            )

            val ok = storage.saveAtomic(override)
            assertTrue(ok, "Penyimpanan atomik harus berhasil")

            val targetFile = File(tempDir, "new-metro.json")
            assertTrue(targetFile.exists(), "Berkas new-metro.json harus ada")

            val tmpFile = File(tempDir, "new-metro.json.tmp")
            assertFalse(tmpFile.exists(), "Berkas sementara .tmp tidak boleh tersisa setelah simpan")

            val readOverride = storage.read("new-metro")
            assertNotNull(readOverride)
            assertEquals("new-metro", readOverride.kitId)
            val bassOverride = readOverride.drums["bass"]
            assertNotNull(bassOverride)
            assertEquals(0.3f, bassOverride.center?.x)
            assertEquals(1.2f, bassOverride.scale)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDeleteRemovesFile() {
        val tempDir = File.createTempFile("layouts-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val storage = LayoutOverrideStorage(tempDir)
            val targetFile = File(tempDir, "new-metro.json").apply {
                writeText("{}")
            }
            assertTrue(targetFile.exists())

            val deleted = storage.delete("new-metro")
            assertTrue(deleted)
            assertFalse(targetFile.exists(), "Berkas harus terhapus")
            assertNull(storage.read("new-metro"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
