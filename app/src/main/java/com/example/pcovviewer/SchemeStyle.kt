package com.example.pcovviewer

import android.graphics.Color
import android.graphics.Paint

data class SchemeStyle(
    val backgroundColor: Int = Color.parseColor("#E6E6E6"),
    val pointColor: Int = Color.RED,
    val lineColor: Int = Color.BLUE,
    val textColor: Int = Color.DKGRAY,
    val basePointRadius: Float = 4f,
    val lineStrokeWidth: Float = 2f,
    val baseStrokeWidth: Float = 2f,
    val baseTextSize: Float = 18f,
    val baseLabelOffsetX: Float = 6f,
    val baseLabelOffsetY: Float = 6f,
    val baseLineSpacing: Float = 2f,
    val outerPadding: Float = 16f,
    /**
     * Additional multiplier applied after fitting the drawing to the viewport.
     * Values below 1f shrink the scheme slightly so it keeps visible margins.
     */
    val contentScaleFactor: Float = 0.9f
) {

    fun createPointPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pointColor
        style = Paint.Style.FILL
    }

    fun createLinePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeWidth = lineStrokeWidth
    }

    fun createBaseTextPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = baseTextSize
    }

    fun labelLines(point: PcoParser.PcoPoint): List<String> {
        val lines = mutableListOf(point.number.toString())
        val baseCode = point.codeInfo.baseCode
        if (baseCode.isNotEmpty()) {
            lines += baseCode
        }
        return lines
    }
}

object SchemeStyles {
    val default = SchemeStyle()
}
