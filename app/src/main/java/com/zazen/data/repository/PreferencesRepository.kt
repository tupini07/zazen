package com.zazen.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
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
}
