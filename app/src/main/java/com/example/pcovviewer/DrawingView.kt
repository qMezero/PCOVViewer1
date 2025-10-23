package com.example.pcovviewer

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val style: SchemeStyle = SchemeStyles.default

    init {
        setBackgroundColor(style.backgroundColor)
    }

    private val style = SchemeStyles.default

    private var points: List<PcoParser.PcoPoint> = emptyList()

    private val pointPaint = style.createPointPaint()
    private val linePaint = style.createLinePaint()
    private val textPaint = style.createBaseTextPaint()
    private val baseLineStrokeWidth = linePaint.strokeWidth

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

        val geometry = GeometryBuilder.build(points, width.toFloat(), height.toFloat(), style) ?: return

        val adjustedStrokeWidth = baseLineStrokeWidth / scaleFactor
        val adjustedStrokeWidth = style.baseStrokeWidth / scaleFactor
        val adjustedTextSize = style.baseTextSize / scaleFactor
        val adjustedPointRadius = style.basePointRadius / scaleFactor
        val labelOffsetX = style.baseLabelOffsetX / scaleFactor
        val labelOffsetY = style.baseLabelOffsetY / scaleFactor
        val lineSpacing = style.baseLineSpacing / scaleFactor

        linePaint.strokeWidth = adjustedStrokeWidth
        textPaint.textSize = adjustedTextSize

        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scaleFactor, scaleFactor)

        SchemeRenderer.draw(
            canvas = canvas,
            geometry = geometry,
            style = style,
            pointPaint = pointPaint,
            linePaint = linePaint,
            textPaint = textPaint,
            pointRadius = adjustedPointRadius,
            labelOffsetX = labelOffsetX,
            labelOffsetY = labelOffsetY,
            lineSpacing = lineSpacing
        )

        canvas.restore()
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
