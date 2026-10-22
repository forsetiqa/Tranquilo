package com.tranquilo.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tranquilo.alarm.service.SleepMonitorService
import com.tranquilo.alarm.ui.TranquiloTheme
import com.tranquilo.alarm.ui.WideButton
import java.time.LocalTime

class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            TranquiloTheme {
                RingScreen(onDismiss = {
                    SleepMonitorService.stop(this)
                    finish()
                })
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun RingScreen(onDismiss: () -> Unit) {
    val now = LocalTime.now()

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "%02d:%02d".format(now.hour, now.minute),
            style = MaterialTheme.typography.display1,
            color = MaterialTheme.colors.primary
        )

        Text(
            text = "Доброе утро",
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(Modifier.height(12.dp))

        WideButton(text = "Выключить", onClick = onDismiss)
    }
}
