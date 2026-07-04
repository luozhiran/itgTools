package com.itg.itg_file.read

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileReadUtilsTest {

    @Test
    fun readChunksReturnsIndependentChunksWithoutTrailingGarbage() {
        val file = Files.createTempFile("itg-read-chunks-", ".bin").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val chunks = mutableListOf<ByteArray>()
        val total = FileReadUtils.readChunks(
            path = file.absolutePath,
            chunkSize = 4
        ) { chunk, _, totalChunks ->
            assertEquals(2, totalChunks)
            chunks += chunk
            true
        }

        assertEquals(5L, total)
        assertEquals(2, chunks.size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), chunks[0])
        assertArrayEquals(byteArrayOf(5), chunks[1])
        assertNotSame(chunks[0], chunks[1])

        chunks[0][0] = 9
        assertTrue(chunks[1][0] == 5.toByte())
        file.delete()
    }

    @Test
    fun readLinesStreamingReturnsProcessedLineCountWhenStoppedEarly() {
        val file = Files.createTempFile("itg-read-lines-", ".txt").toFile()
        file.writeText("first\nsecond\nthird")

        val seen = mutableListOf<String>()
        val count = FileReadUtils.readLinesStreaming(file.absolutePath) { line, index ->
            seen += "$index:$line"
            index < 0
        }

        assertEquals(1, count)
        assertEquals(listOf("0:first"), seen)
        file.delete()
    }
}
