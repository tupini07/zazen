package com.zazen.data.db

import androidx.room.TypeConverter
import com.zazen.data.model.PresetBell
import org.json.JSONArray
import org.json.JSONObject

class BellListConverter {
    @TypeConverter
    fun fromBellList(bells: List<PresetBell>): String {
        val arr = JSONArray()
        bells.forEach { bell ->
            arr.put(JSONObject().apply {
                put("t", bell.triggerAtMillis)
                put("s", bell.soundName)
            })
        }
        return arr.toString()
    }

    @TypeConverter
    fun toBellList(json: String): List<PresetBell> {
        if (json.isEmpty()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            PresetBell(triggerAtMillis = obj.getLong("t"), soundName = obj.getString("s"))
        }
    }
}
