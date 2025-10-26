package com.example.pcovviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint

/**
 * Builds a miniature bitmap that reflects connections between the supplied points.
 */
object MiniPreviewGenerator {

    private const val DEFAULT_SIZE_PX = 256
    private const val STROKE_FRACTION = 0.01f
    private const val MIN_STROKE_WIDTH = 1.5f
    private const val DASH_MULTIPLIER = 3f

    fun createConnectionsPreview(
        points: List<PcoParser.PcoPoint>,
        width: Int = DEFAULT_SIZE_PX,
        height: Int = DEFAULT_SIZE_PX,
        backgroundColor: Int = DrawingStyle.MINI_PREVIEW_BACKGROUND_COLOR,
        allowedBaseCodes: Set<String?>? = null
    ): Bitmap? {
        if (points.isEmpty() || width <= 0 || height <= 0) {
            return null
        }

        val filteredPoints = if (allowedBaseCodes.isNullOrEmpty()) {
            points
        } else {
            points.filter { point ->
                val baseCode = point.codeInfo.baseCode.takeIf { it.isNotBlank() }
                allowedBaseCodes.contains(baseCode)
            }
        }

        if (filteredPoints.isEmpty()) {
            return null
        }

        val geometry = GeometryBuilder.build(
            points = filteredPoints,
            width = width.toFloat(),
            height = height.toFloat()
        ) ?: return null

        if (geometry.connections.isEmpty()) {
            return null
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val baseStrokeWidth = (minOf(width, height) * STROKE_FRACTION).coerceAtLeast(MIN_STROKE_WIDTH)
        val dashInterval = (baseStrokeWidth * DASH_MULTIPLIER).coerceAtLeast(baseStrokeWidth)
        val dashGap = dashInterval

        val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DrawingStyle.LINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = baseStrokeWidth
        }

        val dottedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DrawingStyle.LINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = baseStrokeWidth
            pathEffect = DashPathEffect(floatArrayOf(dashInterval, dashGap), 0f)
        }

        geometry.connections.forEach { connection ->
            val paint = when (connection.style) {
                ConnectionStyle.SOLID -> solidPaint
                ConnectionStyle.DOTTED -> dottedPaint
            }
            canvas.drawLine(
                connection.start.x,
                connection.start.y,
                connection.end.x,
                connection.end.y,
                paint
            )
        }

        return bitmap
    }
}
