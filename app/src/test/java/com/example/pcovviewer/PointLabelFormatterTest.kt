package com.example.pcovviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class PointLabelFormatterTest {

    @Test
    fun `adds missing second dot to connection without suffix`() {
        assertEquals("306..", normalizeConnectionCode("306."))
    }

    @Test
    fun `adds missing second dot before numeric suffix`() {
        assertEquals("306..7", normalizeConnectionCode("306.7"))
    }

    @Test
    fun `keeps codes without connection separator unchanged`() {
        assertEquals("ABC", normalizeConnectionCode("ABC"))
    }

    @Test
    fun `keeps codes that already contain double dots unchanged`() {
        assertEquals("306..7", normalizeConnectionCode("306..7"))
    }

    @Test
    fun `keeps codes that start with double dots unchanged`() {
        assertEquals("..306", normalizeConnectionCode("..306"))
    }

    @Test
    fun `removes spaces around connection separators`() {
        assertEquals("51..2", normalizeConnectionCode("51 .. 2"))
        assertEquals("51..", normalizeConnectionCode("51 .."))
    }
}
