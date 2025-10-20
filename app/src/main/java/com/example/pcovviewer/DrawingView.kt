package com.example.pcovviewer.ui.theme

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.pcovviewer.PcoParser

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<PcoParser.PcoPoint> = emptyList()

    // Кисть для точек (красная)
    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Кисть для линий (синяя)
    private val linePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // Кисть для подписей
    private val textPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 18f   // маленький шрифт
        isAntiAlias = true
    }

    // Устанавливаем точки
    fun setData(points: List<PcoParser.PcoPoint>) {
        this.points = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (points.isEmpty()) return

        // Нормализуем координаты под размер экрана
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        val scaleX = width / (maxX - minX)
        val scaleY = height / (maxY - minY)
        val scale = minOf(scaleX, scaleY)

        val offsetX = (width - (maxX - minX) * scale) / 2
        val offsetY = (height - (maxY - minY) * scale) / 2

        val scaledPoints = points.map {
            val x = offsetX + (it.x - minX) * scale
            val y = offsetY + (maxY - it.y) * scale // инвертируем ось Y
            it.copy(x = x, y = y)
        }

        // --- РИСОВАНИЕ ЛИНИЙ ---
        for (i in scaledPoints.indices) {
            val point = scaledPoints[i]
            val code = point.code

            // Если код содержит ".."
            if (code.contains("..")) {
                val parts = code.split("..")
                val base = parts[0]
                val target = parts.getOrNull(1)

                // Если просто "301.." → соединяем с предыдущей точкой
                if (target.isNullOrEmpty() && i > 0) {
                    val prev = scaledPoints[i - 1]
                    canvas.drawLine(prev.x, prev.y, point.x, point.y, linePaint)
                }
                // Если "301..12" → соединяем с точкой с номером 12
                else if (!target.isNullOrEmpty()) {
                    val targetPoint = scaledPoints.find { it.number.toString() == target }
                    if (targetPoint != null) {
                        canvas.drawLine(point.x, point.y, targetPoint.x, targetPoint.y, linePaint)
                    }
                }
            }
        }

        // --- РИСОВАНИЕ ТОЧЕК И ПОДПИСЕЙ ---
        for (point in scaledPoints) {
            canvas.drawCircle(point.x, point.y, 4f, pointPaint) // маленькие точки

            // подпись "номер / код" рядом
            val label = "${point.number}\n${point.code}"
            drawMultilineText(label, point.x + 6f, point.y - 6f, textPaint, canvas)
        }
    }

    // Вспомогательная функция для многострочного текста
    private fun drawMultilineText(text: String, x: Float, y: Float, paint: Paint, canvas: Canvas) {
        val lines = text.split("\n")
        for ((i, line) in lines.withIndex()) {
            canvas.drawText(line, x, y + i * (paint.textSize + 2), paint)
        }
    }
}
