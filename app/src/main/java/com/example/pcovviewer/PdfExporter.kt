package com.example.pcovviewer

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.pcovviewer.PcoParser.PcoPoint
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    private var lastPdfFile: File? = null

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

            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val pointPaint = Paint().apply {
                color = DrawingStyle.POINT_COLOR
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = DrawingStyle.TEXT_COLOR
                textSize = DrawingStyle.BASE_TEXT_SIZE
                isAntiAlias = true
                isLinearText = true
                isSubpixelText = true
            }

            val linePaint = Paint().apply {
                color = DrawingStyle.LINE_COLOR
                strokeWidth = DrawingStyle.BASE_STROKE_WIDTH
                isAntiAlias = true
            }

            drawConnections(canvas, geometry.connections, linePaint)
            drawPoints(canvas, geometry.points, pointPaint, textPaint)

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
        connections.forEach { (start, end) ->
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
    }

    private fun drawPoints(
        canvas: Canvas,
        points: List<ScaledPoint>,
        pointPaint: Paint,
        textPaint: Paint
    ) {
        points.forEach { scaledPoint ->
            canvas.drawCircle(scaledPoint.x, scaledPoint.y, DrawingStyle.BASE_POINT_RADIUS, pointPaint)

            val labelLines = PointLabelFormatter.buildLines(scaledPoint.point)
            drawMultilineText(
                labelLines,
                scaledPoint.x + DrawingStyle.BASE_LABEL_OFFSET_X,
                scaledPoint.y - DrawingStyle.BASE_LABEL_OFFSET_Y,
                textPaint,
                DrawingStyle.BASE_LINE_SPACING,
                canvas
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
