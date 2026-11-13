package com.itg.itg_file

import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.read.FileReadUtils
import com.itg.itg_file.write.FileWriteUtils
import com.itg.itg_file.write.OkioWriteUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class FileSafetyTest {

    @Test
    fun copyRejectsSameCanonicalFileWithoutTruncatingIt() {
        val file = File.createTempFile("itg-file-", ".bin").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }

        assertFalse(FileUtils.copy(file.absolutePath, file.absolutePath))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), file.readBytes())
    }

    @Test
    fun limitedReadRejectsOversizedInputInsteadOfSilentlyTruncating() {
        assertNull(FileReadUtils.readBytes(ByteArrayInputStream(ByteArray(5)), maxBytes = 4))
        assertArrayEquals(
            ByteArray(4),
            FileReadUtils.readBytes(ByteArrayInputStream(ByteArray(4)), maxBytes = 4)
        )
    }

    @Test
    fun failedStreamWriteKeepsExistingFile() {
        val file = File.createTempFile("itg-file-", ".bin").apply {
            writeText("old")
            deleteOnExit()
        }

        assertFalse(FileWriteUtils.writeFromStream(file.absolutePath, failingInput()))
        assertArrayEquals("old".toByteArray(), file.readBytes())

        assertFalse(OkioWriteUtils.writeFromStream(file.absolutePath, failingInput()))
        assertArrayEquals("old".toByteArray(), file.readBytes())
    }

    private fun failingInput(): InputStream = object : InputStream() {
        private var firstRead = true

        override fun read(): Int = throw IOException("simulated interruption")

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (!firstRead) throw IOException("simulated interruption")
            firstRead = false
            bytes[offset] = 1
            return 1
        }
    }
}
