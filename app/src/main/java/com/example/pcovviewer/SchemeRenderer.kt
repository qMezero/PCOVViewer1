package com.example.pcovviewer

import android.graphics.Canvas
import android.graphics.Paint

object SchemeRenderer {

    fun draw(
        canvas: Canvas,
        geometry: Geometry,
        style: SchemeStyle,
        pointPaint: Paint,
        linePaint: Paint,
        textPaint: Paint,
        pointRadius: Float,
        labelOffsetX: Float,
        labelOffsetY: Float,
        lineSpacing: Float
    ) {
        geometry.connections.forEach { (start, end) ->
            canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
        }

        geometry.points.forEach { scaledPoint ->
            canvas.drawCircle(scaledPoint.x, scaledPoint.y, pointRadius, pointPaint)

            val lines = style.labelLines(scaledPoint.point)
            drawMultilineText(
                lines = lines,
                x = scaledPoint.x + labelOffsetX,
                y = scaledPoint.y - labelOffsetY,
                paint = textPaint,
                lineSpacing = lineSpacing,
                canvas = canvas
            )
        }
    }

    private fun drawMultilineText(
        lines: List<String>,
        x: Float,
        y: Float,
        paint: Paint,
        lineSpacing: Float,
        canvas: Canvas
    ) {
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, x, y + index * (paint.textSize + lineSpacing), paint)
        }
    }
}
