package com.zazen.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class Preset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Total seconds, or 0 for an open-ended sit. */
    val durationSeconds: Int,
    val vibrateOnly: Boolean = false,
    val bellVolume: Float = 1f,
    val endSoundName: String = Sound.DEFAULT.name,
    val dndEnabled: Boolean = false,
    val bells: List<PresetBell> = emptyList(),
    /** Spacing between repeating bells in seconds, or 0 to disable them. */
    val repeatEverySeconds: Int = 0,
    val repeatSoundName: String = Sound.DEFAULT.name,
) {
    val isOpenEnded: Boolean get() = durationSeconds <= 0

    fun toTimerConfig(): TimerConfig {
        val endSound = Sound.entries.find { it.name == endSoundName } ?: Sound.DEFAULT
        val repeatSound = Sound.entries.find { it.name == repeatSoundName } ?: Sound.DEFAULT
        return TimerConfig(
            durationMillis = durationSeconds.coerceAtLeast(0) * 1_000L,
            bells = bells.mapNotNull { pb ->
                val sound = Sound.entries.find { it.name == pb.soundName } ?: return@mapNotNull null
                IntervalBell(triggerAtMillis = pb.triggerAtMillis, soundResId = sound.resId)
            },
            vibrateOnly = vibrateOnly,
            bellVolume = bellVolume,
            endSoundResId = endSound.resId,
            dndEnabled = dndEnabled,
            repeatEveryMillis = repeatEverySeconds.coerceAtLeast(0) * 1_000L,
            repeatSoundResId = repeatSound.resId,
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
            repeatEverySeconds: Int = 0,
            repeatSound: Sound = Sound.DEFAULT,
        ): Preset = Preset(
            name = name,
            durationSeconds = durationSeconds,
            vibrateOnly = vibrateOnly,
            bellVolume = bellVolume,
            endSoundName = endSound.name,
            dndEnabled = dndEnabled,
            repeatEverySeconds = repeatEverySeconds,
            repeatSoundName = repeatSound.name,
            bells = intervalBells.map { ib ->
                PresetBell(
                    triggerAtMillis = ib.triggerAtMillis,
                    soundName = Sound.fromResId(ib.soundResId)?.name ?: Sound.DEFAULT.name,
                )
            },
        )
    }
}
