package com.itg.itg_file

import com.itg.itg_file.core.FileUtils
import com.itg.itg_file.read.FileReadUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

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
}
