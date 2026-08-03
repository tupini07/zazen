package com.zazen.data.repository

import android.content.Context
import android.net.Uri
import com.zazen.data.model.MeditationSession
import com.zazen.data.model.Preset
import com.zazen.data.model.PresetBell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Summary of what was restored from a backup file. */
data class ImportResult(val presetCount: Int, val sessionCount: Int)

/**
 * Handles exporting and importing all user data (presets, session history and preferences)
 * as a single JSON file, so users can back up and restore their data across devices.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presetRepository: PresetRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    /** Writes a full backup of the app's data to [uri] as JSON. */
    suspend fun exportToUri(uri: Uri) {
        val json = buildBackupJson()
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Unable to open output stream")
        stream.use { it.write(json.toString(2).toByteArray(Charsets.UTF_8)) }
    }

    /** Reads a backup JSON file from [uri] and replaces the current app data with it. */
    suspend fun importFromUri(uri: Uri): ImportResult {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open input stream")
        val text = stream.use { it.bufferedReader().readText() }
        return restoreFromJson(JSONObject(text))
    }

    private suspend fun buildBackupJson(): JSONObject {
        val presets = presetRepository.getAll().first()
        val sessions = sessionRepository.getAllSessions().first()

        val presetsArray = JSONArray()
        presets.forEach { presetsArray.put(presetToJson(it)) }

        val sessionsArray = JSONArray()
        sessions.forEach { sessionsArray.put(sessionToJson(it)) }

        return JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("presets", presetsArray)
            put("sessions", sessionsArray)
            put("preferences", preferencesToJson())
        }
    }

    private suspend fun restoreFromJson(json: JSONObject): ImportResult {
        val presets = json.optJSONArray("presets")?.let { arr ->
            (0 until arr.length()).map { presetFromJson(arr.getJSONObject(it)) }
        } ?: emptyList()

        val sessions = json.optJSONArray("sessions")?.let { arr ->
            (0 until arr.length()).map { sessionFromJson(arr.getJSONObject(it)) }
        } ?: emptyList()

        presetRepository.replaceAll(presets)
        sessionRepository.replaceAll(sessions)
        json.optJSONObject("preferences")?.let { restorePreferences(it) }

        return ImportResult(presetCount = presets.size, sessionCount = sessions.size)
    }

    private fun presetToJson(preset: Preset): JSONObject {
        val bells = JSONArray()
        preset.bells.forEach { bell ->
            bells.put(
                JSONObject().apply {
                    put("triggerAtMillis", bell.triggerAtMillis)
                    put("soundName", bell.soundName)
                },
            )
        }
        return JSONObject().apply {
            put("id", preset.id)
            put("name", preset.name)
            put("durationSeconds", preset.durationSeconds)
            put("vibrateOnly", preset.vibrateOnly)
            put("bellVolume", preset.bellVolume.toDouble())
            put("endSoundName", preset.endSoundName)
            put("dndEnabled", preset.dndEnabled)
            put("repeatEverySeconds", preset.repeatEverySeconds)
            put("repeatSoundName", preset.repeatSoundName)
            put("bells", bells)
        }
    }

    private fun presetFromJson(obj: JSONObject): Preset {
        val bellsArray = obj.optJSONArray("bells") ?: JSONArray()
        val bells = (0 until bellsArray.length()).map { i ->
            val bellObj = bellsArray.getJSONObject(i)
            PresetBell(
                triggerAtMillis = bellObj.getLong("triggerAtMillis"),
                soundName = bellObj.getString("soundName"),
            )
        }
        return Preset(
            id = obj.optLong("id", 0),
            name = obj.getString("name"),
            durationSeconds = obj.getInt("durationSeconds"),
            vibrateOnly = obj.optBoolean("vibrateOnly", false),
            bellVolume = obj.optDouble("bellVolume", 1.0).toFloat(),
            endSoundName = obj.optString("endSoundName", com.zazen.data.model.Sound.DEFAULT.name),
            dndEnabled = obj.optBoolean("dndEnabled", false),
            repeatEverySeconds = obj.optInt("repeatEverySeconds", 0),
            repeatSoundName = obj.optString(
                "repeatSoundName",
                com.zazen.data.model.Sound.DEFAULT.name,
            ),
            bells = bells,
        )
    }

    private fun sessionToJson(session: MeditationSession): JSONObject = JSONObject().apply {
        put("id", session.id)
        put("startTime", session.startTime)
        put("durationMillis", session.durationMillis)
        put("completedMillis", session.completedMillis)
        put("completed", session.completed)
        put("notes", session.notes)
    }

    private fun sessionFromJson(obj: JSONObject): MeditationSession = MeditationSession(
        id = obj.optLong("id", 0),
        startTime = obj.getLong("startTime"),
        durationMillis = obj.getLong("durationMillis"),
        completedMillis = obj.getLong("completedMillis"),
        completed = obj.getBoolean("completed"),
        notes = obj.optString("notes", ""),
    )

    private fun preferencesToJson(): JSONObject = JSONObject().apply {
        put("durationSeconds", preferencesRepository.durationSeconds)
        put("vibrateOnly", preferencesRepository.vibrateOnly)
        put("bellVolume", preferencesRepository.bellVolume.toDouble())
        put("endSoundName", preferencesRepository.endSoundName)
        put("dndEnabled", preferencesRepository.dndEnabled)
        put("screenAlwaysOn", preferencesRepository.screenAlwaysOn)
        put("themeMode", preferencesRepository.themeMode)
        put(
            "intervalBells",
            JSONArray().apply {
                preferencesRepository.loadIntervalBells().forEach { (millis, soundName) ->
                    put(
                        JSONObject().apply {
                            put("triggerAtMillis", millis)
                            put("soundName", soundName)
                        },
                    )
                }
            },
        )
    }

    private fun restorePreferences(obj: JSONObject) {
        obj.optInt("durationSeconds", -1).takeIf { it >= 0 }?.let {
            preferencesRepository.durationSeconds = it
        }
        if (obj.has("vibrateOnly")) preferencesRepository.vibrateOnly = obj.getBoolean("vibrateOnly")
        if (obj.has("bellVolume")) preferencesRepository.bellVolume = obj.getDouble("bellVolume").toFloat()
        if (obj.has("endSoundName")) preferencesRepository.endSoundName = obj.getString("endSoundName")
        if (obj.has("dndEnabled")) preferencesRepository.dndEnabled = obj.getBoolean("dndEnabled")
        if (obj.has("screenAlwaysOn")) preferencesRepository.screenAlwaysOn = obj.getBoolean("screenAlwaysOn")
        if (obj.has("themeMode")) preferencesRepository.themeMode = obj.getString("themeMode")

        val intervalBellsArray = obj.optJSONArray("intervalBells")
        if (intervalBellsArray != null) {
            val bells = (0 until intervalBellsArray.length()).map { i ->
                val bellObj = intervalBellsArray.getJSONObject(i)
                bellObj.getLong("triggerAtMillis") to bellObj.getString("soundName")
            }
            preferencesRepository.saveIntervalBells(bells)
        }
    }

    companion object {
        private const val BACKUP_VERSION = 1
    }
}
