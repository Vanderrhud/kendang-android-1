package com.vrkendang.kitauthoring

import com.vrkendang.kitcore.model.Point
import com.vrkendang.kitcore.model.Radius
import com.vrkendang.kitcore.packageio.KitPackageReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DevKitAuthoringSessionTest {

    private fun buildDummyWav(): ByteArray {
        val pcmBytes = ByteArray(100)
        val fmtSize = 16
        val riffSize = 4 + (8 + fmtSize) + (8 + pcmBytes.size)
        val buffer = ByteBuffer.allocate(8 + riffSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(riffSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(fmtSize)
        buffer.putShort(1.toShort()) // PCM
        buffer.putShort(1.toShort()) // Mono
        buffer.putInt(44100)
        buffer.putInt(44100 * 2)
        buffer.putShort(2.toShort())
        buffer.putShort(16.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmBytes.size)
        buffer.put(pcmBytes)

        return buffer.array()
    }

    @Test
    fun testCreateNewKitWithValidIdAndTemplate() {
        val tempDir = File.createTempFile("dev-session-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val session = DevKitAuthoringSession(tempDir)
            val newKit = session.createNewKit("kendang-koplo", "Kendang Koplo", null)

            assertEquals("kendang-koplo", newKit.kitId)
            assertEquals("Kendang Koplo", newKit.name)
            assertEquals(1, newKit.version)
            assertEquals(4, newKit.drums.size)

            // Semua zona awal harus null
            assertTrue(newKit.drums.all { drum -> drum.zones.all { it.sound == null } })

            val kitJsonFile = File(tempDir, "kit.json")
            assertTrue(kitJsonFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCreateNewKitRejectsBuiltInDuplicateId() {
        val tempDir = File.createTempFile("dev-session-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val session = DevKitAuthoringSession(tempDir)
            assertFailsWith<IllegalArgumentException> {
                session.createNewKit("new-metro", "Duplikat New Metro", null)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMoveDrumOrderChangesHitTestPriority() {
        val tempDir = File.createTempFile("dev-session-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val session = DevKitAuthoringSession(tempDir)
            val kit = session.createNewKit("test-order", "Test Order", null)
            assertEquals(listOf("bass", "kecil1", "sedang", "kecil2"), kit.drums.map { it.id })

            // Geser Bass (indeks 0) ke bawah (indeks 1)
            val updated = session.moveDrumOrder(fromIndex = 0, toIndex = 1)
            assertEquals(listOf("kecil1", "bass", "sedang", "kecil2"), updated.drums.map { it.id })
            assertTrue(session.hasUnsavedChanges)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUpdateBassDividerValidation() {
        val tempDir = File.createTempFile("dev-session-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-div", "Test Divider", null)

            // Valid divider
            val okKit = session.updateBassDivider(dx = -0.5f, dy = 0.2f, r = 1.1f)
            val bassDivider = okKit.drums.first { it.id == "bass" }.divider
            assertNotNull(bassDivider)
            assertEquals(-0.5f, bassDivider.dx)
            assertEquals(0.2f, bassDivider.dy)
            assertEquals(1.1f, bassDivider.r)

            // Invalid dx > 3.0
            assertFailsWith<IllegalArgumentException> {
                session.updateBassDivider(dx = 3.5f, dy = 0.0f, r = 1.0f)
            }

            // Invalid r <= 0
            assertFailsWith<IllegalArgumentException> {
                session.updateBassDivider(dx = 0.0f, dy = 0.0f, r = 0.0f)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUpdateZoneGainClamped() {
        val tempDir = File.createTempFile("dev-session-test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-gain", "Test Gain", null)

            val updated = session.updateZoneGain("bass-1", 2.5f) // > 2.0 -> dijepit ke 2.0
            val gain = updated.drums.first { it.id == "bass" }.zones.first { it.id == "bass-1" }.gain
            assertEquals(2.0f, gain)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSaveCurrentEditAndSaveAsNewInternalKit() {
        val tempDevDir = File.createTempFile("dev-session-internal", "").apply { delete(); mkdirs() }
        val tempInternalKitsDir = File.createTempFile("internal-kits-dir", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDevDir)
            session.createNewKit("kit-awal", "Kit Awal", null)
            session.updateZoneGain("bass-1", 1.4f)
            assertTrue(session.hasUnsavedChanges)

            // 1. Simpan menimpa kit saat ini di penyimpanan internal aplikasi
            val savedCurrent = session.saveCurrentEditToInternalStorage(tempInternalKitsDir)
            assertEquals("kit-awal", savedCurrent.kitId)
            assertEquals(2, savedCurrent.version)
            assertFalse(session.hasUnsavedChanges)
            assertTrue(File(tempInternalKitsDir, "kit-awal/kit.json").isFile)

            // 2. Simpan menjadi kit baru di penyimpanan internal aplikasi
            val savedNew = session.saveAsNewInternalKit("kit-baru-koplo", "Kit Baru Koplo", tempInternalKitsDir)
            assertEquals("kit-baru-koplo", savedNew.kitId)
            assertEquals("Kit Baru Koplo", savedNew.name)
            assertEquals(1, savedNew.version)
            assertFalse(session.hasUnsavedChanges)
            assertTrue(File(tempInternalKitsDir, "kit-baru-koplo/kit.json").isFile)
        } finally {
            tempDevDir.deleteRecursively()
            tempInternalKitsDir.deleteRecursively()
        }
    }

    @Test
    fun testSetBassZoneCountAndOvalDivider() {
        val tempDir = File.createTempFile("dev-session-bass-zones", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-bass-zones", "Test Bass Zones", null)

            // 1. Ubah jumlah zona Bass ke 3 zona
            val kit3 = session.setBassZoneCount(3)
            val bass3 = kit3.drums.first { it.id == "bass" }
            assertEquals(3, bass3.zones.size)
            assertEquals(listOf("bass-1", "bass-2", "bass-3"), bass3.zones.map { it.id })

            // 2. Ubah parameter oval Bass
            val kitOval = session.updateBassDivider(
                dx = -0.2f,
                dy = 0.1f,
                r = 0.7f,
                rx = 0.6f,
                ry = 0.9f
            )
            val div = kitOval.drums.first { it.id == "bass" }.divider
            assertNotNull(div)
            assertEquals(-0.2f, div.dx)
            assertEquals(0.1f, div.dy)
            assertEquals(0.6f, div.effectiveRx)
            assertEquals(0.9f, div.effectiveRy)

            // 3. Ubah posisi sekat (untuk 3 zona, crescentCount=2, splits punya 1 entri pada index 0)
            val kitSplit = session.updateBassSplit(0, 0.40f)
            val divSplit = kitSplit.drums.first { it.id == "bass" }.divider
            assertNotNull(divSplit)
            assertEquals(listOf(0.40f), divSplit.splits)

            // 4. Ubah jumlah zona Bass ke 1 zona
            val kit1 = session.setBassZoneCount(1)
            val bass1 = kit1.drums.first { it.id == "bass" }
            assertEquals(1, bass1.zones.size)
            assertEquals("bass-1", bass1.zones.first().id)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAddAndDeleteDrumAndRadialConfiguration() {
        val tempDir = File.createTempFile("dev-session-drums", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-custom-drums", "Test Custom Drums", null)

            // 1. Tambah kendang baru dengan 3 zona
            val (kitWithNew, newDrum) = session.addDrum("Kecil 3", 3)
            assertEquals("kecil-3", newDrum.id)
            assertEquals(3, newDrum.zones.size)
            assertEquals("split", newDrum.model)
            assertEquals(5, kitWithNew.drums.size) // bass, kecil1, sedang, kecil2, kecil-3
            assertTrue(session.hasUnsavedChanges)

            // 2. Ubah putaran sudut pada kendang baru
            val kitRotated = session.updateDrumAngle("kecil-3", 75.0f)
            val rotatedDrum = kitRotated.drums.first { it.id == "kecil-3" }
            assertEquals(75.0f, rotatedDrum.divider?.angle)

            // 3. Ubah jumlah zona kendang bawaan (kecil1 dari 1 zona -> 4 zona)
            val kitKecil4 = session.setDrumZoneCount("kecil1", 4)
            val updatedKecil1 = kitKecil4.drums.first { it.id == "kecil1" }
            assertEquals(4, updatedKecil1.zones.size)
            assertEquals("split", updatedKecil1.model)

            // 4. Ubah kembali ke 1 zona
            val kitKecil1 = session.setDrumZoneCount("kecil1", 1)
            val singleKecil1 = kitKecil1.drums.first { it.id == "kecil1" }
            assertEquals(1, singleKecil1.zones.size)
            assertEquals("single", singleKecil1.model)

            // 5. Hapus kendang yang baru ditambahkan
            val kitAfterDelete = session.deleteDrum("kecil-3")
            assertEquals(4, kitAfterDelete.drums.size)
            assertFalse(kitAfterDelete.drums.any { it.id == "kecil-3" })

            // 6. Proteksi Bass tidak boleh dihapus
            assertFailsWith<IllegalArgumentException> {
                session.deleteDrum("bass")
            }

            // 7. Seluruh perubahan valid menurut KitValidator
            com.vrkendang.kitcore.validation.KitValidator.validateOrThrow(kitAfterDelete)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBringDrumToFrontAndPositionSizeAdjustment() {
        val tempDir = File.createTempFile("dev-session-front", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            val kit = session.createNewKit("test-front", "Test Front", null)
            assertEquals(listOf("bass", "kecil1", "sedang", "kecil2"), kit.drums.map { it.id })

            // 1. Pindahkan 'sedang' (indeks 2) ke posisi teratas (indeks 0)
            val kitFront = session.bringDrumToFront("sedang")
            assertEquals(listOf("sedang", "bass", "kecil1", "kecil2"), kitFront.drums.map { it.id })
            assertTrue(session.hasUnsavedChanges)

            // 2. Pindahkan kendang yang sudah di indeks 0 ('sedang') -> tidak berubah
            val kitSame = session.bringDrumToFront("sedang")
            assertEquals(listOf("sedang", "bass", "kecil1", "kecil2"), kitSame.drums.map { it.id })

            // 3. Pindahkan 'kecil2' ke posisi teratas
            val kitKecil2Front = session.bringDrumToFront("kecil2")
            assertEquals(listOf("kecil2", "sedang", "bass", "kecil1"), kitKecil2Front.drums.map { it.id })

            // 4. Ubah posisi dan ukuran kendang teratas
            val kitMoved = session.updateDrumCenter("kecil2", Point(0.65f, 0.45f))
            assertEquals(Point(0.65f, 0.45f), kitMoved.drums.first { it.id == "kecil2" }.center)

            val kitResized = session.updateDrumRadius("kecil2", Radius(0.25f, 0.25f))
            assertEquals(Radius(0.25f, 0.25f), kitResized.drums.first { it.id == "kecil2" }.radius)

            com.vrkendang.kitcore.validation.KitValidator.validateOrThrow(kitResized)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun buildMinimalPng(): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        ))
        val dos = java.io.DataOutputStream(baos)
        dos.writeInt(13)
        dos.write("IHDR".toByteArray(Charsets.US_ASCII))
        dos.writeInt(128)
        dos.writeInt(128)
        dos.writeByte(8)
        dos.writeByte(6)
        dos.writeByte(0)
        dos.writeByte(0)
        dos.writeByte(0)
        dos.writeInt(0)
        dos.flush()
        return baos.toByteArray()
    }

    @Test
    fun testDeleteDrumRemovesPngImage() {
        val tempDir = File.createTempFile("dev-session-del-img", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-del-img", "Test Delete Image", null)
            val (_, newDrum) = session.addDrum("Kecil 3", 1)

            val pngFile = File(session.imagesDir, "${newDrum.id}.png").apply { writeBytes(buildMinimalPng()) }
            assertTrue(pngFile.exists())

            session.deleteDrum(newDrum.id)
            assertFalse(pngFile.exists(), "File PNG harus otomatis terhapus saat kendang dihapus")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRenameDrumIdMovesPngFileAndUpdatesImage() {
        val tempDir = File.createTempFile("dev-session-rename-img", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-rename-img", "Test Rename Image", null)
            val (_, newDrum) = session.addDrum("Kecil 3", 1)
            val oldId = newDrum.id

            File(session.imagesDir, "$oldId.png").writeBytes(buildMinimalPng())

            // Pasang image path di model
            val kitWithImage = session.workingKit!!.copy(
                drums = session.workingKit!!.drums.map {
                    if (it.id == oldId) it.copy(image = "images/$oldId.png") else it
                }
            )
            val targetFile = File(tempDir, "kit.json")
            targetFile.writeText(com.vrkendang.kitcore.parser.KitJsonParser.stringify(kitWithImage))
            session.loadExistingWorkingCopy()

            val updated = session.renameDrumId(oldId, "ketipung-kanan")
            assertFalse(File(session.imagesDir, "$oldId.png").exists(), "File lama harus dipindahkan")
            assertTrue(File(session.imagesDir, "ketipung-kanan.png").exists(), "File baru harus ada dengan nama ID baru")

            val renamedDrum = updated.drums.first { it.id == "ketipung-kanan" }
            assertEquals("images/ketipung-kanan.png", renamedDrum.image)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testInternalSaveAndReloadPreservesImages() {
        val tempDir = File.createTempFile("dev-session-save-img", "").apply { delete(); mkdirs() }
        val internalDir = File.createTempFile("dev-session-internal-img", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            val kit = session.createNewKit("test-save-img", "Test Save Image", null)

            File(session.imagesDir, "bass.png").writeBytes(buildMinimalPng())
            val kitWithImage = kit.copy(
                drums = kit.drums.map {
                    if (it.id == "bass") it.copy(image = "images/bass.png") else it
                }
            )
            File(tempDir, "kit.json").writeText(com.vrkendang.kitcore.parser.KitJsonParser.stringify(kitWithImage))
            session.loadExistingWorkingCopy()

            session.saveCurrentEditToInternalStorage(internalDir)

            val savedImageFile = File(internalDir, "test-save-img/images/bass.png")
            assertTrue(savedImageFile.exists(), "Gambar harus ikut tersimpan di internal storage")

            // Bersihkan devKitDir dan muat ulang dari internal
            session.imagesDir.deleteRecursively()
            val fakeAssetManager = org.robolectric.RuntimeEnvironment.getApplication().assets
            val reloaded = session.openActiveKitAsWorkingCopy("test-save-img", internalDir, fakeAssetManager)

            assertEquals("images/bass.png", reloaded.drums.first { it.id == "bass" }.image)
            assertTrue(File(session.imagesDir, "bass.png").exists(), "Gambar harus dipulihkan ke salinan kerja")
        } finally {
            tempDir.deleteRecursively()
            internalDir.deleteRecursively()
        }
    }
}
