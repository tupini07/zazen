package com.zazen.data.model

import android.os.Parcelable
import com.zazen.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class TimerConfig(
    /** Total duration in millis, or 0 for an open-ended (count-up) sit. */
    val durationMillis: Long,
    val bells: List<IntervalBell> = emptyList(),
    val vibrateOnly: Boolean = false,
    val bellVolume: Float = 1f,
    val endSoundResId: Int = R.raw.bell,
    val dndEnabled: Boolean = false,
    /** Spacing between repeating bells in millis, or 0 to disable them. */
    val repeatEveryMillis: Long = 0,
    val repeatSoundResId: Int = R.raw.bell,
) : Parcelable {
    val isOpenEnded: Boolean get() = durationMillis <= 0L
}
