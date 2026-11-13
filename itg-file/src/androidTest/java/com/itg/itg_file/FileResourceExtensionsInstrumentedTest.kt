package com.itg.itg_file

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileResourceExtensionsInstrumentedTest {
    @Test
    fun copiesAssetToReceiverWithProgressAndOverwriteControl() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sourceContext = instrumentation.context
        val destination = File(instrumentation.targetContext.cacheDir, "asset-extension-test.txt")
        destination.delete()
        try {
            var lastCopied = 0L
            var failure: Exception? = null
            assertTrue(destination.copyFromAsset(
                onFailure = { failure = it },
                context = sourceContext,
                assetPath = "file_extension_sample.txt",
                onProgress = { copied, _ -> lastCopied = copied }
            ))
            assertNull(failure)
            assertEquals("asset copy sample\n", destination.readText())
            assertEquals(destination.length(), lastCopied)
            assertFalse(destination.copyFromAsset(sourceContext, "file_extension_sample.txt", overwrite = false))
        } finally {
            destination.delete()
        }
    }

    @Test
    fun copiesRawResourceToReceiverFromCoroutine() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sourceContext = instrumentation.context
        val resId = sourceContext.resources.getIdentifier(
            "file_extension_sample", "raw", sourceContext.packageName
        )
        assertTrue(resId != 0)
        val destination = File(instrumentation.targetContext.cacheDir, "raw-extension-test.txt")
        destination.delete()
        try {
            assertTrue(destination.copyFromRawSuspend(sourceContext, resId))
            assertEquals("raw copy sample\n", destination.readText())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun missingAssetReportsFailureToCaller() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val destination = File(instrumentation.targetContext.cacheDir, "missing-asset-test.txt")
        destination.delete()
        var failure: Exception? = null

        assertFalse(destination.copyFromAsset(
            onFailure = { failure = it },
            context = instrumentation.context,
            assetPath = "missing-asset-file.txt"
        ))
        assertTrue(failure?.message.orEmpty().contains("missing-asset-file.txt"))
        assertFalse(destination.exists())
    }
}
