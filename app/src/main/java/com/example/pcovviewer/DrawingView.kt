package com.example.pcovviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
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

        solidLinePaint.strokeWidth = adjustedStrokeWidth
        solidLinePaint.pathEffect = null
        dottedLinePaint.strokeWidth = adjustedStrokeWidth
        dottedLinePaint.pathEffect = DashPathEffect(
            floatArrayOf(baseDashInterval / scaleFactor, baseDashGap / scaleFactor),
            0f
        )

        textPaint.textSize = adjustedTextSize

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

        val occupiedLabelAreas = mutableListOf<RectF>()

        geometry.points.forEach { scaledPoint ->
            canvas.drawCircle(scaledPoint.x, scaledPoint.y, adjustedPointRadius, pointPaint)

            val labelLines = PointLabelFormatter.buildLines(scaledPoint.point)
            val placement = findLabelPlacement(
                scaledPoint.x,
                scaledPoint.y,
                labelLines,
                textPaint,
                lineSpacing,
                labelOffsetX,
                labelOffsetY,
                occupiedLabelAreas
            )

            occupiedLabelAreas.add(placement.area)

            drawMultilineText(
                labelLines,
                placement.x,
                placement.y,
                textPaint,
                lineSpacing,
                canvas
            )
        }

        canvas.restore()
    }

    private fun findLabelPlacement(
        pointX: Float,
        pointY: Float,
        lines: List<String>,
        paint: Paint,
        lineSpacing: Float,
        baseOffsetX: Float,
        baseOffsetY: Float,
        occupiedAreas: List<RectF>
    ): LabelPlacement {
        if (lines.isEmpty()) {
            val fallbackRect = RectF(pointX, pointY, pointX, pointY)
            return LabelPlacement(pointX, pointY, fallbackRect)
        }

        val maxWidth = lines.maxOf { paint.measureText(it) }
        val fontMetrics = paint.fontMetrics

        val orientations = listOf(
            LabelOrientation.TOP_RIGHT,
            LabelOrientation.TOP_LEFT,
            LabelOrientation.BOTTOM_RIGHT,
            LabelOrientation.BOTTOM_LEFT
        )

        val maxMultiplier = 4

        for (multiplier in 1..maxMultiplier) {
            val horizontalOffset = baseOffsetX * multiplier
            val verticalOffset = baseOffsetY * multiplier

            for (orientation in orientations) {
                val (x, y) = when (orientation) {
                    LabelOrientation.TOP_RIGHT -> pointX + horizontalOffset to pointY - verticalOffset
                    LabelOrientation.TOP_LEFT -> pointX - horizontalOffset - maxWidth to pointY - verticalOffset
                    LabelOrientation.BOTTOM_RIGHT -> pointX + horizontalOffset to pointY + verticalOffset - fontMetrics.ascent
                    LabelOrientation.BOTTOM_LEFT -> pointX - horizontalOffset - maxWidth to pointY + verticalOffset - fontMetrics.ascent
                }

                val area = computeLabelArea(x, y, lines, paint, lineSpacing, maxWidth, fontMetrics)

                val intersects = occupiedAreas.any { RectF.intersects(it, area) }
                if (!intersects) {
                    return LabelPlacement(x, y, area)
                }
            }
        }

        val fallbackX = pointX + baseOffsetX
        val fallbackY = pointY - baseOffsetY
        val fallbackArea = computeLabelArea(
            fallbackX,
            fallbackY,
            lines,
            paint,
            lineSpacing,
            maxWidth,
            fontMetrics
        )
        return LabelPlacement(fallbackX, fallbackY, fallbackArea)
    }

    private fun computeLabelArea(
        x: Float,
        y: Float,
        lines: List<String>,
        paint: Paint,
        lineSpacing: Float,
        maxWidth: Float,
        fontMetrics: Paint.FontMetrics
    ): RectF {
        val area = RectF()

        val firstBaseline = y
        val lastBaseline = y + (lines.size - 1) * (paint.textSize + lineSpacing)
        val top = firstBaseline + fontMetrics.ascent
        val bottom = lastBaseline + fontMetrics.descent

        area.set(x, top, x + maxWidth, bottom)
        return area
    }

    private data class LabelPlacement(val x: Float, val y: Float, val area: RectF)

    private enum class LabelOrientation {
        TOP_RIGHT,
        TOP_LEFT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT
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
}
