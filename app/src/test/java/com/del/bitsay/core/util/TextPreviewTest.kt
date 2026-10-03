package com.del.bitsay.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPreviewTest {

    @Test
    fun `a multi line note previews as one line`() {
        assertEquals(
            "会议纪要 1. 确定 Q4 目标 2. 排期评审",
            TextPreview.singleLine("会议纪要\n1. 确定 Q4 目标\n2. 排期评审"),
        )
    }

    @Test
    fun `runs of whitespace collapse into a single space`() {
        assertEquals("a b c", TextPreview.singleLine("a \t\n\n   b\u3000c"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("买牛奶", TextPreview.singleLine("  买牛奶\n\n"))
    }

    @Test
    fun `a single line note is left alone`() {
        assertEquals("回复客户邮件", TextPreview.singleLine("回复客户邮件"))
    }

    @Test
    fun `blank text stays blank`() {
        assertEquals("", TextPreview.singleLine("   \n\t "))
    }
}
