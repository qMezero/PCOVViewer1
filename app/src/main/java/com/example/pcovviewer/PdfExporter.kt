package com.example.pcovviewer

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.pcovviewer.PcoParser.PcoPoint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

object PdfExporter {

    private var lastPdfFile: File? = null
    private var lastPdfUri: Uri? = null

    private val SMALL_SPECIAL_POINT_CODES = setOf("40", "42")

    data class ExportResult(val uri: Uri, val description: String)

    fun exportToPdf(
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

        val fileNameBase = baseFileName?.let { "$it" + "_proba" }
            ?: "drawing_${System.currentTimeMillis()}"
        val fileName = "$fileNameBase.pdf"

        return try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()

            val geometry = GeometryBuilder.build(
                points = points,
                width = pageInfo.pageWidth.toFloat(),
                height = pageInfo.pageHeight.toFloat()
            ) ?: run {
                pdfDocument.close()
                return null
            }

            val previewMetrics = context.resources.displayMetrics
            val previewGeometry = if (previewMetrics.widthPixels > 0 && previewMetrics.heightPixels > 0) {
                GeometryBuilder.build(
                    points = points,
                    width = previewMetrics.widthPixels.toFloat(),
                    height = previewMetrics.heightPixels.toFloat()
                )
            } else {
                null
            }

            val previewScale = previewGeometry?.scale ?: geometry.scale
            val relativeScale = if (previewScale > 0f) geometry.scale / previewScale else 1f
            val sizeScale = relativeScale

            val pointRadius = DrawingStyle.BASE_POINT_RADIUS * sizeScale
            val specialPointRadius = DrawingStyle.BASE_SPECIAL_POINT_RADIUS * sizeScale
            val strokeWidth = DrawingStyle.BASE_STROKE_WIDTH * sizeScale
            val specialStrokeWidth = DrawingStyle.BASE_SPECIAL_POINT_STROKE_WIDTH * sizeScale
            val textSize = DrawingStyle.BASE_TEXT_SIZE * sizeScale
            val labelOffsetX = DrawingStyle.BASE_LABEL_OFFSET_X * sizeScale
            val labelOffsetY = DrawingStyle.BASE_LABEL_OFFSET_Y * sizeScale
            val lineSpacing = DrawingStyle.BASE_LINE_SPACING * sizeScale

            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = DrawingStyle.POINT_COLOR
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DrawingStyle.resolvePointLabelColor(context)
                this.textSize = textSize
            }

            val specialPointFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = DrawingStyle.SPECIAL_POINT_FILL_COLOR
            }

            val specialPointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = DrawingStyle.SPECIAL_POINT_STROKE_COLOR
                this.strokeWidth = specialStrokeWidth
            }

            val specialPointTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DrawingStyle.SPECIAL_POINT_TEXT_COLOR
                textAlign = Paint.Align.CENTER
                this.textSize = specialPointRadius * DrawingStyle.SPECIAL_POINT_TEXT_SCALE
                typeface = DrawingStyle.SPECIAL_POINT_TYPEFACE
                isFakeBoldText = false
            }

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DrawingStyle.LINE_COLOR
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }

            val dashInterval = (DrawingStyle.BASE_DASH_INTERVAL * sizeScale).coerceAtLeast(1f)
            val dashGap = (DrawingStyle.BASE_DASH_GAP * sizeScale).coerceAtLeast(1f)
            val circleMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DrawingStyle.LINE_COLOR
                style = Paint.Style.FILL
            }
            val circleMarkerRadius = DrawingStyle.BASE_CIRCLE_MARKER_RADIUS * sizeScale
            val circleMarkerSpacing = DrawingStyle.BASE_CIRCLE_MARKER_SPACING * sizeScale

            drawConnections(
                canvas = canvas,
                connections = geometry.connections,
                paint = linePaint,
                dashInterval = dashInterval,
                dashGap = dashGap,
                circleMarkerPaint = circleMarkerPaint,
                circleMarkerRadius = circleMarkerRadius,
                circleMarkerSpacing = circleMarkerSpacing
            )
            drawPoints(
                canvas = canvas,
                points = geometry.points,
                pointPaint = pointPaint,
                textPaint = textPaint,
                pointRadius = pointRadius,
                specialPointFillPaint = specialPointFillPaint,
                specialPointStrokePaint = specialPointStrokePaint,
                specialPointTextPaint = specialPointTextPaint,
                specialPointRadius = specialPointRadius,
                labelOffsetX = labelOffsetX,
                labelOffsetY = labelOffsetY,
                lineSpacing = lineSpacing,
                showNumbers = showPointNumbers,
                showCodes = showPointCodes
            )

            pdfDocument.finishPage(page)

            val result = if (targetDirectoryUri != null) {
                val directory = DocumentFile.fromTreeUri(context, targetDirectoryUri)
                val documentFile = directory?.createFile("application/pdf", fileNameBase)
                    ?: run {
                        pdfDocument.close()
                        return null
                    }

                context.contentResolver.openOutputStream(documentFile.uri)?.use { output ->
                    pdfDocument.writeTo(output)
                } ?: run {
                    pdfDocument.close()
                    return null
                }

                pdfDocument.close()

                lastPdfUri = documentFile.uri
                lastPdfFile = null

                ExportResult(documentFile.uri, documentFile.name ?: fileName)
            } else {
                val file = File(context.getExternalFilesDir(null), fileName)
                FileOutputStream(file).use { output ->
                    pdfDocument.writeTo(output)
                }
                pdfDocument.close()

                lastPdfFile = file
                lastPdfUri = null

                ExportResult(Uri.fromFile(file), file.absolutePath)
            }

            result
        } catch (e: Exception) {
            null
        }
    }

    fun openLastPdf(context: Context): Boolean {
        lastPdfUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            return try {
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }

        val file = lastPdfFile ?: run {
            val pdfs = context.getExternalFilesDir(null)?.listFiles { f -> f.extension == "pdf" }
            val latest = pdfs?.maxByOrNull { it.lastModified() }
            lastPdfFile = latest
            lastPdfUri = null
            latest
        }

        if (file != null && file.exists()) {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            return try {
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }

        return false
    }

    private fun drawConnections(
        canvas: Canvas,
        connections: List<Connection>,
        paint: Paint,
        dashInterval: Float,
        dashGap: Float,
        circleMarkerPaint: Paint,
        circleMarkerRadius: Float,
        circleMarkerSpacing: Float
    ) {
        if (connections.isEmpty()) {
            return
        }

        // Build vector paths to keep the stroke commands in the PDF as geometry
        val solidPath = Path()
        val dottedPath = Path()
        var hasSolid = false
        var hasDotted = false
        val circleConnections = mutableListOf<Connection>()

        connections.forEach { connection ->
            val start = connection.start
            val end = connection.end
            when (connection.style) {
                ConnectionStyle.SOLID -> {
                    solidPath.moveTo(start.x, start.y)
                    solidPath.lineTo(end.x, end.y)
                    hasSolid = true
                }

                ConnectionStyle.DOTTED -> {
                    dottedPath.moveTo(start.x, start.y)
                    dottedPath.lineTo(end.x, end.y)
                    hasDotted = true
                }

                ConnectionStyle.CIRCLE_MARKERS -> {
                    solidPath.moveTo(start.x, start.y)
                    solidPath.lineTo(end.x, end.y)
                    hasSolid = true
                    circleConnections += connection
                }
            }
        }

        if (hasSolid) {
            paint.pathEffect = null
            canvas.drawPath(solidPath, paint)
        }

        if (hasDotted) {
            paint.pathEffect = DashPathEffect(floatArrayOf(dashInterval, dashGap), 0f)
            canvas.drawPath(dottedPath, paint)
            paint.pathEffect = null
        }

        if (circleConnections.isNotEmpty() && circleMarkerRadius > 0f && circleMarkerSpacing > 0f) {
            circleConnections.forEach { connection ->
                drawCircleMarkers(
                    canvas = canvas,
                    startX = connection.start.x,
                    startY = connection.start.y,
                    endX = connection.end.x,
                    endY = connection.end.y,
                    spacing = circleMarkerSpacing,
                    radius = circleMarkerRadius,
                    paint = circleMarkerPaint
                )
            }
        }
    }

    private fun drawCircleMarkers(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        spacing: Float,
        radius: Float,
        paint: Paint
    ) {
        if (spacing <= 0f || radius <= 0f) {
            return
        }

        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length <= 0f) {
            return
        }

        var distance = spacing
        while (distance < length) {
            val fraction = distance / length
            val x = startX + dx * fraction
            val y = startY + dy * fraction
            canvas.drawCircle(x, y, radius, paint)
            distance += spacing
        }
    }

    private fun drawPoints(
        canvas: Canvas,
        points: List<ScaledPoint>,
        pointPaint: Paint,
        textPaint: Paint,
        pointRadius: Float,
        specialPointFillPaint: Paint,
        specialPointStrokePaint: Paint,
        specialPointTextPaint: Paint,
        specialPointRadius: Float,
        labelOffsetX: Float,
        labelOffsetY: Float,
        lineSpacing: Float,
        showNumbers: Boolean,
        showCodes: Boolean
    ) {
        if (points.isEmpty()) {
            return
        }

        val specialPointCodes = DrawingStyle.SPECIAL_POINT_CODES
        val regularPoints = points.filterNot { it.point.codeInfo.baseCode in specialPointCodes }
        val specialPoints = points.filter { it.point.codeInfo.baseCode in specialPointCodes }

        if (regularPoints.isNotEmpty()) {
            // Circles are also drawn as vector paths so they stay sharp on zoom
            val path = Path()
            regularPoints.forEach { scaledPoint ->
                path.addCircle(scaledPoint.x, scaledPoint.y, pointRadius, Path.Direction.CW)
            }
            canvas.drawPath(path, pointPaint)
        }

        if (specialPoints.isNotEmpty()) {
            specialPoints.forEach { scaledPoint ->
                val baseCode = scaledPoint.point.codeInfo.baseCode
                val radius = if (baseCode in SMALL_SPECIAL_POINT_CODES) {
                    specialPointRadius / 3f
                } else {
                    specialPointRadius
                }

                if (radius <= 0f) {
                    return@forEach
                }

                canvas.drawCircle(scaledPoint.x, scaledPoint.y, radius, specialPointFillPaint)
                canvas.drawCircle(scaledPoint.x, scaledPoint.y, radius, specialPointStrokePaint)

                val letter = DrawingStyle.specialPointLetter(baseCode)
                if (letter != null) {
                    DrawingStyle.adjustSpecialPointTextSize(
                        paint = specialPointTextPaint,
                        letter = letter,
                        radius = radius
                    )
                    val metrics = specialPointTextPaint.fontMetrics
                    val centerY = scaledPoint.y - (metrics.ascent + metrics.descent) / 2f
                    val verticalOffsetFactor = DrawingStyle.specialPointLetterVerticalOffsetFactor(letter)
                    val textY = centerY - radius * verticalOffsetFactor
                    canvas.drawText(letter, scaledPoint.x, textY, specialPointTextPaint)
                }
            }
        }

        points.forEach { scaledPoint ->
            val labelLines = PointLabelFormatter.buildLines(
                point = scaledPoint.point,
                showNumbers = showNumbers,
                showCodes = showCodes
            )
            if (labelLines.isNotEmpty()) {
                drawMultilineText(
                    lines = labelLines,
                    x = scaledPoint.x + labelOffsetX,
                    y = scaledPoint.y - labelOffsetY,
                    paint = textPaint,
                    lineSpacing = lineSpacing,
                    canvas = canvas
                )
            }
        }
    }

    private fun drawMultilineText(
        lines: List<String>,
        x: Float,
        y: Float,
        paint: Paint,
        lineSpacing: Float,
        canvas: Canvas
    ) {
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, x, y + index * (paint.textSize + lineSpacing), paint)
        }
    }
}
