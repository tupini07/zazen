package com.zazen.data.model

sealed interface TimerState {
    data object Idle : TimerState

    data class Running(
        /** Always 0 for an open-ended sit — read [elapsedMillis] instead. */
        val remainingMillis: Long,
        /** Always 0 for an open-ended sit. */
        val totalMillis: Long,
        val elapsedMillis: Long = 0,
    ) : TimerState {
        val isOpenEnded: Boolean get() = totalMillis <= 0L
    }

    data class Paused(
        val remainingMillis: Long,
        val totalMillis: Long,
        val elapsedMillis: Long = 0,
    ) : TimerState {
        val isOpenEnded: Boolean get() = totalMillis <= 0L
    }

    data class Finished(
        val sessionId: Long,
        val completed: Boolean,
        val elapsedMillis: Long = 0,
        val openEnded: Boolean = false,
    ) : TimerState
}
