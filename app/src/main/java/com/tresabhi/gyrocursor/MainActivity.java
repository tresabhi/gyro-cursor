package com.tresabhi.gyrocursor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = "GyroCursor";

    private static final byte[] HID_DESCRIPTOR = new byte[]{
            (byte) 0x05, (byte) 0x01, // Usage Page (Generic Desktop Ctrls)
            (byte) 0x09, (byte) 0x02, // Usage (Mouse)
            (byte) 0xA1, (byte) 0x01, // Collection (Application)
            (byte) 0x09, (byte) 0x01, //   Usage (Pointer)
            (byte) 0xA1, (byte) 0x00, //   Collection (Physical)
            (byte) 0x05, (byte) 0x09, //     Usage Page (Button)
            (byte) 0x19, (byte) 0x01, //     Usage Minimum (1)
            (byte) 0x29, (byte) 0x03, //     Usage Maximum (3)
            (byte) 0x15, (byte) 0x00, //     Logical Minimum (0)
            (byte) 0x25, (byte) 0x01, //     Logical Maximum (1)
            (byte) 0x95, (byte) 0x03, //     Report Count (3)
            (byte) 0x75, (byte) 0x01, //     Report Size (1)
            (byte) 0x81, (byte) 0x02, //     Input (Data, Var, Abs)
            (byte) 0x95, (byte) 0x01, //     Report Count (1)
            (byte) 0x75, (byte) 0x05, //     Report Size (5)
            (byte) 0x81, (byte) 0x03, //     Input (Cnst, Var, Abs)
            (byte) 0x05, (byte) 0x01, //
            (byte) 0x09, (byte) 0x30,
            (byte) 0x09, (byte) 0x31,
            (byte) 0x15, (byte) 0x81,
            (byte) 0x25, (byte) 0x7F,
            (byte) 0x75, (byte) 0x08,
            (byte) 0x95, (byte) 0x02, // also 0x03
            (byte) 0x81, (byte) 0x06,

            (byte) 0xC0,
            (byte) 0xC0
    };

    private final Set<String> discoveredAddresses = new HashSet<>();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDeviceProfile;
    private BluetoothDevice targetDevice;

    private SensorManager sensorManager;
    private Sensor gyroscope;
    private HandlerThread hidThread;
    private Handler hidHandler;

    private float carryU = 0f;
    private float carryV = 0f;

    private TextView statusTextView;
    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
            Log.d(TAG, "HID registration status changed. Registered: " + registered);
            runOnUiThread(() -> statusTextView.setText("HID Profile State: " + (registered ? "Registered" : "Unregistered")));
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            Log.d(TAG, "Connection state changed: " + state);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                targetDevice = device;
                runOnUiThread(() -> statusTextView.setText("Connected to: " + device.getAddress()));
                startGyroscope();
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (targetDevice != null && targetDevice.equals(device)) {
                    targetDevice = null;
                    stopGyroscope();
                    runOnUiThread(() -> statusTextView.setText("Disconnected. Standby."));
                }
            }
        }
    };
    private LinearLayout deviceContainerLayout;
    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    String address = device.getAddress();

                    if (discoveredAddresses.add(address)) {
                        String name = device.getName();
                        String displayName = (name != null ? name : "Unknown Device") + "\n[" + address + "]";

                        runOnUiThread(() -> addDeviceButton(device, displayName));
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createSimpleUI();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        hidThread = new HandlerThread("hid-report-thread");
        hidThread.start();
        hidHandler = new Handler(hidThread.getLooper());

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            statusTextView.setText("Error: Bluetooth not supported.");
            return;
        }

        setupHidProfile();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryReceiver, filter);

        startDeviceDiscovery();
    }

    private void createSimpleUI() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(32, 32, 32, 32);
        rootLayout.setBackgroundColor(Color.parseColor("#F5F5F7"));

        statusTextView = new TextView(this);
        statusTextView.setText("HID Profile State: Initializing...");
        statusTextView.setTextSize(16);
        statusTextView.setPadding(0, 0, 0, 16);
        statusTextView.setTextColor(Color.DKGRAY);
        rootLayout.addView(statusTextView);

        Button scanButton = new Button(this);
        scanButton.setText("Scan for Devices");
        scanButton.setOnClickListener(v -> startDeviceDiscovery());
        rootLayout.addView(scanButton);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.setMargins(0, 24, 0, 0);
        scrollView.setLayoutParams(scrollParams);
        scrollView.setBackgroundColor(Color.WHITE);

        deviceContainerLayout = new LinearLayout(this);
        deviceContainerLayout.setOrientation(LinearLayout.VERTICAL);
        deviceContainerLayout.setPadding(16, 16, 16, 16);

        scrollView.addView(deviceContainerLayout);
        rootLayout.addView(scrollView);

        setContentView(rootLayout);
    }

    @SuppressLint("MissingPermission")
    private void addDeviceButton(BluetoothDevice device, String displayName) {
        Button deviceButton = new Button(this);
        deviceButton.setText(displayName);
        deviceButton.setTransformationMethod(null); // Keeps casing intact

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        deviceButton.setLayoutParams(params);

        deviceButton.setOnClickListener(v -> {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            statusTextView.setText("Connecting to: " + device.getAddress() + "...");

            if (hidDeviceProfile != null) {
                hidDeviceProfile.connect(device);
            }
        });

        deviceContainerLayout.addView(deviceButton);
    }

    @SuppressLint("MissingPermission")
    private void setupHidProfile() {
        bluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDeviceProfile = (BluetoothHidDevice) proxy;

                    BluetoothHidDeviceAppSdpSettings sdpSettings = new BluetoothHidDeviceAppSdpSettings(
                            "GyroCursorMouse", "Android Gyro Mouse", "Android",
                            BluetoothHidDevice.SUBCLASS1_MOUSE, HID_DESCRIPTOR
                    );

                    hidDeviceProfile.registerApp(sdpSettings, null, null,
                            Executors.newSingleThreadExecutor(), callback);
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDeviceProfile = null;
                    stopGyroscope();
                    runOnUiThread(() -> statusTextView.setText("HID Profile State: Disconnected"));
                }
            }
        }, BluetoothProfile.HID_DEVICE);
    }

    @SuppressLint("MissingPermission")
    private void startDeviceDiscovery() {
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }

            stopGyroscope();
            discoveredAddresses.clear();
            deviceContainerLayout.removeAllViews();

            bluetoothAdapter.startDiscovery();
        }
    }

    private void startGyroscope() {
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void stopGyroscope() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            final float gyroX = event.values[0];
            final float gyroY = event.values[1];
            final float gyroZ = event.values[2];

            // Pipeline off the main UI thread immediately
            hidHandler.post(() -> processAndSendHid(gyroX, gyroY, gyroZ));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void processAndSendHid(float gx, float gy, float gz) {
        if (hidDeviceProfile == null || targetDevice == null) {
            return;
        }

        float sensitivity = 15f;
        float rawY = gx * sensitivity;
        float rawZ = gz * sensitivity;

        carryU += rawZ;
        carryV += rawY;

        int du = (int) carryU;
        int dv = (int) carryV;

        carryU -= du;
        carryV -= dv;

        du *= -1;
        dv *= -1;

        if (du == 0 && dv == 0) {
            return;
        }

        byte reportX = (byte) Math.max(-127, Math.min(127, du));
        byte reportY = (byte) Math.max(-127, Math.min(127, dv));

        byte[] reportBuffer = new byte[]{
                (byte) 0x00,
                reportX,
                reportY
        };

        hidDeviceProfile.sendReport(targetDevice, (byte) 0x01, reportBuffer);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Keep tracking if running background connection, otherwise cleanup gracefully
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopGyroscope();
        try {
            unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (hidThread != null) {
            hidThread.quitSafely();
        }
        if (bluetoothAdapter != null && hidDeviceProfile != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDeviceProfile);
        }
    }
}