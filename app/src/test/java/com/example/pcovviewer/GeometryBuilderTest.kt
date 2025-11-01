package com.example.pcovviewer

import com.example.pcovviewer.PcoParser.PcoPoint
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

class GeometryBuilderTest {

    @Test
    fun `connection between 51 and 51N uses circle markers`() {
        val points = listOf(
            PcoPoint(number = 1, code = "51..", x = 0f, y = 0f, z = null),
            PcoPoint(number = 2, code = "51..N", x = 10f, y = 0f, z = null)
        )

        val geometry = GeometryBuilder.build(points, width = 100f, height = 100f)

        assertNotNull(geometry)
        val connection = geometry.connections.single()
        assertEquals(ConnectionStyle.CIRCLE_MARKERS, connection.style)
    }

    @Test
    fun `connection between 51N and 51 uses circle markers`() {
        val points = listOf(
            PcoPoint(number = 10, code = "51..N", x = 0f, y = 0f, z = null),
            PcoPoint(number = 11, code = "51..", x = 5f, y = 5f, z = null)
        )

        val geometry = GeometryBuilder.build(points, width = 100f, height = 100f)

        assertNotNull(geometry)
        val connection = geometry.connections.single()
        assertEquals(ConnectionStyle.CIRCLE_MARKERS, connection.style)
    }
}
