package com.example.pcovviewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ExpandableListView
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var loadButton: Button
    private lateinit var savePdfButton: Button
    private lateinit var saveDwgButton: Button
    private lateinit var openPdfButton: Button
    private lateinit var layerButton: Button
    private lateinit var themeButton: ImageButton

    private var loadedPoints: List<PcoParser.PcoPoint> = emptyList()
    private var visiblePoints: List<PcoParser.PcoPoint> = emptyList()
    private val layerStates = mutableListOf<LayerState>()
    private enum class ExportType { PDF, DWG }

    private var pendingExportType: ExportType? = null
    private var currentPcoFileName: String? = null
    private var showPointNumbers = true
    private var showPointCodes = true

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

    private val exportDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val requestedType = pendingExportType
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {
                    // Игнорируем невозможность сохранить разрешения и продолжаем без них.
                }
                saveExportDirectory(uri)

                if (requestedType != null && visiblePoints.isNotEmpty()) {
                    when (requestedType) {
                        ExportType.PDF -> exportVisiblePointsToPdf(uri)
                        ExportType.DWG -> exportVisiblePointsToDwg(uri)
                    }
                } else {
                    Toast.makeText(this, R.string.save_pdf_directory_saved, Toast.LENGTH_SHORT).show()
                }
            } else if (requestedType != null) {
                Toast.makeText(this, R.string.save_pdf_directory_not_selected, Toast.LENGTH_SHORT).show()
            }

            pendingExportType = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        loadButton = findViewById(R.id.buttonLoadPco)
        savePdfButton = findViewById(R.id.buttonSavePdf)
        saveDwgButton = findViewById(R.id.buttonSaveDwg)
        openPdfButton = findViewById(R.id.buttonOpenPdf)
        layerButton = findViewById(R.id.buttonLayers)
        themeButton = findViewById(R.id.buttonTheme)

        drawingView.setLabelVisibility(showPointNumbers, showPointCodes)

        layerButton.setOnClickListener { showLayerSelectionDialog() }
        updateLayerButtonState()
        themeButton.setOnClickListener { showThemeSelectionDialog() }
        updateThemeButtonState()

        // Загрузка .pco
        loadButton.setOnClickListener { openFilePicker() }

        // Сохранение PDF
        savePdfButton.setOnClickListener {
            handleExportRequest(type = ExportType.PDF, forceDirectorySelection = false)
        }
        savePdfButton.setOnLongClickListener {
            handleExportRequest(type = ExportType.PDF, forceDirectorySelection = true)
            true
        }

        saveDwgButton.setOnClickListener {
            handleExportRequest(type = ExportType.DWG, forceDirectorySelection = false)
        }
        saveDwgButton.setOnLongClickListener {
            handleExportRequest(type = ExportType.DWG, forceDirectorySelection = true)
            true
        }

        // Открытие последнего PDF
        openPdfButton.setOnClickListener {
            if (!PdfExporter.openLastPdf(this)) {
                Toast.makeText(this, "PDF ещё не создан", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateThemeButtonState() {
        val themeName = ThemeManager.getThemeDisplayName(this)
        val description = getString(R.string.button_select_theme_current, themeName)
        themeButton.contentDescription = description
        ViewCompat.setTooltipText(themeButton, description)
    }

    private fun showThemeSelectionDialog() {
        val themes = ThemeManager.AppTheme.values()
        var selectedTheme = ThemeManager.getSelectedTheme(this)
        val themeOptions = ThemeManager.getThemeOptions(this)
        val currentIndex = ThemeManager.getSelectedThemeIndex(this)

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_dialog_title)
            .setSingleChoiceItems(themeOptions, currentIndex) { _, which ->
                selectedTheme = themes[which]
            }
            .setPositiveButton(R.string.theme_dialog_apply) { _, _ ->
                ThemeManager.selectTheme(this, selectedTheme)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleExportRequest(type: ExportType, forceDirectorySelection: Boolean) {
        if (visiblePoints.isEmpty()) {
            Toast.makeText(this, R.string.save_pdf_no_data, Toast.LENGTH_SHORT).show()
            return
        }

        val savedDirectory = getSavedExportDirectory()
        if (forceDirectorySelection || savedDirectory == null) {
            pendingExportType = type
            val messageRes = when (type) {
                ExportType.PDF -> R.string.save_pdf_choose_directory
                ExportType.DWG -> R.string.save_dwg_choose_directory
            }
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
            exportDirectoryLauncher.launch(savedDirectory)
        } else {
            when (type) {
                ExportType.PDF -> exportVisiblePointsToPdf(savedDirectory)
                ExportType.DWG -> exportVisiblePointsToDwg(savedDirectory)
            }
        }
    }

    private fun exportVisiblePointsToPdf(directoryUri: Uri?) {
        val result = PdfExporter.exportToPdf(
            context = this,
            points = visiblePoints,
            targetDirectoryUri = directoryUri,
            baseFileName = currentPcoFileName,
            showPointNumbers = showPointNumbers,
            showPointCodes = showPointCodes
        )
        if (result != null) {
            Toast.makeText(
                this,
                getString(R.string.save_pdf_success, result.description),
                Toast.LENGTH_LONG
            ).show()
        } else {
            if (directoryUri != null) {
                clearExportDirectory()
            }
            Toast.makeText(this, R.string.save_pdf_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun exportVisiblePointsToDwg(directoryUri: Uri?) {
        val result = DwgExporter.exportToDwg(
            context = this,
            points = visiblePoints,
            targetDirectoryUri = directoryUri,
            baseFileName = currentPcoFileName,
            showPointNumbers = showPointNumbers,
            showPointCodes = showPointCodes
        )
        if (result != null) {
            Toast.makeText(
                this,
                getString(R.string.save_dwg_success, result.description),
                Toast.LENGTH_LONG
            ).show()
        } else {
            if (directoryUri != null) {
                clearExportDirectory()
            }
            Toast.makeText(this, R.string.save_dwg_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveExportDirectory(uri: Uri) {
        getSharedPreferences(PREFS_EXPORT, MODE_PRIVATE)
            .edit()
            .putString(KEY_EXPORT_DIRECTORY, uri.toString())
            .apply()
    }

    private fun getSavedExportDirectory(): Uri? {
        val uriString = getSharedPreferences(PREFS_EXPORT, MODE_PRIVATE)
            .getString(KEY_EXPORT_DIRECTORY, null)
        return uriString?.let(Uri::parse)
    }

    private fun clearExportDirectory() {
        getSharedPreferences(PREFS_EXPORT, MODE_PRIVATE)
            .edit()
            .remove(KEY_EXPORT_DIRECTORY)
            .apply()
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
            val document = androidx.documentfile.provider.DocumentFile.fromSingleUri(this, uri)
            val resolvedName = document?.name ?: uri.lastPathSegment
            currentPcoFileName = resolvedName?.substringBeforeLast('.', missingDelimiterValue = resolvedName)

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
            currentPcoFileName = null
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

        val sortedKeys = groups.keys.sortedWith(LayerOrdering.comparator)
        sortedKeys.forEach { key ->
            val points = groups[key].orEmpty()
            layerStates += LayerState(
                baseCode = key,
                count = points.size,
                isEnabled = CodeRules.isDefaultEnabled(key)
            )
        }
    }

    private fun showLayerSelectionDialog() {
        if (layerStates.isEmpty()) {
            Toast.makeText(this, R.string.layers_toast_no_layers, Toast.LENGTH_SHORT).show()
            return
        }

        val selectionState = layerStates.associate { it.baseCode to it.isEnabled }.toMutableMap()
        val groups = buildLayerSelectionGroups()

        val dialogView = layoutInflater.inflate(R.layout.dialog_layers, null)
        val expandableListView = dialogView.findViewById<ExpandableListView>(R.id.layersExpandableList)
        val hideNumbersCheckBox = dialogView.findViewById<CheckBox>(R.id.hideNumbersCheckBox)
        val hideCodesCheckBox = dialogView.findViewById<CheckBox>(R.id.hideCodesCheckBox)

        hideNumbersCheckBox.isChecked = !showPointNumbers
        hideCodesCheckBox.isChecked = !showPointCodes

        hideNumbersCheckBox.setOnCheckedChangeListener { _, isChecked ->
            showPointNumbers = !isChecked
            drawingView.setLabelVisibility(showPointNumbers, showPointCodes)
        }

        hideCodesCheckBox.setOnCheckedChangeListener { _, isChecked ->
            showPointCodes = !isChecked
            drawingView.setLabelVisibility(showPointNumbers, showPointCodes)
        }

        val adapter = LayerSelectionAdapter(this, groups, selectionState)
        expandableListView.setAdapter(adapter)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.layers_dialog_apply) { _, _ ->
                layerStates.forEach { state ->
                    state.isEnabled = selectionState[state.baseCode] ?: false
                }
                applyLayerFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.layers_dialog_select_all) { _, _ ->
                selectionState.keys.forEach { key -> selectionState[key] = true }
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
            val activeLayers = layerStates
                .filter { it.isEnabled }
                .map { it.baseCode }
                .toSet()
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
            LayerDefinitions.codeDisplayName(this, code)
                ?: getString(R.string.layers_layer_with_code, code)
        } ?: getString(R.string.layers_layer_without_code)
    }

    private fun buildLayerSelectionGroups(): List<LayerSelectionGroup> {
        val statesByCode = layerStates.associateBy { it.baseCode }
        val result = mutableListOf<LayerSelectionGroup>()

        LayerDefinitions.groups.forEach { definition ->
            val items = definition.codes.mapNotNull { codeDefinition ->
                val state = statesByCode[codeDefinition.code]
                state?.let {
                    LayerSelectionItem(
                        baseCode = codeDefinition.code,
                        displayName = getString(codeDefinition.nameRes),
                        count = state.count
                    )
                }
            }
            if (items.isNotEmpty()) {
                result += LayerSelectionGroup(
                    title = getString(definition.titleRes),
                    items = items
                )
            }
        }

        val otherStates = layerStates
            .filter { state ->
                val code = state.baseCode
                code != null && !LayerDefinitions.isKnownCode(code)
            }
            .sortedWith { first, second ->
                LayerOrdering.comparator.compare(first.baseCode, second.baseCode)
            }

        if (otherStates.isNotEmpty()) {
            val otherItems = otherStates.map { state ->
                LayerSelectionItem(
                    baseCode = state.baseCode,
                    displayName = layerDisplayName(state),
                    count = state.count
                )
            }
            result += LayerSelectionGroup(
                title = getString(R.string.layers_group_other),
                items = otherItems
            )
        }

        val noCodeState = layerStates.firstOrNull { it.baseCode == null }
        if (noCodeState != null) {
            result += LayerSelectionGroup(
                title = getString(R.string.layers_group_no_code),
                items = listOf(
                    LayerSelectionItem(
                        baseCode = null,
                        displayName = getString(R.string.layers_layer_without_code),
                        count = noCodeState.count
                    )
                )
            )
        }

        return result
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

    companion object {
        private const val PREFS_EXPORT = "export_prefs"
        private const val KEY_EXPORT_DIRECTORY = "export_directory"
    }

}
