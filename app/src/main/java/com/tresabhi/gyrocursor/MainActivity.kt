package com.tresabhi.gyrocursor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tresabhi.gyrocursor.ui.theme.GyroCursorTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var hidManager: HidMouseManager

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
                    GyroTracker(modifier = Modifier.padding(innerPadding), hidManager)
                }
            }


        }

        hidManager = HidMouseManager(this)
        hidManager.init()

        requestPermissions(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ), 1
        )
    }
}

@Composable
fun GyroTracker(modifier: Modifier = Modifier, hidManager: HidMouseManager) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val gyroSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }

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

                val scale = 10f
                val dx = (-y * scale).toInt()
                val dy = (x * scale).toInt()
                hidManager.move(dx, dy)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose { sensorManager.unregisterListener(listener) }
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
            drawCircle(
                color = Color.hsl(0f, 0f, 0.25f),
                radius = 20.dp.toPx(),
                center = Offset(
                    x = size.width / 2 - y * scale,
                    y = size.height / 2 + x * scale
                )
            )
        }
    }
}

class HidMouseManager(private val context: Context) {
    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {}
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            hostDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
        }
    }

    fun init() {
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hidDevice = proxy as BluetoothHidDevice

                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "GyroMouse",
                    "Gyroscope Mouse",
                    "GyroCursor",
                    BluetoothHidDevice.SUBCLASS1_MOUSE,
                    byteArrayOf(
                        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x02.toByte(),
                        0xa1.toByte(), 0x01.toByte(), 0x09.toByte(), 0x01.toByte(),
                        0xa1.toByte(), 0x00.toByte(), 0x05.toByte(), 0x09.toByte(),
                        0x19.toByte(), 0x01.toByte(), 0x29.toByte(), 0x03.toByte(),
                        0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(),
                        0x95.toByte(), 0x03.toByte(), 0x75.toByte(), 0x01.toByte(),
                        0x81.toByte(), 0x02.toByte(), 0x95.toByte(), 0x01.toByte(),
                        0x75.toByte(), 0x05.toByte(), 0x81.toByte(), 0x03.toByte(),
                        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x30.toByte(),
                        0x09.toByte(), 0x31.toByte(), 0x15.toByte(), 0x81.toByte(),
                        0x25.toByte(), 0x7f.toByte(), 0x75.toByte(), 0x08.toByte(),
                        0x95.toByte(), 0x02.toByte(), 0x81.toByte(), 0x06.toByte(),
                        0xc0.toByte(), 0xc0.toByte()
                    )
                )

                val qos = BluetoothHidDeviceAppQosSettings(
                    BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED,
                    800, 9, 0, 11250, 11250
                )

                val permission = Manifest.permission.BLUETOOTH_CONNECT
                if (ContextCompat.checkSelfPermission(
                        context,
                        permission
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    hidDevice?.registerApp(sdp, qos, qos, executor, callback)
                } else {
                    println("BLUETOOTH_CONNECT permission not granted")
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                hidDevice = null
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun move(dx: Int, dy: Int) {
        val permission = Manifest.permission.BLUETOOTH_CONNECT
        if (ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            hostDevice?.let { device ->
                val report = byteArrayOf(0x00, dx.toByte(), dy.toByte(), 0x00)
                try {
                    hidDevice?.sendReport(device, 0, report)
                } catch (_: SecurityException) {
                    println("Cannot send HID report: BLUETOOTH_CONNECT not granted")
                }
            }
        } else {
            println("BLUETOOTH_CONNECT permission not granted")
        }
    }
}