package com.example.pcovviewer

import com.example.pcovviewer.PcoParser.PcoPoint

object PointLabelFormatter {
    private val codesWithoutLabel: Set<String> = setOf("40", "42")

    fun buildLines(
        point: PcoPoint,
        showNumbers: Boolean,
        showCodes: Boolean
    ): List<String> {
        val lines = mutableListOf<String>()

        if (showNumbers) {
            lines += point.number.toString()
        }

        val rawCode = point.code.trim()
        val baseCode = point.codeInfo.baseCode
        if (showCodes && rawCode.isNotEmpty() && baseCode !in codesWithoutLabel) {
            lines += rawCode
        }
        return lines
    }
}
