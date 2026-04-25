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
    /** Whether the timer ran to completion */
    val completed: Boolean,
)
