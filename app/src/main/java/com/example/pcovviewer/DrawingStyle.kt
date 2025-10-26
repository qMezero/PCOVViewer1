package com.example.pcovviewer

import android.graphics.Color

object DrawingStyle {
    const val POINT_COLOR: Int = Color.RED
    const val LINE_COLOR: Int = Color.BLUE
    const val TEXT_COLOR: Int = Color.DKGRAY

    const val BASE_POINT_RADIUS: Float = 4f
    const val BASE_STROKE_WIDTH: Float = 2f
    const val BASE_TEXT_SIZE: Float = 18f
    const val BASE_LABEL_OFFSET_X: Float = 6f
    const val BASE_LABEL_OFFSET_Y: Float = 6f
    const val BASE_LINE_SPACING: Float = 2f
    const val BASE_DASH_INTERVAL: Float = 12f
    const val BASE_DASH_GAP: Float = 12f

    const val BASE_SPECIAL_POINT_RADIUS: Float = 12f
    const val BASE_SPECIAL_POINT_STROKE_WIDTH: Float = 2f
    const val SPECIAL_POINT_FILL_COLOR: Int = Color.WHITE
    const val SPECIAL_POINT_STROKE_COLOR: Int = Color.BLACK
    const val SPECIAL_POINT_TEXT_COLOR: Int = Color.BLACK
    const val SPECIAL_POINT_TEXT_SCALE: Float = 2.2f

    private val specialPointLetters: Map<String, String> = mapOf(
        "40" to "К",
        "42" to "в"
    )

    val SPECIAL_POINT_CODES: Set<String> = specialPointLetters.keys

    fun specialPointLetter(code: String): String? = specialPointLetters[code]
}
