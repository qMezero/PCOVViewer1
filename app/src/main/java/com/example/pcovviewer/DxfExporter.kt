package com.example.pcovviewer

import android.content.Context
import android.net.Uri
import java.io.OutputStreamWriter
import java.util.Locale
import kotlin.math.max

object DxfExporter {
    private const val DXF_VERSION = "AC1009"
    private const val LAYER_DEFAULT = "0"
    private const val LAYER_POINTS = "POINTS"
    private const val LAYER_CONNECTIONS = "CONNECTIONS"
    private const val LAYER_LABELS = "LABELS"

    fun exportToDxf(context: Context, points: List<PcoParser.PcoPoint>, destination: Uri): Boolean {
        val visiblePoints = points.filterNot { CodeRules.isHidden(it) }
        if (visiblePoints.isEmpty()) {
            return false
        }

        val minX = visiblePoints.minOf { it.x }
        val maxX = visiblePoints.maxOf { it.x }
        val minY = visiblePoints.minOf { it.y }
        val maxY = visiblePoints.maxOf { it.y }

        val spanX = max(maxX - minX, 1e-6f)
        val spanY = max(maxY - minY, 1e-6f)
        val span = max(spanX, spanY)

        val textHeight = max(span / 60f, 0.1f)
        val labelOffsetX = textHeight * 0.8f
        val labelOffsetY = textHeight * 1.2f
        val lineSpacing = textHeight * 0.35f
        val pointRadius = max(span / 400f, textHeight / 4f)

        val connections = ConnectionBuilder.build(visiblePoints)
        val polylines = PolylineBuilder.buildChains(visiblePoints, connections)

        val builder = DxfBuilder()
        builder.beginSection("HEADER")
        builder.append(9, "\$ACADVER")
        builder.append(1, DXF_VERSION)
        builder.endSection()

        builder.beginSection("TABLES")
        builder.beginTable("LTYPE", 1)
        builder.addLinetype("CONTINUOUS", "Solid line")
        builder.endTable()

        val layers = listOf(
            LayerDefinition(LAYER_DEFAULT, 7),
            LayerDefinition(LAYER_CONNECTIONS, 5),
            LayerDefinition(LAYER_POINTS, 1),
            LayerDefinition(LAYER_LABELS, 8)
        )
        builder.beginTable("LAYER", layers.size)
        layers.forEach { layer ->
            builder.addLayer(layer)
        }
        builder.endTable()
        builder.endSection()

        builder.beginSection("ENTITIES")
        polylines.forEach { chain ->
            if (chain.points.size < 2) return@forEach
            builder.addPolyline(chain)
        }

        visiblePoints.forEach { point ->
            val radiusScale = CodeRules.pointRadiusScale(point)
            builder.addPointCircle(point, pointRadius * radiusScale)
        }

        visiblePoints.forEach { point ->
            builder.addPointLabels(
                point = point,
                textHeight = textHeight,
                labelOffsetX = labelOffsetX,
                labelOffsetY = labelOffsetY,
                lineSpacing = lineSpacing
            )
        }
        builder.endSection()
        builder.finish()

        return try {
            context.contentResolver.openOutputStream(destination)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(builder.build())
                }
            } != null
        } catch (_: Exception) {
            false
        }
    }

    private data class LayerDefinition(val name: String, val color: Int)

    private class DxfBuilder {
        private val content = StringBuilder()

        fun beginSection(name: String) {
            append(0, "SECTION")
            append(2, name)
        }

        fun endSection() {
            append(0, "ENDSEC")
        }

        fun beginTable(name: String, count: Int) {
            append(0, "TABLE")
            append(2, name)
            append(70, count)
        }

        fun endTable() {
            append(0, "ENDTAB")
        }

        fun addLinetype(name: String, description: String) {
            append(0, "LTYPE")
            append(2, name)
            append(70, 64)
            append(3, description)
            append(72, 65)
            append(73, 0)
            append(40, 0.0)
        }

        fun addLayer(definition: LayerDefinition) {
            append(0, "LAYER")
            append(2, definition.name)
            append(70, 0)
            append(62, definition.color)
            append(6, "CONTINUOUS")
        }

        fun addPolyline(chain: PolylineChain) {
            append(0, "LWPOLYLINE")
            append(8, LAYER_CONNECTIONS)
            append(6, "CONTINUOUS")
            append(62, 5)
            append(90, chain.points.size)
            if (chain.isClosed) {
                append(70, 1)
            }
            chain.points.forEach { point ->
                append(10, point.x)
                append(20, point.y)
            }
        }

        fun addPointCircle(point: PcoParser.PcoPoint, radius: Float) {
            append(0, "CIRCLE")
            append(8, LAYER_POINTS)
            append(6, "CONTINUOUS")
            append(62, 1)
            append(10, point.x)
            append(20, point.y)
            append(30, 0.0)
            append(40, radius)
        }

        fun addPointLabels(
            point: PcoParser.PcoPoint,
            textHeight: Float,
            labelOffsetX: Float,
            labelOffsetY: Float,
            lineSpacing: Float
        ) {
            val baseX = point.x + labelOffsetX
            val numberY = point.y + labelOffsetY
            val codeY = numberY - textHeight - lineSpacing

            appendText(
                text = point.number.toString(),
                x = baseX,
                y = numberY,
                textHeight = textHeight,
                verticalAlign = 3
            )

            val codeValue = point.displayCode
            if (codeValue.isNotEmpty()) {
                appendText(
                    text = codeValue,
                    x = baseX,
                    y = codeY,
                    textHeight = textHeight,
                    verticalAlign = 1
                )
            }
        }

        private fun appendText(
            text: String,
            x: Float,
            y: Float,
            textHeight: Float,
            verticalAlign: Int
        ) {
            append(0, "TEXT")
            append(8, LAYER_LABELS)
            append(6, "CONTINUOUS")
            append(62, 8)
            append(10, x)
            append(20, y)
            append(30, 0.0)
            append(40, textHeight)
            append(1, text)
            append(7, "STANDARD")
            append(72, 1)
            append(73, verticalAlign)
            append(11, x)
            append(21, y)
            append(31, 0.0)
        }

        fun append(code: Int, value: String) {
            content.append(code).append('\n').append(value).append('\n')
        }

        fun append(code: Int, value: Int) {
            append(code, value.toString())
        }

        fun append(code: Int, value: Float) {
            append(code, format(value.toDouble()))
        }

        fun append(code: Int, value: Double) {
            append(code, format(value))
        }

        private fun format(value: Double): String {
            return String.format(Locale.US, "%.6f", value)
        }

        fun finish() {
            append(0, "EOF")
        }

        fun build(): String = content.toString()
    }
}
