package com.tranquilo.alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tranquilo.alarm.R
import com.tranquilo.alarm.core.MovementAnalyzer
import com.tranquilo.alarm.core.SleepOnsetDetector
import com.tranquilo.alarm.data.SettingsStore
import java.time.Instant
import java.time.ZoneId

/**
 * «Лёг спать»: следит за движениями, пока не найдёт момент засыпания,
 * сохраняет, за сколько минут уснул, и сразу выключается.
 * С утренним мониторингом никак не связан и ему не мешает.
 */
class BedtimeService : Service(), SensorEventListener {

    private lateinit var store: SettingsStore

    private var sensorManager: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var detector: SleepOnsetDetector? = null
    private val movement = MovementAnalyzer(sensitivity = 3)
    private var bedtimeMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                SleepMonitorService.CHANNEL_MONITOR, "Мониторинг сна", NotificationManager.IMPORTANCE_MIN
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            stopEverything()
            return START_NOT_STICKY
        }

        bedtimeMs = store.bedtimeActiveSince().takeIf { it > 0 } ?: System.currentTimeMillis()
        goForeground()
        if (detector == null) begin()
        // Если систему перезапустит, начать заново честно не получится — не воскрешаемся.
        return START_NOT_STICKY
    }

    private fun begin() {
        detector = SleepOnsetDetector()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tranquilo:bedtime").apply {
            setReferenceCounted(false)
            acquire(SleepOnsetDetector.MAX_SEARCH_MS + 60_000L)
        }

        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            finish(onsetMs = null)
            return
        }
        sensorManager = sm
        sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val d = detector ?: return
        val now = System.currentTimeMillis()

        if (now - bedtimeMs > SleepOnsetDetector.MAX_SEARCH_MS) {
            finish(onsetMs = null)
            return
        }

        val epoch = movement.addSample(now, event.values[0], event.values[1], event.values[2]) ?: return
        val onsetMs = d.addEpoch(now, epoch) ?: return

        finish(onsetMs = onsetMs.coerceAtLeast(bedtimeMs))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** onsetMs = null — засыпание за отведённое время не нашлось. */
    private fun finish(onsetMs: Long?) {
        if (detector == null) return
        detector = null
        store.finishBedtime(bedtimeMs, onsetMs)
        stopEverything()
    }

    private fun stopEverything() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun goForeground() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    /** Тихое: без звука и вибрации, человек засыпает. */
    private fun notification(): Notification {
        val time = Instant.ofEpochMilli(bedtimeMs).atZone(ZoneId.systemDefault()).toLocalTime()
        return NotificationCompat.Builder(this, SleepMonitorService.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tranquilo ждёт, когда ты уснёшь")
            .setContentText("Лёг в %02d:%02d".format(time.hour, time.minute))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val ACTION_CANCEL = "com.tranquilo.alarm.bedtime.CANCEL"
        private const val NOTIF_ID = 3

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BedtimeService::class.java))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, BedtimeService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
