package com.example.pcovviewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var loadButton: Button
    private lateinit var savePdfButton: Button
    private lateinit var openPdfButton: Button

    private var loadedPoints: List<PcoParser.PcoPoint> = emptyList()
    private val preferences by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    // Регистрируем обработчик выбора файла
    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    loadPcoFile(uri)
                } else {
                    Toast.makeText(this, "Файл не выбран", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val savePdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri == null) {
                Toast.makeText(this, "Файл не создан", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Игнорируем, если пермишены не поддерживаются
            }

            val success = PdfExporter.exportToPdf(this, loadedPoints, uri)
            if (success) {
                preferences.edit().putString(KEY_LAST_PDF_URI, uri.toString()).apply()
                Toast.makeText(this, "PDF сохранён", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Не удалось сохранить PDF", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        loadButton = findViewById(R.id.buttonLoadPco)
        savePdfButton = findViewById(R.id.buttonSavePdf)
        openPdfButton = findViewById(R.id.buttonOpenPdf)

        // Загрузка .pco
        loadButton.setOnClickListener { openFilePicker() }

        // Сохранение PDF
        savePdfButton.setOnClickListener {
            if (loadedPoints.isEmpty()) {
                Toast.makeText(this, "Нет данных для сохранения", Toast.LENGTH_SHORT).show()
            } else {
                val fileName = "drawing_${System.currentTimeMillis()}.pdf"
                savePdfLauncher.launch(fileName)
            }
        }

        // Открытие последнего PDF
        openPdfButton.setOnClickListener {
            val storedUri = preferences.getString(KEY_LAST_PDF_URI, null)?.let(Uri::parse)
            if (!PdfExporter.openLastPdf(this, storedUri)) {
                Toast.makeText(this, "PDF ещё не создан", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        openFileLauncher.launch(intent)
    }

    private fun loadPcoFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            val points = PcoParser.parse(content)

            loadedPoints = points
            drawingView.setData(points)

            Toast.makeText(this, "Загружено точек: ${points.size}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка загрузки файла: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

}

private const val PREFS_NAME = "pdf_export_prefs"
private const val KEY_LAST_PDF_URI = "last_pdf_uri"
