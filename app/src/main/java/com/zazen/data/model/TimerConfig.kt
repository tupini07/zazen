package com.zazen.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TimerConfig(
    val durationMillis: Long,
    val bells: List<IntervalBell> = emptyList(),
    val vibrateOnly: Boolean = false,
) : Parcelable
