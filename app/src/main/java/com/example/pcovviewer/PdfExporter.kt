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

object PdfExporter {

    private var lastPdfFile: File? = null
    private var lastPdfUri: Uri? = null

    private const val PDF_POINT_RADIUS_MULTIPLIER = 0.25f
    private const val PDF_TEXT_SIZE_MULTIPLIER = 0.4f
    private const val PDF_DIGIT_EXTRA_SPACING_PX = 1f
    private const val PDF_STROKE_WIDTH_MULTIPLIER = 0.5f

    data class ExportResult(val uri: Uri, val description: String)

    fun exportToPdf(
        context: Context,
        points: List<PcoPoint>,
        targetDirectoryUri: Uri?,
        baseFileName: String?
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
            val clampedScale = relativeScale.coerceAtMost(1f)

            val pointRadius = DrawingStyle.BASE_POINT_RADIUS * clampedScale * PDF_POINT_RADIUS_MULTIPLIER
            val specialPointRadius = DrawingStyle.BASE_SPECIAL_POINT_RADIUS * clampedScale * PDF_POINT_RADIUS_MULTIPLIER
            val strokeWidth = DrawingStyle.BASE_STROKE_WIDTH * clampedScale * PDF_STROKE_WIDTH_MULTIPLIER
            val specialStrokeWidth = DrawingStyle.BASE_SPECIAL_POINT_STROKE_WIDTH * clampedScale * PDF_STROKE_WIDTH_MULTIPLIER
            val textSize = DrawingStyle.BASE_TEXT_SIZE * clampedScale * PDF_TEXT_SIZE_MULTIPLIER
            val specialPointTextSize = specialPointRadius * DrawingStyle.SPECIAL_POINT_TEXT_SCALE
            val labelOffsetX = DrawingStyle.BASE_LABEL_OFFSET_X * clampedScale * PDF_TEXT_SIZE_MULTIPLIER
            val labelOffsetY = DrawingStyle.BASE_LABEL_OFFSET_Y * clampedScale * PDF_TEXT_SIZE_MULTIPLIER
            val lineSpacing = DrawingStyle.BASE_LINE_SPACING * clampedScale * PDF_TEXT_SIZE_MULTIPLIER

            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = DrawingStyle.POINT_COLOR
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DrawingStyle.TEXT_COLOR
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
                this.textSize = specialPointTextSize
                isFakeBoldText = true
            }

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DrawingStyle.LINE_COLOR
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }

            val dashInterval = (DrawingStyle.BASE_DASH_INTERVAL * clampedScale).coerceAtLeast(1f)
            val dashGap = (DrawingStyle.BASE_DASH_GAP * clampedScale).coerceAtLeast(1f)

            drawConnections(canvas, geometry.connections, linePaint, dashInterval, dashGap)
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
                lineSpacing = lineSpacing
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
        dashGap: Float
    ) {
        if (connections.isEmpty()) {
            return
        }

        // Build vector paths to keep the stroke commands in the PDF as geometry
        val solidPath = Path()
        val dottedPath = Path()
        var hasSolid = false
        var hasDotted = false

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
        lineSpacing: Float
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
            val fillPath = Path()
            val strokePath = Path()
            specialPoints.forEach { scaledPoint ->
                fillPath.addCircle(scaledPoint.x, scaledPoint.y, specialPointRadius, Path.Direction.CW)
                strokePath.addCircle(scaledPoint.x, scaledPoint.y, specialPointRadius, Path.Direction.CW)
            }
            canvas.drawPath(fillPath, specialPointFillPaint)
            canvas.drawPath(strokePath, specialPointStrokePaint)

            specialPoints.forEach { scaledPoint ->
                val metrics = specialPointTextPaint.fontMetrics
                val textY = scaledPoint.y - (metrics.ascent + metrics.descent) / 2f
                val letter = DrawingStyle.specialPointLetter(scaledPoint.point.codeInfo.baseCode)
                if (letter != null) {
                    canvas.drawText(letter, scaledPoint.x, textY, specialPointTextPaint)
                }
            }
        }

        points.forEach { scaledPoint ->
            val labelLines = PointLabelFormatter.buildLines(scaledPoint.point)
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

    private fun drawMultilineText(
        lines: List<String>,
        x: Float,
        y: Float,
        paint: Paint,
        lineSpacing: Float,
        canvas: Canvas
    ) {
        val originalLetterSpacing = paint.letterSpacing
        val digitLetterSpacing = originalLetterSpacing + (PDF_DIGIT_EXTRA_SPACING_PX / paint.textSize)

        lines.forEachIndexed { index, line ->
            val isNumeric = line.all { it.isDigit() }
            paint.letterSpacing = if (isNumeric) digitLetterSpacing else originalLetterSpacing

            canvas.drawText(line, x, y + index * (paint.textSize + lineSpacing), paint)
        }

        paint.letterSpacing = originalLetterSpacing
    }
}
