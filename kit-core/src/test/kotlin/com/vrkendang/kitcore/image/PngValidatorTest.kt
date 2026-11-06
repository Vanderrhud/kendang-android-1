package com.vrkendang.kitcore.image

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PngValidatorTest {

    private fun createMinimalPng(width: Int, height: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        // Signature (8 bytes)
        baos.write(byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        ))

        // IHDR chunk (length = 13)
        val dos = DataOutputStream(baos)
        dos.writeInt(13)
        dos.write("IHDR".toByteArray(Charsets.US_ASCII))
        dos.writeInt(width)
        dos.writeInt(height)
        dos.writeByte(8) // bit depth
        dos.writeByte(6) // color type (RGBA)
        dos.writeByte(0) // compression
        dos.writeByte(0) // filter
        dos.writeByte(0) // interlace
        dos.writeInt(0) // dummy CRC
        dos.flush()

        return baos.toByteArray()
    }

    @Test
    fun testValidSquarePng() {
        val pngBytes = createMinimalPng(512, 512)
        val header = PngValidator.validate(pngBytes)
        assertEquals(512, header.width)
        assertEquals(512, header.height)
        assertTrue(header.isSquare)
    }

    @Test
    fun testValidNonSquarePng() {
        val pngBytes = createMinimalPng(800, 600)
        val header = PngValidator.validate(pngBytes)
        assertEquals(800, header.width)
        assertEquals(600, header.height)
        assertFalse(header.isSquare)
    }

    @Test
    fun testTruncatedBytesThrowsException() {
        val truncated = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte())
        assertFailsWith<IllegalArgumentException> {
            PngValidator.validate(truncated)
        }
    }

    @Test
    fun testInvalidSignatureThrowsException() {
        val fakePng = ByteArray(40) { 0 }
        assertFailsWith<IllegalArgumentException> {
            PngValidator.validate(fakePng)
        }
    }

    @Test
    fun testFirstChunkNotIhdrThrowsException() {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        ))
        val dos = DataOutputStream(baos)
        dos.writeInt(13)
        dos.write("PLTE".toByteArray(Charsets.US_ASCII)) // Not IHDR
        dos.write(ByteArray(13))
        dos.writeInt(0)
        dos.flush()

        assertFailsWith<IllegalArgumentException> {
            PngValidator.validate(baos.toByteArray())
        }
    }
}
