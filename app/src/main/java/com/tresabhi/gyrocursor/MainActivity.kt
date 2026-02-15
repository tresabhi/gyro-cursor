package com.tresabhi.gyrocursor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tresabhi.gyrocursor.ui.theme.GyroCursorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        enableEdgeToEdge()

        setContent {
            GyroCursorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GyroTracker(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun GyroTracker(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    val gyroSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var z by remember { mutableFloatStateOf(0f) }
    var lastTimestamp by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (lastTimestamp == 0L) {
                    lastTimestamp = event.timestamp
                    return
                }

                val dt = (event.timestamp - lastTimestamp) * 1e-9f
                lastTimestamp = event.timestamp

                x += event.values[0] * dt
                y += event.values[1] * dt
                z += event.values[2] * dt
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener,
            gyroSensor,
            SensorManager.SENSOR_DELAY_GAME
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable {
                x = 0f
                y = 0f
                z = 0f
                lastTimestamp = 0L
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = 300f
            var u = -y
            var v = x

            drawCircle(
                color = Color.hsl(0f, 0f, 0.25f),
                radius = 20.dp.toPx(),
                center = Offset(
                    x = size.width / 2 + u * scale,
                    y = size.height / 2 + v * scale
                )
            )
        }
    }
}