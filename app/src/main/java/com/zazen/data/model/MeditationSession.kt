package com.zazen.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class MeditationSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis when the session started */
    val startTime: Long,
    /** Planned duration in millis */
    val durationMillis: Long,
    /** Actual elapsed meditation time in millis */
    val completedMillis: Long,
    /** Whether the planned time was reached (or an open-ended sit was stopped). */
    val completed: Boolean,
    /** Optional user notes / reflections */
    val notes: String = "",
) {
    /** True when this was an open-ended sit (no planned duration). */
    val isOpenEnded: Boolean get() = durationMillis <= 0L
}
