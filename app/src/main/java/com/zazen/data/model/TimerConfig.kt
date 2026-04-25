package com.zazen.data.model

import android.os.Parcelable
import com.zazen.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class TimerConfig(
    val durationMillis: Long,
    val bells: List<IntervalBell> = emptyList(),
    val vibrateOnly: Boolean = false,
    val bellVolume: Float = 1f,
    val endSoundResId: Int = R.raw.bell,
    val dndEnabled: Boolean = false,
) : Parcelable
