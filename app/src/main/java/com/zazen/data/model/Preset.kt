package com.zazen.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class Preset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationSeconds: Int,
    val vibrateOnly: Boolean = false,
    val bellVolume: Float = 1f,
    val endSoundName: String = Sound.DEFAULT.name,
    val dndEnabled: Boolean = false,
    val bells: List<PresetBell> = emptyList(),
) {
    fun toTimerConfig(): TimerConfig {
        val endSound = Sound.entries.find { it.name == endSoundName } ?: Sound.DEFAULT
        return TimerConfig(
            durationMillis = durationSeconds * 1_000L,
            bells = bells.mapNotNull { pb ->
                val sound = Sound.entries.find { it.name == pb.soundName } ?: return@mapNotNull null
                IntervalBell(triggerAtMillis = pb.triggerAtMillis, soundResId = sound.resId)
            },
            vibrateOnly = vibrateOnly,
            bellVolume = bellVolume,
            endSoundResId = endSound.resId,
            dndEnabled = dndEnabled,
        )
    }

    companion object {
        fun fromSetupState(
            name: String,
            durationSeconds: Int,
            vibrateOnly: Boolean,
            bellVolume: Float,
            endSound: Sound,
            dndEnabled: Boolean,
            intervalBells: List<IntervalBell>,
        ): Preset = Preset(
            name = name,
            durationSeconds = durationSeconds,
            vibrateOnly = vibrateOnly,
            bellVolume = bellVolume,
            endSoundName = endSound.name,
            dndEnabled = dndEnabled,
            bells = intervalBells.map { ib ->
                PresetBell(
                    triggerAtMillis = ib.triggerAtMillis,
                    soundName = Sound.fromResId(ib.soundResId)?.name ?: Sound.DEFAULT.name,
                )
            },
        )
    }
}
