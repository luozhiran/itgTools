package com.itg.itg_bitmap

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itg.itg_bitmap.shape.BitmapShapeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapShapeInstrumentedTest {
    @Test
    fun circleWithBorderClipsCornersAndPreservesCenter() {
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val result = BitmapShapeUtils.toCircleWithBorder(source, 2, Color.BLUE)
        assertNotNull(result)
        result!!
        assertEquals(Color.TRANSPARENT, result.getPixel(0, 0))
        assertEquals(Color.RED, result.getPixel(result.width / 2, result.height / 2))
        source.recycle()
        result.recycle()
    }
}
