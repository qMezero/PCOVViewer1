package com.example.pcovviewer

import com.example.pcovviewer.PcoParser.PcoPoint

object PointLabelFormatter {
    private val codesWithoutLabel: Set<String> = setOf("40", "42")

    fun buildLines(point: PcoPoint): List<String> {
        val lines = mutableListOf(point.number.toString())
        val rawCode = point.code.trim()
        val baseCode = point.codeInfo.baseCode
        if (rawCode.isNotEmpty() && baseCode !in codesWithoutLabel) {
            lines += rawCode
        }
        return lines
    }
}
