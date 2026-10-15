package com.simphotos.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    var lastScanAt: Long
        get() = prefs.getLong("last_scan_at", 0L)
        set(value) = prefs.edit { putLong("last_scan_at", value) }

    var totalFreedBytes: Long
        get() = prefs.getLong("total_freed", 0L)
        set(value) = prefs.edit { putLong("total_freed", value) }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            threshold = prefs.getFloat("threshold", d.threshold),
            timeWindow = runCatching { TimeWindow.valueOf(prefs.getString("time_window", null)!!) }
                .getOrDefault(d.timeWindow),
            findExactDuplicatesEverywhere = prefs.getBoolean("exact_everywhere", d.findExactDuplicatesEverywhere),
            includeScreenshots = prefs.getBoolean("include_screenshots", d.includeScreenshots),
            protectFavorites = prefs.getBoolean("protect_favorites", d.protectFavorites),
            deleteMode = runCatching { DeleteMode.valueOf(prefs.getString("delete_mode", null)!!) }
                .getOrDefault(d.deleteMode),
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit {
            putFloat("threshold", s.threshold)
            putString("time_window", s.timeWindow.name)
            putBoolean("exact_everywhere", s.findExactDuplicatesEverywhere)
            putBoolean("include_screenshots", s.includeScreenshots)
            putBoolean("protect_favorites", s.protectFavorites)
            putString("delete_mode", s.deleteMode.name)
        }
        _settings.value = s
    }
}
