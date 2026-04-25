package com.zazen.data.model

/**
 * Bell definition stored in presets — uses Sound enum name (stable across builds)
 * instead of resource IDs which can change between app versions.
 */
data class PresetBell(
    val triggerAtMillis: Long,
    val soundName: String,
)
