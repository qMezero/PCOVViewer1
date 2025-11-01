package com.example.pcovviewer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.pcovviewer.PcoParser.PcoPoint
import com.example.pcovviewer.normalizeConnectionCode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object DwgExporter {

    data class ExportResult(val uri: Uri, val description: String)

    private const val DEFAULT_SCALE_REFERENCE = 1000f
    private val asciiCharset = Charsets.UTF_8

    fun exportToDwg(
        context: Context,
        points: List<PcoPoint>,
        targetDirectoryUri: Uri?,
        baseFileName: String?,
        showPointNumbers: Boolean,
        showPointCodes: Boolean
    ): ExportResult? {
        if (points.isEmpty()) {
            return null
        }

        val fileNameBase = baseFileName?.let { "${it}_proba" } ?: "drawing_${System.currentTimeMillis()}"
        val fileName = "$fileNameBase.dwg"

        val geometry = GeometryBuilder.build(points, DEFAULT_SCALE_REFERENCE, DEFAULT_SCALE_REFERENCE)
        val connections = geometry?.connections.orEmpty()

        val dxfContent = buildString {
            append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
            append("0\nSECTION\n2\nTABLES\n0\nENDSEC\n")
            append("0\nSECTION\n2\nENTITIES\n")

            connections.forEach { connection ->
                val start = connection.start.point
                val end = connection.end.point
                append("0\nLINE\n8\n0\n")
                appendCoordinateTriplet("10", start)
                appendCoordinateTriplet("11", end)
            }

            points.forEach { point ->
                append("0\nPOINT\n8\n0\n")
                appendCoordinateTriplet("10", point)

                val labels = mutableListOf<String>()
                if (showPointNumbers) {
                    labels += point.number.toString()
                }
                if (showPointCodes && point.code.isNotBlank()) {
                    labels += normalizeConnectionCode(point.code)
                }
                val labelText = labels.joinToString(separator = " ") { it.replace('\n', ' ') }
                if (labelText.isNotEmpty()) {
                    append("0\nTEXT\n8\n0\n")
                    appendCoordinateTriplet("10", point)
                    append("40\n1.5\n1\n")
                    append(labelText)
                    append('\n')
                }
            }

            append("0\nENDSEC\n0\nEOF\n")
        }

        return try {
            if (targetDirectoryUri != null) {
                val directory = DocumentFile.fromTreeUri(context, targetDirectoryUri)
                val documentFile = directory?.createFile("application/octet-stream", fileName)
                    ?: return null

                context.contentResolver.openOutputStream(documentFile.uri)?.use { output ->
                    output.write(dxfContent.toByteArray(asciiCharset))
                } ?: return null

                ExportResult(documentFile.uri, documentFile.name ?: fileName)
            } else {
                val file = File(context.getExternalFilesDir(null), fileName)
                FileOutputStream(file).use { output ->
                    output.write(dxfContent.toByteArray(asciiCharset))
                }

                ExportResult(Uri.fromFile(file), file.absolutePath)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun StringBuilder.appendCoordinateTriplet(prefix: String, point: PcoPoint) {
        val (yCode, zCode) = if (prefix == "11") {
            "21" to "31"
        } else {
            "20" to "30"
        }

        append(prefix)
        append('\n')
        append(formatCoordinate(point.x))
        append('\n')
        append(yCode)
        append('\n')
        append(formatCoordinate(point.y))
        append('\n')
        append(zCode)
        append('\n')
        append(formatCoordinate(point.z ?: 0f))
        append('\n')
    }

    private fun formatCoordinate(value: Float): String =
        String.format(Locale.US, "%.3f", value)
}
