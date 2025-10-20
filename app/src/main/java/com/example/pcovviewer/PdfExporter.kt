package com.example.pcovviewer

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
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
            }

            val linePaint = Paint().apply {
                color = Color.BLUE
                strokeWidth = 1.5f
                isAntiAlias = true
            }

            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }

            val spanX = maxX - minX
            val spanY = maxY - minY

            val pageWidth = pageInfo.pageWidth.toFloat()
            val pageHeight = pageInfo.pageHeight.toFloat()

            val scaleX = if (spanX == 0f) 1f else pageWidth / spanX
            val scaleY = if (spanY == 0f) 1f else pageHeight / spanY
            val scale = minOf(scaleX, scaleY)

            val offsetX = (pageWidth - spanX * scale) / 2f
            val offsetY = (pageHeight - spanY * scale) / 2f

            val scaledPoints = points.map { point ->
                val scaledX = offsetX + (point.x - minX) * scale
                val scaledY = offsetY + (maxY - point.y) * scale
                ScaledPoint(point, scaledX, scaledY)
            }

            drawConnections(canvas, scaledPoints, linePaint)
            drawPoints(canvas, scaledPoints, pointPaint, textPaint)

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
        points: List<ScaledPoint>,
        paint: Paint
    ) {
        for (i in points.indices) {
            val scaledPoint = points[i]
            val code = scaledPoint.point.code
            if (!code.contains("..")) {
                continue
            }

            val parts = code.split("..")
            val target = parts.getOrNull(1)?.trim().orEmpty()

            when {
                target.isEmpty() && i > 0 -> {
                    val previous = points[i - 1]
                    canvas.drawLine(
                        previous.x,
                        previous.y,
                        scaledPoint.x,
                        scaledPoint.y,
                        paint
                    )
                }

                target.isNotEmpty() -> {
                    val targetPoint = points.find { it.point.number.toString() == target }
                    if (targetPoint != null) {
                        canvas.drawLine(
                            scaledPoint.x,
                            scaledPoint.y,
                            targetPoint.x,
                            targetPoint.y,
                            paint
                        )
                    }
                }
            }
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

            val label = "${scaledPoint.point.number}\n${scaledPoint.point.code}"
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

    private data class ScaledPoint(
        val point: PcoPoint,
        val x: Float,
        val y: Float
    )
}
