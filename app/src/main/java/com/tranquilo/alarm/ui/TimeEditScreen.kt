package com.tranquilo.alarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Простой выбор времени: часы по 1, минуты по 5.
 * Специально без сторонних библиотек — меньше зависимостей, меньше поводов сломаться.
 */
@Composable
fun TimeEditScreen(
    initialHour: Int,
    initialMinute: Int,
    onDone: (hour: Int, minute: Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Разбудить не позже",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.primary
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(
                value = "%02d".format(hour),
                onUp = { hour = (hour + 1) % 24 },
                onDown = { hour = (hour + 23) % 24 }
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.display2,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Stepper(
                value = "%02d".format(minute),
                onUp = { minute = (minute + 5) % 60 },
                onDown = { minute = (minute + 55) % 60 }
            )
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { onDone(hour, minute) },
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Text("Готово")
        }
    }
}

@Composable
private fun Stepper(value: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onUp,
            modifier = Modifier.size(28.dp),
            colors = ButtonDefaults.secondaryButtonColors()
        ) { Text("+", style = MaterialTheme.typography.button) }

        Text(
            text = value,
            style = MaterialTheme.typography.display2,
            modifier = Modifier.padding(vertical = 2.dp)
        )

        Button(
            onClick = onDown,
            modifier = Modifier.size(28.dp),
            colors = ButtonDefaults.secondaryButtonColors()
        ) { Text("−", style = MaterialTheme.typography.button) }
    }
}
