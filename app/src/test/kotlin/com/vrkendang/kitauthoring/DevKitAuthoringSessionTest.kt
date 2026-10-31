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
}
