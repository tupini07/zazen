package com.zazen.service

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RawRes
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
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val vibrator = context.getSystemService(Vibrator::class.java)

    private val loadedSounds = mutableMapOf<Int, Int>()

    fun preload(@RawRes resId: Int) {
        if (resId !in loadedSounds) {
            loadedSounds[resId] = soundPool.load(context, resId, 1)
        }
    }

    fun play(@RawRes resId: Int) {
        val soundId = loadedSounds.getOrPut(resId) {
            soundPool.load(context, resId, 1)
        }
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun vibrate(durationMs: Long = 500) {
        vibrator?.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    fun release() {
        soundPool.release()
    }
}
