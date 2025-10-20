package com.example.pcovviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<PcoParser.PcoPoint> = emptyList()

    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 18f
        isAntiAlias = true
    }

    fun setData(points: List<PcoParser.PcoPoint>) {
        this.points = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (points.isEmpty()) return

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        val spanX = maxX - minX
        val spanY = maxY - minY

        val scaleX = if (spanX == 0f) 1f else width.toFloat() / spanX
        val scaleY = if (spanY == 0f) 1f else height.toFloat() / spanY
        val scale = minOf(scaleX, scaleY)

        val offsetX = (width.toFloat() - spanX * scale) / 2f
        val offsetY = (height.toFloat() - spanY * scale) / 2f

        val scaledPoints = points.map {
            val x = offsetX + (it.x - minX) * scale
            val y = offsetY + (maxY - it.y) * scale
            it.copy(x = x, y = y)
        }

        for (i in scaledPoints.indices) {
            val point = scaledPoints[i]
            val code = point.code

            if (code.contains("..")) {
                val parts = code.split("..")
                val target = parts.getOrNull(1)

                if (target.isNullOrEmpty() && i > 0) {
                    val prev = scaledPoints[i - 1]
                    canvas.drawLine(prev.x, prev.y, point.x, point.y, linePaint)
                } else if (!target.isNullOrEmpty()) {
                    val targetPoint = scaledPoints.find { it.number.toString() == target }
                    if (targetPoint != null) {
                        canvas.drawLine(point.x, point.y, targetPoint.x, targetPoint.y, linePaint)
                    }
                }
            }
        }

        for (point in scaledPoints) {
            canvas.drawCircle(point.x, point.y, 4f, pointPaint)

            val label = "${point.number}\n${point.code}"
            drawMultilineText(label, point.x + 6f, point.y - 6f, textPaint, canvas)
        }
    }

    private fun drawMultilineText(text: String, x: Float, y: Float, paint: Paint, canvas: Canvas) {
        val lines = text.split("\n")
        for ((index, line) in lines.withIndex()) {
            canvas.drawText(line, x, y + index * (paint.textSize + 2f), paint)
        }
    }
}
