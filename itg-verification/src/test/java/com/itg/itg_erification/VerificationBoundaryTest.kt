package com.itg.itg_erification

import com.itg.itg_erification.compare.FileComparator
import com.itg.itg_erification.download.DownloadVerifier
import com.itg.itg_erification.integrity.IntegrityVerifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VerificationBoundaryTest {

    @Test
    fun truncatedDownloadIsNotComplete() {
        val file = File.createTempFile("download-", ".bin").apply {
            writeBytes(ByteArray(3))
            deleteOnExit()
        }

        val result = DownloadVerifier.verifyDownload(file.absolutePath, expectedSize = 4)
        assertFalse(result.isValid)
        assertFalse(result.isComplete)
    }

    @Test
    fun fileHeaderRejectsInvalidOffset() {
        val file = File.createTempFile("header-", ".bin").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        assertFalse(
            IntegrityVerifier.verifyFileHeader(file.absolutePath, byteArrayOf(1), -1).isValid
        )
    }

    @Test
    fun invalidDirectoryIsReportedAsDifference() {
        val valid = createTempDir(prefix = "compare-")
        valid.deleteOnExit()

        val result = FileComparator.compareDirectories(
            File(valid, "missing").absolutePath,
            valid.absolutePath
        )
        assertTrue(result.hasDifference)
        assertTrue(result.errors.isNotEmpty())
    }
}
