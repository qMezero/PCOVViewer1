package com.example.pcovviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
        color = DrawingStyle.POINT_COLOR
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val specialPointFillPaint = Paint().apply {
        color = DrawingStyle.SPECIAL_POINT_FILL_COLOR
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val specialPointStrokePaint = Paint().apply {
        color = DrawingStyle.POINT_COLOR
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val specialPointTextPaint = Paint().apply {
        color = DrawingStyle.SPECIAL_POINT_TEXT_COLOR
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val solidLinePaint = Paint().apply {
        color = DrawingStyle.LINE_COLOR
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val dottedLinePaint = Paint().apply {
        color = DrawingStyle.LINE_COLOR
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = DrawingStyle.TEXT_COLOR
        isAntiAlias = true
    }

    private val basePointRadius = DrawingStyle.BASE_POINT_RADIUS
    private val baseStrokeWidth = DrawingStyle.BASE_STROKE_WIDTH
    private val baseTextSize = DrawingStyle.BASE_TEXT_SIZE
    private val baseLabelOffsetX = DrawingStyle.BASE_LABEL_OFFSET_X
    private val baseLabelOffsetY = DrawingStyle.BASE_LABEL_OFFSET_Y
    private val baseLineSpacing = DrawingStyle.BASE_LINE_SPACING
    private val baseDashInterval = DrawingStyle.BASE_DASH_INTERVAL
    private val baseDashGap = DrawingStyle.BASE_DASH_GAP
    private val baseSpecialPointRadius = DrawingStyle.BASE_SPECIAL_POINT_RADIUS
    private val baseSpecialPointStrokeWidth = DrawingStyle.BASE_SPECIAL_POINT_STROKE_WIDTH
    private val baseSpecialPointTextSize = DrawingStyle.BASE_SPECIAL_POINT_TEXT_SIZE

    private val specialPointCode = "40"

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
        val adjustedSpecialPointRadius = baseSpecialPointRadius / scaleFactor
        val adjustedSpecialStrokeWidth = baseSpecialPointStrokeWidth / scaleFactor
        val adjustedSpecialTextSize = baseSpecialPointTextSize / scaleFactor
        val labelOffsetX = baseLabelOffsetX / scaleFactor
        val labelOffsetY = baseLabelOffsetY / scaleFactor
        val lineSpacing = baseLineSpacing / scaleFactor

        solidLinePaint.strokeWidth = adjustedStrokeWidth
        solidLinePaint.pathEffect = null
        dottedLinePaint.strokeWidth = adjustedStrokeWidth
        dottedLinePaint.pathEffect = DashPathEffect(
            floatArrayOf(baseDashInterval / scaleFactor, baseDashGap / scaleFactor),
            0f
        )

        textPaint.textSize = adjustedTextSize
        specialPointStrokePaint.strokeWidth = adjustedSpecialStrokeWidth
        specialPointTextPaint.textSize = adjustedSpecialTextSize

        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scaleFactor, scaleFactor)

        geometry.connections.forEach { connection ->
            val paint = when (connection.style) {
                ConnectionStyle.SOLID -> solidLinePaint
                ConnectionStyle.DOTTED -> dottedLinePaint
            }
            val start = connection.start
            val end = connection.end
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }

        geometry.points.forEach { scaledPoint ->
            val baseCode = scaledPoint.point.codeInfo.baseCode
            if (baseCode == specialPointCode) {
                drawSpecialPoint(
                    canvas = canvas,
                    x = scaledPoint.x,
                    y = scaledPoint.y,
                    radius = adjustedSpecialPointRadius
                )
            } else {
                canvas.drawCircle(scaledPoint.x, scaledPoint.y, adjustedPointRadius, pointPaint)
            }

            val labelLines = PointLabelFormatter.buildLines(scaledPoint.point)
            drawMultilineText(
                labelLines,
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
        lines: List<String>,
        x: Float,
        y: Float,
        paint: Paint,
        lineSpacing: Float,
        canvas: Canvas
    ) {
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

    private fun drawSpecialPoint(canvas: Canvas, x: Float, y: Float, radius: Float) {
        canvas.drawCircle(x, y, radius, specialPointFillPaint)
        canvas.drawCircle(x, y, radius, specialPointStrokePaint)

        val metrics = specialPointTextPaint.fontMetrics
        val textY = y - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(DrawingStyle.SPECIAL_POINT_LETTER, x, textY, specialPointTextPaint)
    }
}
