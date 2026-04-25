package com.zazen.data.model

import androidx.annotation.RawRes
import com.zazen.R

enum class Sound(
    @RawRes val resId: Int,
    val displayName: String,
) {
    BELL(R.raw.bell, "Bell"),
    BELL_HIGH(R.raw.bell_high, "High Bell"),
    BOWL(R.raw.bowl, "Tibetan Bowl"),
    BOWL_DEEP(R.raw.bowl_deep, "Deep Bowl"),
    GONG(R.raw.gong, "Zen Gong");

    companion object {
        val DEFAULT = BELL

        fun fromResId(resId: Int): Sound? = entries.find { it.resId == resId }
    }
}
