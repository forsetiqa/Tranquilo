package com.tranquilo.alarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Держит вибрацию и звук, пока пользователь не нажмёт "Выключить". */
object AlarmPlayer {

    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null

    @Synchronized
    fun start(context: Context, withSound: Boolean) {
        stop()

        vibrator = vibrator(context).also { v ->
            // Три длинных сильных импульса, пауза — и по кругу, пока не нажмут «Выключить».
            val timings = longArrayOf(0, 1000, 400, 1000, 400, 1000, 1200)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0)
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, 0)
            } else {
                VibrationEffect.createWaveform(timings, 0)
            }

            // Помечаем вибрацию как будильник: иначе ночной режим и «Не беспокоить»
            // её глушат, и остаётся только тихое уведомление.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, alarmAudioAttributes())
            }
        }

        if (withSound) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
                audioAttributes = alarmAudioAttributes()
                play()
            }
        }
    }

    private fun alarmAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    @Synchronized
    fun stop() {
        vibrator?.cancel()
        vibrator = null
        ringtone?.let { if (it.isPlaying) it.stop() }
        ringtone = null
    }

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
}
