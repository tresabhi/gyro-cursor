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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tresabhi.gyrocursor.ui.theme.GyroCursorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var lastTimestamp by remember { mutableLongStateOf((0L)) }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (lastTimestamp == 0L) {
                    lastTimestamp = event.timestamp
                }

                val dt = (event.timestamp - lastTimestamp) * 1e-9f;
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

    Column(modifier = modifier) {
        Text("Gyroscope values:")

        Text("X: ${"%.2f".format(x)}")
        Text("Y: ${"%.2f".format(y)}")
        Text("Z: ${"%.2f".format(z)}")
    }
}