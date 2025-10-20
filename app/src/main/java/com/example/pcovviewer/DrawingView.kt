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

        val geometry = GeometryBuilder.build(points, width.toFloat(), height.toFloat()) ?: return

        geometry.connections.forEach { (start, end) ->
            canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
        }

        geometry.points.forEach { scaledPoint ->
            canvas.drawCircle(scaledPoint.x, scaledPoint.y, 4f, pointPaint)

            val label = "${scaledPoint.point.number}\n${scaledPoint.point.codeInfo.baseCode}"
            drawMultilineText(label, scaledPoint.x + 6f, scaledPoint.y - 6f, textPaint, canvas)
        }
    }

    private fun drawMultilineText(text: String, x: Float, y: Float, paint: Paint, canvas: Canvas) {
        val lines = text.split("\n")
        for ((index, line) in lines.withIndex()) {
            canvas.drawText(line, x, y + index * (paint.textSize + 2f), paint)
        }
    }
}
