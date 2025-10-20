package com.example.pcovviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.DKGRAY
        isAntiAlias = true
    }

    private val basePointRadius = 4f
    private val baseStrokeWidth = 2f
    private val baseTextSize = 18f
    private val baseLabelOffsetX = 6f
    private val baseLabelOffsetY = 6f
    private val baseLineSpacing = 2f

    private var scaleFactor = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    private val scaleGestureDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val previousScale = scaleFactor
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.5f, 8f)

                val focusX = detector.focusX
                val focusY = detector.focusY

                val scaleChange = scaleFactor / previousScale
                panX = focusX - (focusX - panX) * scaleChange
                panY = focusY - (focusY - panY) * scaleChange

                invalidate()
                return true
            }
        })

    fun setData(points: List<PcoParser.PcoPoint>) {
        this.points = points
        scaleFactor = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val geometry = GeometryBuilder.build(points, width.toFloat(), height.toFloat()) ?: return

        val adjustedStrokeWidth = baseStrokeWidth / scaleFactor
        val adjustedTextSize = baseTextSize / scaleFactor
        val adjustedPointRadius = basePointRadius / scaleFactor
        val labelOffsetX = baseLabelOffsetX / scaleFactor
        val labelOffsetY = baseLabelOffsetY / scaleFactor
        val lineSpacing = baseLineSpacing / scaleFactor

        linePaint.strokeWidth = adjustedStrokeWidth
        textPaint.textSize = adjustedTextSize

        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scaleFactor, scaleFactor)

        geometry.connections.forEach { (start, end) ->
            canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
        }

        geometry.points.forEach { scaledPoint ->
            canvas.drawCircle(scaledPoint.x, scaledPoint.y, adjustedPointRadius, pointPaint)

            val label = "${scaledPoint.point.number}\n${scaledPoint.point.codeInfo.baseCode}"
            drawMultilineText(
                label,
                scaledPoint.x + labelOffsetX,
                scaledPoint.y - labelOffsetY,
                textPaint,
                lineSpacing,
                canvas
            )
        }

        canvas.restore()
    }

    private fun drawMultilineText(
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
        lineSpacing: Float,
        canvas: Canvas
    ) {
        val lines = text.split("\n")
        for ((index, line) in lines.withIndex()) {
            canvas.drawText(line, x, y + index * (paint.textSize + lineSpacing), paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y
                isPanning = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleGestureDetector.isInProgress && isPanning) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    panX += dx
                    panY += dy
                    invalidate()
                    lastTouchX = event.x
                    lastTouchY = event.y
                } else {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }
}
