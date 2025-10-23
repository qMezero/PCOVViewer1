package com.example.pcovviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryBuilderTest {

    @Test
    fun `does not connect to previous when code references previous point`() {
        val points = listOf(
            PcoParser.PcoPoint(number = 1, code = "10", x = 0f, y = 0f, z = null),
            PcoParser.PcoPoint(number = 2, code = "10..1", x = 1f, y = 0f, z = null)
        )

        val geometry = GeometryBuilder.build(points, width = 100f, height = 100f)

        assertNotNull(geometry)
        assertTrue(geometry!!.connections.isEmpty())
    }

    @Test
    fun `connects to explicit non-previous target`() {
        val points = listOf(
            PcoParser.PcoPoint(number = 1, code = "10", x = 0f, y = 0f, z = null),
            PcoParser.PcoPoint(number = 2, code = "10", x = 1f, y = 0f, z = null),
            PcoParser.PcoPoint(number = 3, code = "10..1", x = 2f, y = 0f, z = null)
        )

        val geometry = GeometryBuilder.build(points, width = 100f, height = 100f)

        assertNotNull(geometry)
        val connections = geometry!!.connections.map { connection ->
            setOf(connection.first.point.number, connection.second.point.number)
        }

        assertEquals(1, connections.size)
        assertTrue(connections.contains(setOf(1, 3)))
    }
}

