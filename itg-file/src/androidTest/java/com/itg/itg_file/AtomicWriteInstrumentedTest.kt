package com.itg.itg_file

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itg.itg_file.write.FileWriteUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AtomicWriteInstrumentedTest {
    @Test
    fun atomicWriteReplacesExistingContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "atomic-write-test.txt")
        file.writeText("old")
        try {
            assertTrue(FileWriteUtils.writeTextAtomic(file.absolutePath, "new"))
            assertEquals("new", file.readText())
        } finally {
            file.delete()
        }
    }
}
