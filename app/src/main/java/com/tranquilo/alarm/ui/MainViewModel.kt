package com.tranquilo.alarm.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.tranquilo.alarm.alarm.AlarmScheduler
import com.tranquilo.alarm.core.SleepOnsetDetector
import com.tranquilo.alarm.service.BedtimeService
import com.tranquilo.alarm.service.SleepMonitorService
import com.tranquilo.alarm.data.AlarmSettings
import com.tranquilo.alarm.data.BedtimeResult
import com.tranquilo.alarm.data.NightReport
import com.tranquilo.alarm.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<AlarmSettings> = _settings.asStateFlow()

    private val _report = MutableStateFlow(store.loadReport())
    val report: StateFlow<NightReport?> = _report.asStateFlow()

    private val _bedtime = MutableStateFlow(loadBedtime())
    val bedtime: StateFlow<BedtimeUi> = _bedtime.asStateFlow()

    fun refresh() {
        _settings.value = store.load()
        _report.value = store.loadReport()
        _bedtime.value = loadBedtime()
    }

    /** «Лёг спать»: первое нажатие начинает поиск засыпания, повторное — отменяет. */
    fun toggleBedtime() {
        val app = getApplication<Application>()
        if (_bedtime.value.activeSinceMs != null) {
            store.cancelBedtime()
            BedtimeService.cancel(app)
        } else {
            store.startBedtime(System.currentTimeMillis())
            BedtimeService.start(app)
        }
        _bedtime.value = loadBedtime()
    }

    private fun loadBedtime(): BedtimeUi {
        val since = store.bedtimeActiveSince()
        // Сервис мог быть убит системой и не успеть снять отметку — считаем её устаревшей.
        val alive = since > 0 &&
            System.currentTimeMillis() - since < SleepOnsetDetector.MAX_SEARCH_MS + 10 * 60_000L
        return BedtimeUi(activeSinceMs = since.takeIf { alive }, last = store.lastBedtime())
    }

    fun toggleEnabled() = update { it.copy(enabled = !it.enabled) }

    fun setWindowEnd(hour: Int, minute: Int) =
        update { it.copy(windowEndHour = hour, windowEndMinute = minute) }

    fun cycleWindowLength() = update {
        val next = when (it.windowMinutes) {
            15 -> 30
            30 -> 45
            else -> 15
        }
        it.copy(windowMinutes = next)
    }

    fun setSensitivity(value: Int) = update { it.copy(sensitivity = value.coerceIn(1, 5)) }

    fun toggleSound() = update { it.copy(soundEnabled = !it.soundEnabled) }

    private fun update(transform: (AlarmSettings) -> AlarmSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        store.save(updated)
        val app = getApplication<Application>()
        AlarmScheduler.schedule(app, updated)
        // Выключили будильник, пока датчик уже работает, — останавливаем и его.
        if (!updated.enabled) {
            app.stopService(Intent(app, SleepMonitorService::class.java))
        }
    }
}

/** Состояние «Лёг спать» для экрана: идёт ли поиск засыпания и чем закончился прошлый. */
data class BedtimeUi(val activeSinceMs: Long?, val last: BedtimeResult?)
