package com.zazen.data.model

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.zazen.R

enum class Sound(
    @RawRes val resId: Int,
    @StringRes val displayNameRes: Int,
) {
    BELL(R.raw.bell, R.string.sound_bell),
    BELL_HIGH(R.raw.bell_high, R.string.sound_bell_high),
    BOWL(R.raw.bowl, R.string.sound_bowl),
    BOWL_DEEP(R.raw.bowl_deep, R.string.sound_bowl_deep),
    GONG(R.raw.gong, R.string.sound_gong);

    companion object {
        val DEFAULT = BELL

        fun fromResId(resId: Int): Sound? = entries.find { it.resId == resId }
    }
}
