package com.garmincoach.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.garmincoach.app.util.SoundManager

/**
 * Garmin Coach Application。
 * 创建通知渠道（Android 8.0+ 必须，为以后推送做准备）。
 */
class GarminCoachApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        SoundManager.load(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_TRAINING,
                "训练提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "训练计划、恢复状态、天气提醒"
                enableVibration(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_TRAINING = "training_reminder"

        lateinit var instance: GarminCoachApp
            private set
    }
}
