package com.example.pcovviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.hypot

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
        color = DrawingStyle.SPECIAL_POINT_STROKE_COLOR
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val specialPointTextPaint = Paint().apply {
        color = DrawingStyle.SPECIAL_POINT_TEXT_COLOR
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = DrawingStyle.SPECIAL_POINT_TYPEFACE
        isFakeBoldText = false
    }

    private val specialPointTextBounds = Rect()

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

    private val circleMarkerPaint = Paint().apply {
        color = DrawingStyle.LINE_COLOR
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = DrawingStyle.resolvePointLabelColor(context)
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
    private val specialPointTextScale = DrawingStyle.SPECIAL_POINT_TEXT_SCALE
    private val baseCircleMarkerRadius = DrawingStyle.BASE_CIRCLE_MARKER_RADIUS
    private val baseCircleMarkerSpacing = DrawingStyle.BASE_CIRCLE_MARKER_SPACING

    private var showPointNumbers = true
    private var showPointCodes = true

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

    fun setLabelVisibility(showNumbers: Boolean, showCodes: Boolean) {
        showPointNumbers = showNumbers
        showPointCodes = showCodes
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
        val adjustedSpecialTextSize = adjustedSpecialPointRadius * specialPointTextScale
        val labelOffsetX = baseLabelOffsetX / scaleFactor
        val labelOffsetY = baseLabelOffsetY / scaleFactor
        val lineSpacing = baseLineSpacing / scaleFactor
        val circleMarkerRadius = baseCircleMarkerRadius / scaleFactor
        val circleMarkerSpacing = baseCircleMarkerSpacing / scaleFactor

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
            val start = connection.start
            val end = connection.end
            when (connection.style) {
                ConnectionStyle.SOLID -> {
                    canvas.drawLine(start.x, start.y, end.x, end.y, solidLinePaint)
                }

                ConnectionStyle.DOTTED -> {
                    canvas.drawLine(start.x, start.y, end.x, end.y, dottedLinePaint)
                }

                ConnectionStyle.CIRCLE_MARKERS -> {
                    canvas.drawLine(start.x, start.y, end.x, end.y, solidLinePaint)
                    drawCircleMarkers(
                        canvas = canvas,
                        startX = start.x,
                        startY = start.y,
                        endX = end.x,
                        endY = end.y,
                        spacing = circleMarkerSpacing,
                        radius = circleMarkerRadius,
                        paint = circleMarkerPaint
                    )
                }
            }
        }

        geometry.points.forEach { scaledPoint ->
            val baseCode = scaledPoint.point.codeInfo.baseCode
            val specialPointLetter = DrawingStyle.specialPointLetter(baseCode)
            if (specialPointLetter != null) {
                drawSpecialPoint(
                    canvas = canvas,
                    x = scaledPoint.x,
                    y = scaledPoint.y,
                    radius = adjustedSpecialPointRadius,
                    letter = specialPointLetter
                )
            } else {
                canvas.drawCircle(scaledPoint.x, scaledPoint.y, adjustedPointRadius, pointPaint)
            }

            val labelLines = PointLabelFormatter.buildLines(
                point = scaledPoint.point,
                showNumbers = showPointNumbers,
                showCodes = showPointCodes
            )
            if (labelLines.isNotEmpty()) {
                drawMultilineText(
                    labelLines,
                    scaledPoint.x + labelOffsetX,
                    scaledPoint.y - labelOffsetY,
                    textPaint,
                    lineSpacing,
                    canvas
                )
            }
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

    private fun drawCircleMarkers(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        spacing: Float,
        radius: Float,
        paint: Paint
    ) {
        if (spacing <= 0f || radius <= 0f) {
            return
        }

        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length <= 0f) {
            return
        }

        var distance = spacing
        while (distance < length) {
            val fraction = distance / length
            val x = startX + dx * fraction
            val y = startY + dy * fraction
            canvas.drawCircle(x, y, radius, paint)
            distance += spacing
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

    private fun drawSpecialPoint(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        letter: String
    ) {
        canvas.drawCircle(x, y, radius, specialPointFillPaint)
        canvas.drawCircle(x, y, radius, specialPointStrokePaint)

        DrawingStyle.adjustSpecialPointTextSize(
            paint = specialPointTextPaint,
            letter = letter,
            radius = radius,
            strokeWidth = specialPointStrokePaint.strokeWidth
        )
        val textY = if (letter.isNotEmpty()) {
            specialPointTextPaint.getTextBounds(letter, 0, letter.length, specialPointTextBounds)
            y - (specialPointTextBounds.top + specialPointTextBounds.bottom) / 2f
        } else {
            val metrics = specialPointTextPaint.fontMetrics
            y - (metrics.ascent + metrics.descent) / 2f
        }
        val verticalOffsetFactor = DrawingStyle.specialPointLetterVerticalOffsetFactor(letter)
        val adjustedTextY = textY - radius * verticalOffsetFactor
        canvas.drawText(letter, x, adjustedTextY, specialPointTextPaint)
    }
}
