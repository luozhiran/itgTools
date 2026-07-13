package com.itg.itg_ui.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TabBadgeTest {

    @Test
    fun maxNumber_acceptsPositiveValue() {
        assertEquals(1, TabBadge(maxNumber = 1).maxNumber)
    }

    @Test
    fun maxNumber_rejectsZeroOrNegativeValue() {
        assertThrows(IllegalArgumentException::class.java) { TabBadge(maxNumber = 0) }
        assertThrows(IllegalArgumentException::class.java) { TabBadge(maxNumber = -1) }
    }
}
