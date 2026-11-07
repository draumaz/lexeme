package io.github.draumaz.lexeme.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

/** App preferences in SharedPreferences, exposed as flows so the theme recomposes on change. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(DYNAMIC, true))
    /** Material You ("Monet") colors from the wallpaper; off uses Lexeme's own ink-blue palette. */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(THEME, null) ?: "") }.getOrDefault(ThemeMode.System)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setDynamicColor(on: Boolean) {
        _dynamicColor.value = on
        prefs.edit().putBoolean(DYNAMIC, on).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(THEME, mode.name).apply()
    }

    private companion object {
        const val DYNAMIC = "dynamic_color"
        const val THEME = "theme_mode"
    }
}
