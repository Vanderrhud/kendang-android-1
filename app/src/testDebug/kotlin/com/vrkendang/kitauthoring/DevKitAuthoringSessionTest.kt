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
            val reloaded = session.openActiveKitAsWorkingCopy("test-save-img", internalDir)

            assertEquals("images/bass.png", reloaded.drums.first { it.id == "bass" }.image)
            assertTrue(File(session.imagesDir, "bass.png").exists(), "Gambar harus dipulihkan ke salinan kerja")
        } finally {
            tempDir.deleteRecursively()
            internalDir.deleteRecursively()
        }
    }

    @Test
    fun testToSizeAndAspectAndToRadiusRoundTrip() {
        // 1. Bulat sempurna (Kelonjongan 0%)
        val radiusCircle = DevKitAuthoringSession.toRadius(0.20f, 0f)
        assertEquals(0.20f, radiusCircle.rx, 0.0001f)
        assertEquals(0.20f, radiusCircle.ry, 0.0001f)
        val (sizeCircle, aspectCircle) = DevKitAuthoringSession.toSizeAndAspect(radiusCircle.rx, radiusCircle.ry)
        assertEquals(0.20f, sizeCircle, 0.0001f)
        assertEquals(0.0f, aspectCircle, 0.0001f)

        // 2. Lonjong mendatar (Kelonjongan negatif -50%)
        val radiusHorizontal = DevKitAuthoringSession.toRadius(0.20f, -50.0f)
        assertEquals(0.20f, radiusHorizontal.rx, 0.0001f)
        // ry = 0.20 * (1.0 - 0.90 * 0.50) = 0.20 * 0.55 = 0.11
        assertEquals(0.11f, radiusHorizontal.ry, 0.0001f)
        val (sizeHoriz, aspectHoriz) = DevKitAuthoringSession.toSizeAndAspect(radiusHorizontal.rx, radiusHorizontal.ry)
        assertEquals(0.20f, sizeHoriz, 0.0001f)
        assertEquals(-50.0f, aspectHoriz, 0.05f)

        // 3. Lonjong tegak (Kelonjongan positif +50%)
        val radiusVertical = DevKitAuthoringSession.toRadius(0.20f, 50.0f)
        assertEquals(0.11f, radiusVertical.rx, 0.0001f)
        assertEquals(0.20f, radiusVertical.ry, 0.0001f)
        val (sizeVert, aspectVert) = DevKitAuthoringSession.toSizeAndAspect(radiusVertical.rx, radiusVertical.ry)
        assertEquals(0.20f, sizeVert, 0.0001f)
        assertEquals(50.0f, aspectVert, 0.05f)

        // 4. Ekstrem -100% (Ketebalan minimum 10% dari S)
        val radiusMinHoriz = DevKitAuthoringSession.toRadius(0.20f, -100.0f)
        assertEquals(0.20f, radiusMinHoriz.rx, 0.0001f)
        assertEquals(0.02f, radiusMinHoriz.ry, 0.0001f)
        val (sizeMinH, aspectMinH) = DevKitAuthoringSession.toSizeAndAspect(radiusMinHoriz.rx, radiusMinHoriz.ry)
        assertEquals(0.20f, sizeMinH, 0.0001f)
        assertEquals(-100.0f, aspectMinH, 0.05f)

        // 5. Ekstrem +100% (Ketebalan minimum 10% dari S)
        val radiusMinVert = DevKitAuthoringSession.toRadius(0.20f, 100.0f)
        assertEquals(0.02f, radiusMinVert.rx, 0.0001f)
        assertEquals(0.20f, radiusMinVert.ry, 0.0001f)
        val (sizeMinV, aspectMinV) = DevKitAuthoringSession.toSizeAndAspect(radiusMinVert.rx, radiusMinVert.ry)
        assertEquals(0.20f, sizeMinV, 0.0001f)
        assertEquals(100.0f, aspectMinV, 0.05f)
    }

    @Test
    fun testConsistencyWhenSizeIsChangedWithAspect() {
        val tempDir = File.createTempFile("dev-session-size-aspect", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-shape", "Test Shape", null)

            // Bentuk oval awal: Ukuran 0.20, Kelonjongan -40%
            val updated1 = session.updateDrumShape("sedang", 0.20f, -40.0f)
            val drum1 = updated1.drums.first { it.id == "sedang" }
            val (size1, aspect1) = DevKitAuthoringSession.toSizeAndAspect(drum1.radius.rx, drum1.radius.ry)
            assertEquals(0.20f, size1, 0.001f)
            assertEquals(-40.0f, aspect1, 0.05f)

            // Perbesar ukuran menjadi 0.35 dengan menjaga kelonjongan tetap -40%
            val updated2 = session.updateDrumShape("sedang", 0.35f, aspect1)
            val drum2 = updated2.drums.first { it.id == "sedang" }
            val (size2, aspect2) = DevKitAuthoringSession.toSizeAndAspect(drum2.radius.rx, drum2.radius.ry)
            assertEquals(0.35f, size2, 0.001f)
            assertEquals(-40.0f, aspect2, 0.05f)
            assertTrue(session.hasUnsavedChanges)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUpdateDrumRotationAndResetDrumShape() {
        val tempDir = File.createTempFile("dev-session-rot-reset", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-rot-reset", "Test Rot Reset", null)

            // 1. Update rotasi kendang
            val rotKit = session.updateDrumRotation("sedang", 45.0f)
            val drumRot = rotKit.drums.first { it.id == "sedang" }
            assertEquals(45.0f, drumRot.rotation, 0.001f)

            // 2. Wrap rotasi di luar 0..360
            val wrapKit = session.updateDrumRotation("sedang", 380.0f)
            assertEquals(20.0f, wrapKit.drums.first { it.id == "sedang" }.rotation, 0.001f)

            val negWrapKit = session.updateDrumRotation("sedang", -30.0f)
            assertEquals(330.0f, negWrapKit.drums.first { it.id == "sedang" }.rotation, 0.001f)

            // 3. Ubah bentuk menjadi oval dan rotasi 60 derajat
            session.updateDrumShape("sedang", 0.25f, 60.0f)
            session.updateDrumRotation("sedang", 60.0f)

            val preReset = session.workingKit!!.drums.first { it.id == "sedang" }
            assertTrue(preReset.radius.rx != preReset.radius.ry)
            assertEquals(60.0f, preReset.rotation, 0.001f)

            // 4. Reset bentuk
            val resetKit = session.resetDrumShape("sedang")
            val postReset = resetKit.drums.first { it.id == "sedang" }

            // Kelonjongan harus kembali bulat sempurna (rx == ry == S)
            assertEquals(0.25f, postReset.radius.rx, 0.001f)
            assertEquals(0.25f, postReset.radius.ry, 0.001f)
            assertEquals(0.0f, postReset.rotation, 0.001f)
            val (_, aspectReset) = DevKitAuthoringSession.toSizeAndAspect(postReset.radius.rx, postReset.radius.ry)
            assertEquals(0.0f, aspectReset, 0.001f)
            assertTrue(session.hasUnsavedChanges)
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
        dos.writeInt(100)
        dos.writeInt(100)
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
    fun testInstallAndClearDrumBodyImage() {
        val tempDir = File.createTempFile("dev-session-body-install", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-body-install", "Test Body Install", null)

            val pngBytes = buildMinimalPng()
            val installRes = session.installDrumBodyImage("sedang", ByteArrayInputStream(pngBytes))
            assertTrue(installRes is DevImageInstaller.Result.Success)

            val drumWithBody = session.workingKit!!.drums.first { it.id == "sedang" }
            assertNotNull(drumWithBody.body)
            assertEquals("images/sedang-body.png", drumWithBody.body!!.image)
            assertTrue(File(tempDir, "images/sedang-body.png").exists())

            // Kosongkan body
            val clearRes = session.clearDrumBodyImage("sedang")
            assertTrue(clearRes is DevImageInstaller.Result.Success)

            val drumCleared = session.workingKit!!.drums.first { it.id == "sedang" }
            kotlin.test.assertNull(drumCleared.body)
            assertFalse(File(tempDir, "images/sedang-body.png").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUpdateDockAndResetDrumBody() {
        val tempDir = File.createTempFile("dev-session-body-update", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-body-update", "Test Body Update", null)

            val pngBytes = buildMinimalPng()
            session.installDrumBodyImage("sedang", ByteArrayInputStream(pngBytes))

            // 1. Update parameter geometri body
            val updated = session.updateDrumBody(
                drumId = "sedang",
                width = 0.40f,
                height = 0.35f,
                offsetX = 0.05f,
                offsetY = 0.18f,
                rotation = 25.0f
            )
            val body = updated.drums.first { it.id == "sedang" }.body!!
            assertEquals(0.40f, body.width, 0.001f)
            assertEquals(0.35f, body.height, 0.001f)
            assertEquals(0.05f, body.offsetX, 0.001f)
            assertEquals(0.18f, body.offsetY, 0.001f)
            assertEquals(25.0f, body.rotation, 0.001f)

            // 2. Dock ke kulit (Auto-Dock)
            val drum = updated.drums.first { it.id == "sedang" }
            val docked = session.dockDrumBodyToSkin("sedang")
            val dockedBody = docked.drums.first { it.id == "sedang" }.body!!
            assertEquals(drum.radius.rx * 2f, dockedBody.width, 0.001f)
            assertEquals(0.0f, dockedBody.offsetX, 0.001f)
            assertEquals(dockedBody.height / 2f, dockedBody.offsetY, 0.001f)
            assertEquals(0.0f, dockedBody.rotation, 0.001f)

            // 3. Reset body
            val reset = session.resetDrumBody("sedang")
            val resetBody = reset.drums.first { it.id == "sedang" }.body!!
            val stdSize = drum.radius.rx * 2f
            assertEquals(stdSize, resetBody.width, 0.001f)
            assertEquals(stdSize, resetBody.height, 0.001f)
            assertEquals(0.0f, resetBody.offsetX, 0.001f)
            assertEquals(stdSize / 2f, resetBody.offsetY, 0.001f)
            assertEquals(0.0f, resetBody.rotation, 0.001f)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRenameAndDeleteDrumWithBody() {
        val tempDir = File.createTempFile("dev-session-body-rename-del", "").apply { delete(); mkdirs() }
        try {
            val session = DevKitAuthoringSession(tempDir)
            session.createNewKit("test-rename-del", "Test Rename Del", null)

            val pngBytes = buildMinimalPng()
            session.installDrumBodyImage("sedang", ByteArrayInputStream(pngBytes))
            assertTrue(File(tempDir, "images/sedang-body.png").exists())

            // Rename drum
            session.renameDrumId("sedang", "sedang-baru")
            val renamedDrum = session.workingKit!!.drums.first { it.id == "sedang-baru" }
            assertEquals("images/sedang-baru-body.png", renamedDrum.body!!.image)
            assertTrue(File(tempDir, "images/sedang-baru-body.png").exists())
            assertFalse(File(tempDir, "images/sedang-body.png").exists())

            // Delete drum
            session.deleteDrum("sedang-baru")
            assertFalse(session.workingKit!!.drums.any { it.id == "sedang-baru" })
            assertFalse(File(tempDir, "images/sedang-baru-body.png").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
