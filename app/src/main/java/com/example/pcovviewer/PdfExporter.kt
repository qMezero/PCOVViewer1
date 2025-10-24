package com.example.pcovviewer

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Path
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.pcovviewer.PcoParser.PcoPoint
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    private var lastPdfFile: File? = null

    private const val PDF_POINT_RADIUS_MULTIPLIER = 0.5f
    private const val PDF_TEXT_SIZE_MULTIPLIER = 0.5f

    fun exportToPdf(context: Context, points: List<PcoPoint>): File? {
        if (points.isEmpty()) {
            return null
        }

        val file = File(context.getExternalFilesDir(null), "drawing_${System.currentTimeMillis()}.pdf")

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
            val strokeWidth = DrawingStyle.BASE_STROKE_WIDTH * clampedScale
            val textSize = DrawingStyle.BASE_TEXT_SIZE * clampedScale * PDF_TEXT_SIZE_MULTIPLIER
            val labelOffsetX = DrawingStyle.BASE_LABEL_OFFSET_X * clampedScale * PDF_TEXT_SIZE_MULTIPLIER
            val labelOffsetY = DrawingStyle.BASE_LABEL_OFFSET_Y * clampedScale * PDF_TEXT_SIZE_MULTIPLIER
            val lineSpacing = DrawingStyle.BASE_LINE_SPACING * clampedScale * PDF_TEXT_SIZE_MULTIPLIER

            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.RED
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DrawingStyle.TEXT_COLOR
                this.textSize = textSize
                textSize = textSize
            }

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DrawingStyle.LINE_COLOR
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeWidth = strokeWidth
            }

            drawConnections(canvas, geometry.connections, linePaint)
            drawPoints(
                canvas = canvas,
                points = geometry.points,
                pointPaint = pointPaint,
                textPaint = textPaint,
                pointRadius = pointRadius,
                labelOffsetX = labelOffsetX,
                labelOffsetY = labelOffsetY,
                lineSpacing = lineSpacing
            )

            pdfDocument.finishPage(page)
            FileOutputStream(file).use { output ->
                pdfDocument.writeTo(output)
            }
            pdfDocument.close()

            lastPdfFile = file
            file
        } catch (e: Exception) {
            null
        }
    }

    fun openLastPdf(context: Context): Boolean {
        val file = lastPdfFile ?: run {
            val pdfs = context.getExternalFilesDir(null)?.listFiles { f -> f.extension == "pdf" }
            pdfs?.maxByOrNull { it.lastModified() }
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
        connections: List<Pair<ScaledPoint, ScaledPoint>>,
        paint: Paint
    ) {
        if (connections.isEmpty()) {
            return
        }

        // Build a single vector path to keep the stroke commands in the PDF as geometry
        val path = Path()
        connections.forEach { (start, end) ->
            path.moveTo(start.x, start.y)
            path.lineTo(end.x, end.y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPoints(
        canvas: Canvas,
        points: List<ScaledPoint>,
        pointPaint: Paint,
        textPaint: Paint,
        pointRadius: Float,
        labelOffsetX: Float,
        labelOffsetY: Float,
        lineSpacing: Float
    ) {
        if (points.isEmpty()) {
            return
        }

        // Circles are also drawn as vector paths so they stay sharp on zoom
        val path = Path()
        points.forEach { scaledPoint ->
            path.addCircle(scaledPoint.x, scaledPoint.y, pointRadius, Path.Direction.CW)
        }
        canvas.drawPath(path, pointPaint)

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
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, x, y + index * (paint.textSize + lineSpacing), paint)
        }
    }
}
