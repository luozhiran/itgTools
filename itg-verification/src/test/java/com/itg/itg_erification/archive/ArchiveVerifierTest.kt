package com.itg.itg_erification.archive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveVerifierTest {

    @Test
    fun verifyZipAndEntryReadActualData() {
        val data = "archive payload".toByteArray()
        val zip = createZip(mapOf("data/file.txt" to data))
        val crc = CRC32().apply { update(data) }.value

        assertTrue(ArchiveVerifier.verifyZip(zip.absolutePath).isValid)
        assertTrue(
            ArchiveVerifier.verifyZipEntry(zip.absolutePath, "data/file.txt", crc).isValid
        )
        assertFalse(
            ArchiveVerifier.verifyZipEntry(zip.absolutePath, "data/file.txt", crc + 1).isValid
        )
    }

    @Test
    fun zipSlipCheckHandlesPathSegmentsAndBackslashes() {
        val safe = createZip(mapOf("data/file..backup.txt" to byteArrayOf(1)))
        val unsafe = createZip(mapOf("..\\outside.txt" to byteArrayOf(1)))

        assertTrue(ArchiveVerifier.verifyZipSafety(safe.absolutePath).isValid)
        assertFalse(ArchiveVerifier.verifyZipSafety(unsafe.absolutePath).isValid)
    }

    @Test
    fun zipSafetyRejectsInvalidLimitsAndHighPerEntryRatio() {
        val zip = createZip(mapOf("zeros.bin" to ByteArray(64 * 1024)))

        assertFalse(
            ArchiveVerifier.verifyZipSafety(zip.absolutePath, maxCompressionRatio = 0).isValid
        )
        assertFalse(
            ArchiveVerifier.verifyZipSafety(zip.absolutePath, maxCompressionRatio = 10).isValid
        )
    }

    private fun createZip(entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("archive-verifier-", ".zip")
        file.deleteOnExit()
        ZipOutputStream(FileOutputStream(file)).use { output ->
            entries.forEach { (name, data) ->
                output.putNextEntry(ZipEntry(name))
                output.write(data)
                output.closeEntry()
            }
        }
        return file
    }
}
