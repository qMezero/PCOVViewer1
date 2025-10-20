package com.example.pcovviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeInfoTest {

    @Test
    fun `parse base code without connections`() {
        val info = CodeInfo.parse("701")

        assertEquals("701", info.baseCode)
        assertFalse(info.connectsToPrevious)
        assertTrue(info.connectionTargets.isEmpty())
    }

    @Test
    fun `parse code that only links to previous point`() {
        val info = CodeInfo.parse("701..")

        assertEquals("701", info.baseCode)
        assertTrue(info.connectsToPrevious)
        assertTrue(info.connectionTargets.isEmpty())
    }

    @Test
    fun `parse code with explicit connection targets keeps previous link`() {
        val info = CodeInfo.parse("701..12.25")

        assertEquals("701", info.baseCode)
        assertTrue("Expected connection to previous point when suffix present", info.connectsToPrevious)
        assertEquals(listOf(12, 25), info.connectionTargets)
    }
}
