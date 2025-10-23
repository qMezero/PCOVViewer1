package com.example.pcovviewer

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.pcovviewer.PcoParser.PcoPoint
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    private val style: SchemeStyle = SchemeStyles.default

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
                height = pageInfo.pageHeight.toFloat(),
                style = style
            ) ?: run {
                pdfDocument.close()
                return null
            }

            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            canvas.drawColor(style.backgroundColor)

            val pointPaint = style.createPointPaint()
            val linePaint = style.createLinePaint()
            val textPaint = style.createBaseTextPaint()

            SchemeRenderer.draw(
                canvas = canvas,
                geometry = geometry,
                style = style,
                pointPaint = pointPaint,
                linePaint = linePaint,
                textPaint = textPaint,
                pointRadius = style.basePointRadius,
                labelOffsetX = style.baseLabelOffsetX,
                labelOffsetY = style.baseLabelOffsetY,
                lineSpacing = style.baseLineSpacing
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

}
