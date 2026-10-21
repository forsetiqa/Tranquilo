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
import com.tranquilo.alarm.ui.MainScreen
import com.tranquilo.alarm.ui.MainViewModel
import com.tranquilo.alarm.ui.TimeEditScreen
import com.tranquilo.alarm.ui.TranquiloTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TranquiloTheme {
                val vm: MainViewModel = viewModel()
                val settings by vm.settings.collectAsState()
                var editingTime by remember { mutableStateOf(false) }

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* отказ не ломает будильник: вибрация сработает в любом случае */ }

                LaunchedEffect(Unit) {
                    vm.refresh()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
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
                    MainScreen(viewModel = vm, onEditTime = { editingTime = true })
                }
            }
        }
    }
}
