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
    fun `does not connect to previous when code has empty base`() {
        val points = listOf(
            PcoParser.PcoPoint(number = 1, code = "", x = 0f, y = 0f, z = null),
            PcoParser.PcoPoint(number = 2, code = "..1", x = 1f, y = 0f, z = null)
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

    @Test
    fun `does not connect points 417 and 418 in sample asset`() {
        val rawContent = java.io.File("src/main/assets/25_24334gpskt.pco").readText()
        val points = PcoParser.parse(rawContent)

        val geometry = GeometryBuilder.build(points, width = 1000f, height = 1000f)

        assertNotNull(geometry)

        val actualConnections = geometry!!.connections
            .map { connection ->
                setOf(connection.first.point.number, connection.second.point.number)
            }
            .toSet()

        val expectedConnections = setOf(setOf(418, 423))

        assertEquals(expectedConnections, actualConnections)
    }
}

