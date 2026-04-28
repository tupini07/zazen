package com.zazen.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
    // SoundPool kept only for quick UI preview taps (low latency, short clips OK)
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
    private val activePlayers = mutableListOf<MediaPlayer>()

    fun preload(@RawRes resId: Int) {
        if (resId !in loadedSounds) {
            loadedSounds[resId] = soundPool.load(context, resId, 1)
        }
    }

    /** Preload every sound in the Sound enum for instant preview playback. */
    fun preloadAll() {
        Sound.entries.forEach { preload(it.resId) }
    }

    /** Quick preview via SoundPool — fine for short taps in the UI. */
    fun play(@RawRes resId: Int, volume: Float = 1f) {
        val soundId = loadedSounds.getOrPut(resId) {
            soundPool.load(context, resId, 1)
        }
        val vol = volume.coerceIn(0f, 1f)
        soundPool.play(soundId, vol, vol, 1, 0, 1f)
    }

    /**
     * Full-length playback via MediaPlayer — used for interval and end bells
     * during meditation so the entire audio clip plays without truncation.
     */
    fun playFull(@RawRes resId: Int, volume: Float = 1f) {
        val vol = volume.coerceIn(0f, 1f)
        val player = MediaPlayer.create(context, resId)?.apply {
            setVolume(vol, vol)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setOnCompletionListener { mp ->
                mp.release()
                synchronized(activePlayers) { activePlayers.remove(mp) }
            }
        } ?: return
        synchronized(activePlayers) { activePlayers.add(player) }
        player.start()
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
        synchronized(activePlayers) {
            activePlayers.forEach { it.release() }
            activePlayers.clear()
        }
    }
}
