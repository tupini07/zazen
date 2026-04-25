package com.zazen.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.zazen.data.model.Sound
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("zazen_prefs", Context.MODE_PRIVATE)

    var durationMinutes: Int
        get() = prefs.getInt("duration_minutes", 25)
        set(value) = prefs.edit().putInt("duration_minutes", value).apply()

    var vibrateOnly: Boolean
        get() = prefs.getBoolean("vibrate_only", false)
        set(value) = prefs.edit().putBoolean("vibrate_only", value).apply()

    var bellVolume: Float
        get() = prefs.getFloat("bell_volume", 1f)
        set(value) = prefs.edit().putFloat("bell_volume", value).apply()

    var endSoundName: String
        get() = prefs.getString("end_sound", Sound.DEFAULT.name) ?: Sound.DEFAULT.name
        set(value) = prefs.edit().putString("end_sound", value).apply()

    var dndEnabled: Boolean
        get() = prefs.getBoolean("dnd_enabled", false)
        set(value) = prefs.edit().putBoolean("dnd_enabled", value).apply()

    // Theme mode: "system", "light", "dark"
    private val _themeMode = MutableStateFlow(
        prefs.getString("theme_mode", "system") ?: "system"
    )
    val themeModeFlow: StateFlow<String> = _themeMode.asStateFlow()

    var themeMode: String
        get() = _themeMode.value
        set(value) {
            prefs.edit().putString("theme_mode", value).apply()
            _themeMode.value = value
        }
}
