package com.example.pcovviewer

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.max

object DrawingStyle {
    const val POINT_COLOR: Int = Color.RED
    const val LINE_COLOR: Int = Color.BLUE
    const val TEXT_COLOR: Int = Color.DKGRAY

    const val BASE_POINT_RADIUS: Float = 1f
    const val BASE_STROKE_WIDTH: Float = 1f
    const val BASE_TEXT_SIZE: Float = 18f
    const val BASE_LABEL_OFFSET_X: Float = 6f
    const val BASE_LABEL_OFFSET_Y: Float = 6f
    const val BASE_LINE_SPACING: Float = 2f
    const val BASE_DASH_INTERVAL: Float = 2.25f
    const val BASE_DASH_GAP: Float = 3f

    const val BASE_CIRCLE_MARKER_RADIUS: Float = 6f
    const val BASE_CIRCLE_MARKER_SPACING: Float = 28f
    const val CIRCLE_MARKER_FILL_COLOR: Int = Color.WHITE
    const val BASE_CIRCLE_MARKER_STROKE_WIDTH: Float = 3f

    const val BASE_SPECIAL_POINT_RADIUS: Float = 12f
    const val BASE_SPECIAL_POINT_STROKE_WIDTH: Float = 1.5f / 4f
    const val SPECIAL_POINT_FILL_COLOR: Int = Color.WHITE
    const val SPECIAL_POINT_STROKE_COLOR: Int = Color.BLACK
    const val SPECIAL_POINT_TEXT_COLOR: Int = Color.BLACK
    const val SPECIAL_POINT_TEXT_SCALE: Float = 2.2f
    private const val SPECIAL_POINT_TEXT_TARGET_DIAMETER_MULTIPLIER: Float = 0.9f

    val SPECIAL_POINT_TYPEFACE: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

    private val specialPointLetters: Map<String, String> = mapOf(
        "40" to "К",
        "42" to "В"
    )

    private val specialPointLetterVerticalOffsetFactors: Map<String, Float> = emptyMap()

    val SPECIAL_POINT_CODES: Set<String> = specialPointLetters.keys

    fun specialPointLetter(code: String): String? = specialPointLetters[code]

    fun specialPointLetterVerticalOffsetFactor(letter: String): Float =
        specialPointLetterVerticalOffsetFactors[letter].orZero()

    fun resolvePointLabelColor(context: Context): Int {
        val attrs = intArrayOf(R.attr.pointLabelColor)
        val typedArray = context.obtainStyledAttributes(attrs)
        return try {
            typedArray.getColor(0, TEXT_COLOR)
        } finally {
            typedArray.recycle()
        }
    }

    fun adjustSpecialPointTextSize(
        paint: Paint,
        letter: String,
        radius: Float,
        strokeWidth: Float
    ) {
        val effectiveRadius = (radius - strokeWidth / 2f).coerceAtLeast(0f)
        if (letter.isEmpty()) {
            paint.textSize = effectiveRadius * SPECIAL_POINT_TEXT_SCALE
            return
        }

        val bounds = Rect()
        val initialTextSize = effectiveRadius * SPECIAL_POINT_TEXT_SCALE
        paint.textSize = initialTextSize
        paint.getTextBounds(letter, 0, letter.length, bounds)

        val maxDimension = max(bounds.width(), bounds.height())
        if (maxDimension <= 0) {
            return
        }

        val targetDimension = effectiveRadius * 2f * SPECIAL_POINT_TEXT_TARGET_DIAMETER_MULTIPLIER
        val scale = targetDimension / maxDimension
        paint.textSize = initialTextSize * scale
    }
}

private fun Float?.orZero(): Float = this ?: 0f
