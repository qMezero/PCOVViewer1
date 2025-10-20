package com.example.pcovviewer

object PointLabelFormatter {

    fun buildLabel(point: PcoParser.PcoPoint, connectedPointNumbers: List<Int>): String {
        val lines = mutableListOf<String>()

        lines += point.number.toString()

        val connectionLines = connectedPointNumbers
            .distinct()
            .sorted()
            .map { targetNumber ->
                if (point.codeInfo.baseCode.isNotEmpty()) {
                    "${point.codeInfo.baseCode}..$targetNumber"
                } else {
                    "..$targetNumber"
                }
            }

        if (connectionLines.isNotEmpty()) {
            lines += connectionLines
        } else if (point.codeInfo.baseCode.isNotEmpty()) {
            lines += point.codeInfo.baseCode
        } else {
            val rawCode = point.code.trim()
            if (rawCode.isNotEmpty()) {
                lines += rawCode
            }
        }

        return lines.joinToString(separator = "\n")
    }
}
