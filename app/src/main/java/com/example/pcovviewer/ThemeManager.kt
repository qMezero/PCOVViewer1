package com.example.pcovviewer

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity

object ThemeManager {
    private const val PREFS_THEME = "theme_preferences"
    private const val KEY_SELECTED_THEME = "selected_theme"

    enum class AppTheme(
        val id: String,
        @StringRes val titleRes: Int,
        @StyleRes val themeRes: Int
    ) {
        LIGHT("light", R.string.theme_light, R.style.Theme_PCOVViewer),
        MIDNIGHT_INDIGO(
            "midnight_indigo",
            R.string.theme_midnight_indigo,
            R.style.Theme_PCOVViewer_MidnightIndigo
        ),
        TEAL_NIGHTFALL(
            "teal_nightfall",
            R.string.theme_teal_nightfall,
            R.style.Theme_PCOVViewer_TealNightfall
        ),
        GRAPHITE_CONTRAST(
            "graphite_contrast",
            R.string.theme_graphite_contrast,
            R.style.Theme_PCOVViewer_GraphiteContrast
        ),
        APP_THEME(
            "app_theme",
            R.string.theme_app_theme,
            R.style.Theme_PCOVViewer_AppTheme
        );

        companion object {
            fun fromId(id: String?): AppTheme {
                return values().firstOrNull { it.id == id } ?: LIGHT
            }
        }
    }

    fun applyTheme(activity: Activity) {
        activity.setTheme(getSelectedTheme(activity).themeRes)
    }

    fun getSelectedTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
        val storedId = prefs.getString(KEY_SELECTED_THEME, null)
        return AppTheme.fromId(storedId)
    }

    fun getThemeDisplayName(context: Context, theme: AppTheme = getSelectedTheme(context)): String {
        return context.getString(theme.titleRes)
    }

    fun getThemeOptions(context: Context): Array<String> {
        return AppTheme.values().map { context.getString(it.titleRes) }.toTypedArray()
    }

    fun getSelectedThemeIndex(context: Context): Int {
        val current = getSelectedTheme(context)
        return AppTheme.values().indexOf(current)
    }

    fun selectTheme(activity: AppCompatActivity, theme: AppTheme) {
        val prefs = activity.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
        val current = AppTheme.fromId(prefs.getString(KEY_SELECTED_THEME, null))
        if (current == theme) {
            return
        }
        prefs.edit().putString(KEY_SELECTED_THEME, theme.id).apply()
        activity.recreate()
    }
}
