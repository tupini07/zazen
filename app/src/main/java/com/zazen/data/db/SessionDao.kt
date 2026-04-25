package com.zazen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zazen.data.model.MeditationSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: MeditationSession): Long

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<MeditationSession>>

    @Query("SELECT COALESCE(SUM(completedMillis), 0) FROM sessions")
    fun getTotalMeditatedMillis(): Flow<Long>

    @Query("SELECT COUNT(*) FROM sessions")
    fun getSessionCount(): Flow<Int>
}
