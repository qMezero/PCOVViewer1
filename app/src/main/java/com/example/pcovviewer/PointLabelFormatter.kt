package com.example.pcovviewer

import com.example.pcovviewer.PcoParser.PcoPoint

object PointLabelFormatter {
    fun buildLines(point: PcoPoint): List<String> {
        val lines = mutableListOf(point.number.toString())
        val baseCode = point.codeInfo.baseCode
        if (baseCode.isNotEmpty() && !baseCode.startsWith('7')) {
            lines += baseCode
        }
        return lines
    }
}
