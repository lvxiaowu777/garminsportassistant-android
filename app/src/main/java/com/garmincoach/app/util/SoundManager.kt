package com.garmincoach.app.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/**
 * 全局音效：点击 / 录音启停短促提示音，带全局开关（SharedPreferences 持久化）。
 */
object SoundManager {

    private const val PREFS = "garmin_coach_settings"
    private const val KEY_SOUND = "sound_enabled"

    private var tone: ToneGenerator? = null
    var enabled: Boolean = true

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_SOUND, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SOUND, value).apply()
    }

    private fun ensureTone() {
        if (tone == null) {
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, 40)
        }
    }

    fun playClick() {
        if (!enabled) return
        try { ensureTone(); tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) } catch (_: Exception) {}
    }

    fun playRecordStart() {
        if (!enabled) return
        try { ensureTone(); tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 100) } catch (_: Exception) {}
    }

    fun playRecordStop() {
        if (!enabled) return
        try { ensureTone(); tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100) } catch (_: Exception) {}
    }

    fun release() {
        tone?.release()
        tone = null
    }
}
