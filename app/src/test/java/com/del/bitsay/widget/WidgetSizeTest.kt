package com.del.bitsay.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {

    @Test
    fun `a one cell high widget drops the header and shows single lines`() {
        val size = WidgetSize.from(minWidthDp = 180, minHeightDp = 60)
        assertEquals(WidgetSize.COMPACT, size)
        assertEquals(1, size.maxLines)
        assertEquals(false, size.showHeader)
        assertEquals(false, size.showTime)
    }

    @Test
    fun `a narrow widget is compact even when tall`() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.from(minWidthDp = 120, minHeightDp = 400))
    }

    @Test
    fun `the default 3x2 widget is regular`() {
        val size = WidgetSize.from(minWidthDp = 180, minHeightDp = 110)
        assertEquals(WidgetSize.REGULAR, size)
        assertEquals(2, size.maxLines)
        assertEquals(true, size.showHeader)
        assertEquals(false, size.showTime)
    }

    @Test
    fun `a tall widget shows three lines and a timestamp`() {
        val size = WidgetSize.from(minWidthDp = 250, minHeightDp = 260)
        assertEquals(WidgetSize.EXPANDED, size)
        assertEquals(3, size.maxLines)
        assertEquals(true, size.showTime)
    }

    @Test
    fun `an unknown bundle falls back to the default layout`() {
        // getAppWidgetOptions() can come back empty before the host has measured the widget.
        assertEquals(WidgetSize.REGULAR, WidgetSize.from(minWidthDp = 0, minHeightDp = 0))
    }

    @Test
    fun `boundaries are inclusive on the documented side`() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.from(200, 90))
        assertEquals(WidgetSize.REGULAR, WidgetSize.from(200, 91))
        assertEquals(WidgetSize.REGULAR, WidgetSize.from(200, 199))
        assertEquals(WidgetSize.EXPANDED, WidgetSize.from(200, 200))
    }
}
