package com.example.pcovviewer

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    private var lastPdfFile: File? = null

    fun exportToPdf(context: Context, points: List<PcoPoint>): File? {
        val file = File(context.getExternalFilesDir(null), "drawing_${System.currentTimeMillis()}.pdf")
        lastPdfFile = file

        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1.5f
        }

        if (points.size > 1) {
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }

            val scaleX = pageInfo.pageWidth / (maxX - minX)
            val scaleY = pageInfo.pageHeight / (maxY - minY)
            val scale = minOf(scaleX, scaleY)

            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val x1 = (p1.x - minX) * scale
                val y1 = pageInfo.pageHeight - ((p1.y - minY) * scale)
                val x2 = (p2.x - minX) * scale
                val y2 = pageInfo.pageHeight - ((p2.y - minY) * scale)
                canvas.drawLine(x1, y1, x2, y2, paint)
            }
        }

        pdfDocument.finishPage(page)
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        return file
    }


    fun openLastPdf(context: Context) {
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

            context.startActivity(intent)
        }
    }
}
