package com.example.pcovviewer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Shared drawing style configuration ensuring the preview and PDF export look identical.
 */
object DrawingStyle {

    const val BASE_POINT_RADIUS = 4f
    const val BASE_STROKE_WIDTH = 2f
    const val BASE_TEXT_SIZE = 18f
    const val BASE_LABEL_OFFSET_X = 6f
    const val BASE_LABEL_OFFSET_Y = 6f
    const val BASE_LINE_SPACING = 2f

    fun createPointPaint(): Paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun createLinePaint(): Paint = Paint().apply {
        color = Color.BLUE
        strokeWidth = BASE_STROKE_WIDTH
        isAntiAlias = true
    }

    fun createTextPaint(): Paint = Paint().apply {
        color = Color.DKGRAY
        textSize = BASE_TEXT_SIZE
        isAntiAlias = true
        isLinearText = true
        isSubpixelText = true
    }

    fun drawMultilineLabel(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
        lineSpacing: Float
    ) {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, x, y + index * (paint.textSize + lineSpacing), paint)
        }
    }
}
