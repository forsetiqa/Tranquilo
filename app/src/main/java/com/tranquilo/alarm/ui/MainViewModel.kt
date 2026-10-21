package com.tranquilo.alarm.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.tranquilo.alarm.alarm.AlarmScheduler
import com.tranquilo.alarm.service.SleepMonitorService
import com.tranquilo.alarm.data.AlarmSettings
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

    fun refresh() {
        _settings.value = store.load()
        _report.value = store.loadReport()
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
