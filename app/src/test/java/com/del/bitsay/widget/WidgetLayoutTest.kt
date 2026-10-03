package com.del.bitsay.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {

    private val header = 52
    private val row = 48

    /** 52 (header) + 3 x 48 (rows) = 196dp is the point where the header starts paying off. */
    private val threshold = header + WidgetLayout.MIN_ROWS_WITH_HEADER * row

    @Test
    fun `the 2x2 minimum drops the header so the list gets the space`() {
        assertFalse(WidgetLayout.showHeader(minHeightDp = 110, header, row))
    }

    @Test
    fun `a widget with room for exactly three rows keeps its header`() {
        assertTrue(WidgetLayout.showHeader(minHeightDp = threshold, header, row))
    }

    @Test
    fun `one dp less than three rows hides the header`() {
        assertFalse(WidgetLayout.showHeader(minHeightDp = threshold - 1, header, row))
    }

    @Test
    fun `an unmeasured widget hides the header rather than wasting the space`() {
        // getAppWidgetOptions() can come back empty before the host has measured the widget.
        assertFalse(WidgetLayout.showHeader(minHeightDp = 0, header, row))
    }

    @Test
    fun `a tall widget keeps its header`() {
        assertTrue(WidgetLayout.showHeader(minHeightDp = 600, header, row))
    }

    @Test
    fun `the cutoff follows the real metrics instead of a hard-coded number`() {
        // A taller row height must move the cutoff, not silently starve the list.
        val tallRows = 60
        val tallThreshold = header + WidgetLayout.MIN_ROWS_WITH_HEADER * tallRows

        assertFalse(WidgetLayout.showHeader(minHeightDp = threshold, header, tallRows))
        assertTrue(WidgetLayout.showHeader(minHeightDp = tallThreshold, header, tallRows))
    }
}
