package com.example.pcovviewer

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.example.pcovviewer.PcoParser.PcoPoint

object PdfExporter {

    private var lastPdfUri: Uri? = null

    fun exportToPdf(context: Context, points: List<PcoPoint>, destination: Uri): Boolean {
        if (points.isEmpty()) {
            return false
        }

        return try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()

            val geometry = GeometryBuilder.build(
                points = points,
                width = pageInfo.pageWidth.toFloat(),
                height = pageInfo.pageHeight.toFloat()
            ) ?: run {
                pdfDocument.close()
                return false
            }

            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val pointPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                strokeWidth = 1.5f
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 10f
                isAntiAlias = true
                isLinearText = true
                isSubpixelText = true
            }

            val linePaint = Paint().apply {
                color = Color.BLUE
                strokeWidth = 1.5f
                isAntiAlias = true
            }

            drawConnections(canvas, geometry.connections, linePaint)
            drawPoints(canvas, geometry.points, pointPaint, textPaint)

            pdfDocument.finishPage(page)

            context.contentResolver.openOutputStream(destination)?.use { output ->
                pdfDocument.writeTo(output)
            } ?: return false

            pdfDocument.close()

            lastPdfUri = destination
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openLastPdf(context: Context, storedUri: Uri? = null): Boolean {
        val uri = lastPdfUri ?: storedUri ?: return false

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
            canvas.drawCircle(scaledPoint.x, scaledPoint.y, 3f, pointPaint)

            val label = "${scaledPoint.point.number}\n${scaledPoint.point.displayCode}"
            val lines = label.split("\n")
            lines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    scaledPoint.x + 4f,
                    scaledPoint.y - 4f + index * (textPaint.textSize + 1.5f),
                    textPaint
                )
            }
        }
    }
}

