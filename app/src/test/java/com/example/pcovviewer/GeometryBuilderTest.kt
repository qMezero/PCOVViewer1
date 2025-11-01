package com.example.pcovviewer

import com.example.pcovviewer.PcoParser.PcoPoint
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

class GeometryBuilderTest {

    @Test
    fun `connection between 51 and 51-target uses circle markers`() {
        val points = listOf(
            PcoPoint(number = 1, code = "51..", x = 0f, y = 0f, z = null),
            PcoPoint(number = 2, code = "51..2", x = 10f, y = 0f, z = null)
        )

        val geometry = assertNotNull(GeometryBuilder.build(points, width = 100f, height = 100f))
        val connection = geometry.connections.single()
        assertEquals(ConnectionStyle.CIRCLE_MARKERS, connection.style)
    }

    @Test
    fun `connection between 51-target and 51 uses circle markers`() {
        val points = listOf(
            PcoPoint(number = 10, code = "51..42", x = 0f, y = 0f, z = null),
            PcoPoint(number = 11, code = "51..", x = 5f, y = 5f, z = null)
        )

        val geometry = assertNotNull(GeometryBuilder.build(points, width = 100f, height = 100f))
        val connection = geometry.connections.single()
        assertEquals(ConnectionStyle.CIRCLE_MARKERS, connection.style)
    }

    @Test
    fun `connection ignores 51 when both are bare`() {
        val points = listOf(
            PcoPoint(number = 100, code = "51..", x = 0f, y = 0f, z = null),
            PcoPoint(number = 101, code = "51..", x = 5f, y = 0f, z = null)
        )

        val geometry = assertNotNull(GeometryBuilder.build(points, width = 100f, height = 100f))
        val connection = geometry.connections.single()
        assertEquals(ConnectionStyle.SOLID, connection.style)
    }

    @Test
    fun `connection between 51 codes with spaces uses circle markers`() {
        val points = listOf(
            PcoPoint(number = 200, code = "51 ..", x = 0f, y = 0f, z = null),
            PcoPoint(number = 201, code = "51 .. 200", x = 10f, y = 0f, z = null)
        )

        val geometry = assertNotNull(GeometryBuilder.build(points, width = 100f, height = 100f))
        val connection = geometry.connections.single()
        assertEquals(ConnectionStyle.CIRCLE_MARKERS, connection.style)
    }
}
