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

    @Test
    fun imageBadge_inheritsPositionAndCountConfiguration() {
        val badge: TabBadge = TabImageBadge(
            imageRes = 1,
            count = 12,
            position = TabBadgePosition.BOTTOM_START,
            horizontalOffsetDp = 3,
        )
        assertEquals(12, badge.count)
        assertEquals(TabBadgePosition.BOTTOM_START, badge.position)
        assertEquals(3, badge.horizontalOffsetDp)
    }

    @Test
    fun imageBadge_requiresAnImageAndPositiveSize() {
        assertThrows(IllegalArgumentException::class.java) { TabImageBadge() }
        assertThrows(IllegalArgumentException::class.java) {
            TabImageBadge(imageRes = 1, imageSizeDp = 0)
        }
    }
}
