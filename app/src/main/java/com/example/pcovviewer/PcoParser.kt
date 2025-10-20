package com.example.pcovviewer

object PcoParser {

    data class PcoPoint(
        val number: Int,
        val code: String,
        val x: Float,
        val y: Float,
        val z: Float?,
        val attributes: Map<String, String> = emptyMap()
    ) {
        val codeInfo: CodeInfo = CodeInfo.parse(code)
    }

    fun parse(rawContent: String): List<PcoPoint> {
        val points = mutableListOf<PcoPoint>()

        var currentNumber: Int? = null
        var currentCode: String? = null
        var currentX: Float? = null
        var currentY: Float? = null
        var currentZ: Float? = null
        val currentAttributes = mutableMapOf<String, String>()

        fun flushPoint() {
            val number = currentNumber
            val x = currentX
            val y = currentY
            if (number != null && x != null && y != null) {
                points += PcoPoint(
                    number = number,
                    code = currentCode.orEmpty(),
                    x = x,
                    y = y,
                    z = currentZ,
                    attributes = currentAttributes.toMap()
                )
            }

            currentNumber = null
            currentCode = null
            currentX = null
            currentY = null
            currentZ = null
            currentAttributes.clear()
        }

        rawContent.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) {
                    return@forEach
                }

                val key = line.substring(0, separatorIndex).trim()
                val value = line.substring(separatorIndex + 1).trim()

                if (key.isEmpty() || value.isEmpty()) {
                    return@forEach
                }

                if (key == "5") {
                    flushPoint()
                    currentNumber = value.toIntOrNull()
                } else {
                    currentAttributes[key] = value
                    when (key) {
                        "4" -> currentCode = value
                        "37" -> currentX = value.asFloat()
                        "38" -> currentY = value.asFloat()
                        "39" -> currentZ = value.asFloat()
                    }
                }
            }

        flushPoint()

        return points
    }

    private fun String.asFloat(): Float? = replace(',', '.').toFloatOrNull()
}
