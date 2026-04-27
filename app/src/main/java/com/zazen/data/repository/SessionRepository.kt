package com.zazen.data.repository

import com.zazen.data.db.SessionDao
import com.zazen.data.model.MeditationSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
) {
    fun getAllSessions(): Flow<List<MeditationSession>> = sessionDao.getAllSessions()

    fun getTotalMeditatedMillis(): Flow<Long> = sessionDao.getTotalMeditatedMillis()

    fun getSessionCount(): Flow<Int> = sessionDao.getSessionCount()

    suspend fun logSession(session: MeditationSession): Long = sessionDao.insert(session)

    suspend fun deleteSession(id: Long) = sessionDao.deleteById(id)
}
