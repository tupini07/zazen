package com.zazen.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class IntervalBell(
    /** Milliseconds from session start at which this bell fires */
    val triggerAtMillis: Long,
    val soundResId: Int,
    val vibrateOnly: Boolean = false,
) : Parcelable
