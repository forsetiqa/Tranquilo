package com.tranquilo.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tranquilo.alarm.service.HeartRateAccess
import com.tranquilo.alarm.ui.MainScreen
import com.tranquilo.alarm.ui.MainViewModel
import com.tranquilo.alarm.ui.TimeEditScreen
import com.tranquilo.alarm.ui.TranquiloTheme

class MainActivity : ComponentActivity() {

    /** Есть ли полный доступ к пульсу. Обновляется при каждом возврате в приложение. */
    private var heartRateAccess by mutableStateOf(false)

    /** Ссылка на ViewModel, чтобы обновлять экран при каждом возврате в приложение. */
    private var viewModelRef: MainViewModel? = null

    override fun onResume() {
        super.onResume()
        heartRateAccess = HeartRateAccess.full(this)
        viewModelRef?.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TranquiloTheme {
                val vm: MainViewModel = viewModel()
                viewModelRef = vm
                val settings by vm.settings.collectAsState()
                var editingTime by remember { mutableStateOf(false) }

                // Шаг 2: пульс в фоне (Android 13+). Датчик включается ночью по будильнику.
                val backgroundLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { heartRateAccess = HeartRateAccess.full(this@MainActivity) }

                // Шаг 1: уведомления и пульс. Отказ ничего не ломает:
                // без уведомлений будильник всё равно вибрирует, без пульса будит по движению.
                val firstLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    heartRateAccess = HeartRateAccess.full(this@MainActivity)
                    askBackgroundIfNeeded { backgroundLauncher.launch(HeartRateAccess.BACKGROUND) }
                }

                val requestAll = {
                    val missing = buildList {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !granted(Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (!HeartRateAccess.foreground(this@MainActivity)) {
                            add(Manifest.permission.BODY_SENSORS)
                        }
                    }
                    if (missing.isNotEmpty()) {
                        firstLauncher.launch(missing.toTypedArray())
                    } else {
                        askBackgroundIfNeeded { backgroundLauncher.launch(HeartRateAccess.BACKGROUND) }
                    }
                }

                LaunchedEffect(Unit) {
                    vm.refresh()
                    requestAll()
                }

                if (editingTime) {
                    TimeEditScreen(
                        initialHour = settings.windowEndHour,
                        initialMinute = settings.windowEndMinute
                    ) { hour, minute ->
                        vm.setWindowEnd(hour, minute)
                        editingTime = false
                    }
                } else {
                    MainScreen(
                        viewModel = vm,
                        heartRateAccess = heartRateAccess,
                        onRequestHeartRate = { requestAll() },
                        onEditTime = { editingTime = true }
                    )
                }
            }
        }
    }

    private fun askBackgroundIfNeeded(launch: () -> Unit) {
        if (HeartRateAccess.foreground(this) && !HeartRateAccess.background(this)) launch()
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
