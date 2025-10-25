package com.example.pcovviewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var loadButton: Button
    private lateinit var savePdfButton: Button
    private lateinit var openPdfButton: Button
    private lateinit var layerButton: Button

    private var loadedPoints: List<PcoParser.PcoPoint> = emptyList()
    private var visiblePoints: List<PcoParser.PcoPoint> = emptyList()
    private val layerStates = mutableListOf<LayerState>()

    private data class LayerState(
        val baseCode: String?,
        val count: Int,
        var isEnabled: Boolean
    )

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        loadButton = findViewById(R.id.buttonLoadPco)
        savePdfButton = findViewById(R.id.buttonSavePdf)
        openPdfButton = findViewById(R.id.buttonOpenPdf)
        layerButton = findViewById(R.id.buttonLayers)

        layerButton.setOnClickListener { showLayerSelectionDialog() }
        updateLayerButtonState()

        // Загрузка .pco
        loadButton.setOnClickListener { openFilePicker() }

        // Сохранение PDF
        savePdfButton.setOnClickListener {
            if (visiblePoints.isEmpty()) {
                Toast.makeText(this, "Нет данных для сохранения", Toast.LENGTH_SHORT).show()
            } else {
                val file = PdfExporter.exportToPdf(this, visiblePoints)
                if (file != null) {
                    Toast.makeText(
                        this,
                        "PDF сохранён: ${file.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "Не удалось сохранить PDF", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Открытие последнего PDF
        openPdfButton.setOnClickListener {
            if (!PdfExporter.openLastPdf(this)) {
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
            rebuildLayerStates()
            applyLayerFilter()

            Toast.makeText(this, "Загружено точек: ${points.size}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка загрузки файла: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun rebuildLayerStates() {
        layerStates.clear()

        if (loadedPoints.isEmpty()) {
            return
        }

        val groups = loadedPoints.groupBy { point ->
            point.codeInfo.baseCode.takeIf { it.isNotBlank() }
        }

        val sortedKeys = groups.keys.sortedWith(layerComparator)
        sortedKeys.forEach { key ->
            val points = groups[key].orEmpty()
            layerStates += LayerState(
                baseCode = key,
                count = points.size,
                isEnabled = true
            )
        }
    }

    private fun showLayerSelectionDialog() {
        if (layerStates.isEmpty()) {
            Toast.makeText(this, R.string.layers_toast_no_layers, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = layerStates.map { state ->
            val name = layerDisplayName(state)
            getString(R.string.layers_dialog_item_format, name, state.count)
        }.toTypedArray()
        val checkedItems = layerStates.map { it.isEnabled }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.layers_dialog_title)
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(R.string.layers_dialog_apply) { _, _ ->
                layerStates.forEachIndexed { index, layer ->
                    layer.isEnabled = checkedItems[index]
                }
                applyLayerFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.layers_dialog_select_all) { _, _ ->
                layerStates.forEach { it.isEnabled = true }
                applyLayerFilter()
            }
            .show()
    }

    private fun applyLayerFilter() {
        if (loadedPoints.isEmpty()) {
            visiblePoints = emptyList()
            drawingView.setData(visiblePoints)
            updateLayerButtonState()
            return
        }

        visiblePoints = if (layerStates.isEmpty()) {
            loadedPoints
        } else {
            val activeLayers = layerStates.filter { it.isEnabled }.map { it.baseCode }.toSet()
            if (activeLayers.isEmpty()) {
                emptyList()
            } else {
                loadedPoints.filter { point ->
                    val baseCode = point.codeInfo.baseCode.takeIf { it.isNotBlank() }
                    activeLayers.contains(baseCode)
                }
            }
        }

        drawingView.setData(visiblePoints)
        updateLayerButtonState()

        if (visiblePoints.isEmpty() && loadedPoints.isNotEmpty()) {
            Toast.makeText(this, R.string.layers_toast_no_visible_points, Toast.LENGTH_SHORT).show()
        }
    }

    private fun layerDisplayName(layer: LayerState): String {
        return layer.baseCode?.let { code ->
            getString(R.string.layers_layer_with_code, code)
        } ?: getString(R.string.layers_layer_without_code)
    }

    private fun updateLayerButtonState() {
        if (!::layerButton.isInitialized) {
            return
        }

        if (layerStates.isEmpty()) {
            layerButton.isEnabled = true
            layerButton.alpha = 0.6f
            layerButton.text = getString(R.string.layers_button_default)
        } else {
            layerButton.isEnabled = true
            layerButton.alpha = 1f
            val activeCount = layerStates.count { it.isEnabled }
            val totalCount = layerStates.size
            layerButton.text = getString(R.string.layers_button_with_count, activeCount, totalCount)
        }
    }

    private val layerComparator = Comparator<String?> { first, second ->
        if (first == null && second == null) {
            0
        } else if (first == null) {
            -1
        } else if (second == null) {
            1
        } else {
            val firstInt = first.toIntOrNull()
            val secondInt = second.toIntOrNull()
            when {
                firstInt != null && secondInt != null -> firstInt.compareTo(secondInt)
                firstInt != null -> -1
                secondInt != null -> 1
                else -> first.compareTo(second)
            }
        }
    }
}
