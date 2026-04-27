package com.zazen.service

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RawRes
import com.zazen.data.model.Sound
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private val loadedSounds = mutableMapOf<Int, Int>()

    fun preload(@RawRes resId: Int) {
        if (resId !in loadedSounds) {
            loadedSounds[resId] = soundPool.load(context, resId, 1)
        }
    }

    /** Preload every sound in the Sound enum for instant preview playback. */
    fun preloadAll() {
        Sound.entries.forEach { preload(it.resId) }
    }

    fun play(@RawRes resId: Int, volume: Float = 1f) {
        val soundId = loadedSounds.getOrPut(resId) {
            soundPool.load(context, resId, 1)
        }
        val vol = volume.coerceIn(0f, 1f)
        soundPool.play(soundId, vol, vol, 1, 0, 1f)
    }

    /** Double-pulse vibration pattern so it's noticeable during meditation. */
    fun vibrate(strong: Boolean = false) {
        val pattern = if (strong) {
            // End-of-session: long-short-long pattern
            longArrayOf(0, 400, 200, 400, 200, 600)
        } else {
            // Interval bell: two short pulses
            longArrayOf(0, 250, 150, 250)
        }
        vibrator?.vibrate(
            VibrationEffect.createWaveform(pattern, -1)
        )
    }

    fun release() {
        soundPool.release()
    }
}
