package com.example.pcovviewer

import com.example.pcovviewer.PcoParser.PcoPoint

object PointLabelFormatter {
    fun buildLines(point: PcoPoint): List<String> {
        val lines = mutableListOf(point.number.toString())
        val rawCode = point.code.trim()
        if (rawCode.isNotEmpty()) {
            lines += rawCode
        }
        return lines
    }
}
