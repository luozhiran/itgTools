package com.itg.itg_bitmap

import com.itg.itg_bitmap.compress.BitmapCompressUtils
import com.itg.itg_bitmap.decode.BitmapDecodeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapSamplingTest {
    @Test
    fun samplingUsesPowerOfTwoAndNeverReturnsZero() {
        assertEquals(4, BitmapDecodeUtils.calculateInSampleSize(4000, 3000, 800, 600))
        assertEquals(1, BitmapDecodeUtils.calculateInSampleSize(-1, -1, 800, 600))
        assertEquals(4, BitmapCompressUtils.calculateSampleSize(4000, 3000, 800, 600))
    }
}
