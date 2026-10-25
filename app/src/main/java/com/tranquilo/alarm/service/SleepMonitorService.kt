package com.tranquilo.alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.tranquilo.alarm.R
import com.tranquilo.alarm.RingActivity
import com.tranquilo.alarm.alarm.AlarmPlayer
import com.tranquilo.alarm.alarm.AlarmScheduler
import com.tranquilo.alarm.core.MovementAnalyzer
import com.tranquilo.alarm.data.AlarmSettings
import com.tranquilo.alarm.data.Epoch
import com.tranquilo.alarm.data.NightReport
import com.tranquilo.alarm.data.SettingsStore
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.max

class SleepMonitorService : Service(), SensorEventListener {

    private lateinit var settings: AlarmSettings
    private lateinit var store: SettingsStore

    private var sensorManager: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var analyzer: MovementAnalyzer? = null

    private var windowStartMs = 0L
    private var windowEndMs = 0L
    private var baselineFinished = false
    private var peak = 0.0
    private var ringing = false
    private val epochLog = ArrayList<Epoch>()

    // Пульс копим внутри текущей эпохи и пишем среднее вместе с движением.
    private var hrSum = 0.0
    private var hrCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        settings = store.load()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                goForeground(ringNotification())
                ring(smart = false)
            }
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            else -> {
                goForeground(monitorNotification())
                startMonitoring()
            }
        }
        return START_STICKY
    }

    // --- мониторинг ---------------------------------------------------------

    private fun startMonitoring() {
        if (analyzer != null) return

        windowEndMs = nextOccurrence(settings.windowEnd)
        windowStartMs = windowEndMs - settings.windowMinutes * 60_000L
        analyzer = MovementAnalyzer(settings.sensitivity)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val timeout = max(60_000L, windowEndMs - System.currentTimeMillis() + 120_000L)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tranquilo:monitor").apply {
            setReferenceCounted(false)
            acquire(timeout)
        }

        sensorManager = (getSystemService(Context.SENSOR_SERVICE) as SensorManager).also { sm ->
            val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer == null) {
                // Без датчика будильник всё равно должен прозвонить в конце окна.
                return@also
            }
            sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

            // Пульс пока только записываем — для сравнения с фазами сна в Mobvoi Health.
            // Нет разрешения или датчика — просто работаем по движению.
            if (HeartRateAccess.full(this)) {
                sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let { hr ->
                    runCatching { sm.registerListener(this, hr, SensorManager.SENSOR_DELAY_NORMAL) }
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (ringing) return
        val a = analyzer ?: return

        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val bpm = event.values.firstOrNull() ?: 0f
            val reliable = event.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            if (reliable && bpm > 0f) {
                hrSum += bpm
                hrCount++
            }
            return
        }

        val epoch = a.addSample(
            System.currentTimeMillis(),
            event.values[0], event.values[1], event.values[2]
        ) ?: return

        val now = System.currentTimeMillis()
        val hr = if (hrCount > 0) (hrSum / hrCount).toFloat() else 0f
        hrSum = 0.0
        hrCount = 0
        epochLog.add(Epoch(((windowEndMs - now) / 1000).toInt(), epoch.toFloat(), hr))

        if (now < windowStartMs) {
            a.addBaselineEpoch(epoch)
            return
        }

        if (!baselineFinished) {
            a.finishBaseline()
            baselineFinished = true
        }

        peak = max(peak, epoch)

        if (now >= windowEndMs) {
            ring(smart = false)
            return
        }

        if (a.isWakeMoment(epoch)) {
            ring(smart = true)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // --- звонок -------------------------------------------------------------

    private fun ring(smart: Boolean) {
        if (ringing) return
        ringing = true

        releaseSensors()
        AlarmScheduler.cancelBackupRing(this)

        val a = analyzer
        store.saveReport(
            NightReport(
                ringedAtMillis = System.currentTimeMillis(),
                smart = smart,
                baseline = a?.baseline ?: 0.0,
                threshold = a?.threshold?.takeIf { it != Double.MAX_VALUE } ?: 0.0,
                peak = peak,
                windowMinutes = settings.windowMinutes,
                epochs = epochLog.toList()
            )
        )
        // Запоминаем, что на этот крайний срок уже прозвонили, — чтобы после
        // «Выключить» мониторинг не запустился снова до того же срока.
        if (windowEndMs > 0L) store.saveHandledDeadline(windowEndMs)

        AlarmPlayer.start(this, settings.soundEnabled)
        goForeground(ringNotification())

        // На части прошивок активность из фона не запускается — тогда сработает
        // полноэкранное уведомление выше.
        runCatching {
            startActivity(
                Intent(this, RingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }

    private fun stopEverything() {
        releaseSensors()
        AlarmPlayer.stop()
        // Ставим будильник на следующие сутки. Сегодняшний срок AlarmScheduler
        // пропустит сам: он уже отмечен в ring() как отработанный.
        AlarmScheduler.schedule(this, store.load())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseSensors() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseSensors()
        AlarmPlayer.stop()
        super.onDestroy()
    }

    // --- уведомления --------------------------------------------------------

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (HeartRateAccess.foreground(this)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            ServiceCompat.startForeground(this, NOTIF_MONITOR_ID, notification, type)
        } else {
            startForeground(NOTIF_MONITOR_ID, notification)
        }
    }

    /** Без звука, вибрации и включения экрана: в это время человек спит. */
    private fun monitorNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tranquilo следит за сном")
            .setContentText("Разбудит между ${fmt(settings.windowStart)} и ${fmt(settings.windowEnd)}")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun ringNotification(): Notification {
        val full = PendingIntent.getActivity(
            this, 0,
            Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Если экран уже включён, Android показывает не полноэкранный будильник,
        // а уведомление. Поэтому «Выключить» есть и прямо в нём.
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, SleepMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Пора вставать")
            .setContentText("Tranquilo")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Выключить", stop)
            .build()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        // Важность созданного канала не меняется — старый удаляем, заводим новый, тихий.
        manager.deleteNotificationChannel(OLD_CHANNEL_MONITOR)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Мониторинг сна", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, "Будильник", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)   // звук играем сами через AlarmPlayer
                enableVibration(false) // вибрацией тоже управляем сами
            }
        )
    }

    private fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    private fun nextOccurrence(time: LocalTime): Long {
        val now = ZonedDateTime.now()
        var target = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    companion object {
        const val ACTION_MONITOR = "com.tranquilo.alarm.service.MONITOR"
        const val ACTION_RING = "com.tranquilo.alarm.service.RING"
        const val ACTION_STOP = "com.tranquilo.alarm.service.STOP"

        private const val OLD_CHANNEL_MONITOR = "monitor"
        private const val CHANNEL_MONITOR = "monitor_quiet"
        private const val CHANNEL_RING = "ring"
        private const val NOTIF_MONITOR_ID = 1

        fun stop(context: Context) {
            context.startService(
                Intent(context, SleepMonitorService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
