package com.tresabhi.gyrocursor;

import static android.bluetooth.BluetoothHidDevice.SUBCLASS1_KEYBOARD;

import android.Manifest;
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
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = "GyroCursor";

    // scroll: tilt (degrees, relative to where scroll started) inside which nothing scrolls
    private static final double SCROLL_DEADZONE_DEG = 15;

    private static final byte[] HID_DESCRIPTOR = new byte[]{

            // Report 1 (mouse)

            (byte) 0x05, (byte) 0x01,       // Usage Page (Generic Desktop)
            (byte) 0x09, (byte) 0x02,       // Usage (Mouse)
            (byte) 0xA1, (byte) 0x01,       // Collection (Application)

            (byte) 0x85, (byte) 0x01,       // Report ID (1)

            (byte) 0x09, (byte) 0x01,       // Usage (Pointer)
            (byte) 0xA1, (byte) 0x00,       // Collection (Physical)

            (byte) 0x05, (byte) 0x09,       // Usage Page (Button)
            (byte) 0x19, (byte) 0x01,       // Usage Minimum (Button 1)
            (byte) 0x29, (byte) 0x03,       // Usage Maximum (Button 3)

            (byte) 0x15, (byte) 0x00,       // Logical Minimum (0)
            (byte) 0x25, (byte) 0x01,       // Logical Maximum (1)

            (byte) 0x95, (byte) 0x03,       // Report Count (3)
            (byte) 0x75, (byte) 0x01,       // Report Size (1)

            (byte) 0x81, (byte) 0x02,       // Input (Data, Variable, Absolute)

            (byte) 0x95, (byte) 0x01,       // Report Count (1)
            (byte) 0x75, (byte) 0x05,       // Report Size (5)

            (byte) 0x81, (byte) 0x03,       // Input (Constant)

            (byte) 0x05, (byte) 0x01,       // Usage Page (Generic Desktop)

            (byte) 0x09, (byte) 0x30,       // Usage (X)
            (byte) 0x09, (byte) 0x31,       // Usage (Y)
            (byte) 0x09, (byte) 0x38,       // Usage (Wheel)

            (byte) 0x15, (byte) 0x81,       // Logical Minimum (-127)
            (byte) 0x25, (byte) 0x7F,       // Logical Maximum (127)

            (byte) 0x75, (byte) 0x08,       // Report Size (8)
            (byte) 0x95, (byte) 0x03,       // Report Count (3)

            (byte) 0x81, (byte) 0x06,       // Input (Data, Variable, Relative)

            (byte) 0xC0,                    // End Physical Collection
            (byte) 0xC0,                    // End Mouse Collection


            // Report 2 (keyboard, unused, reserved for legacy keyboard app)

            (byte) 0x05, (byte) 0x01,       // Usage Page (Generic Desktop)
            (byte) 0x09, (byte) 0x06,       // Usage (Keyboard)
            (byte) 0xA1, (byte) 0x01,       // Collection (Application)
            (byte) 0x85, SUBCLASS1_KEYBOARD,// Report ID (SUBCLASS1_KEYBOARD)

            (byte) 0x05, (byte) 0x07,       // Usage Page (Keyboard)

            // Modifier keys
            (byte) 0x19, (byte) 0xE0,
            (byte) 0x29, (byte) 0xE7,

            (byte) 0x15, (byte) 0x00,
            (byte) 0x25, (byte) 0x01,

            (byte) 0x75, (byte) 0x01,
            (byte) 0x95, (byte) 0x08,

            (byte) 0x81, (byte) 0x02,

            // Reserved byte
            (byte) 0x75, (byte) 0x08,
            (byte) 0x95, (byte) 0x01,

            (byte) 0x81, (byte) 0x01,

            // Six keyboard keys
            (byte) 0x19, (byte) 0x00,
            (byte) 0x29, (byte) 0x65,

            (byte) 0x15, (byte) 0x00,
            (byte) 0x25, (byte) 0x65,

            (byte) 0x75, (byte) 0x08,
            (byte) 0x95, (byte) 0x06,

            (byte) 0x81, (byte) 0x00,

            (byte) 0xC0                    // End Keyboard Collection
    };

    private final Map<String, Button> deviceButtons = new HashMap<>();
    private static final String PREFS = "gyro_cursor";
    private static final String PREF_HOST = "dashboard_host";
    private static final int DASHBOARD_PORT = 4321;
    private boolean bluetoothStarted = false;
    private boolean restartDiscoveryPending = false;
    private BluetoothDevice pendingBondDevice;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDeviceProfile;
    private BluetoothDevice targetDevice;

    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private Sensor gravitySensor;
    private HandlerThread hidThread;
    private Handler hidHandler;

    private long lastMouseUpdateNs = 0L;

    private Vector3 smooth = new Vector3();

    // matches a 165Hz monitor; not a whole number of ms, so the loop schedules
    // against a running deadline instead of a fixed delay
    private static final double MOUSE_INTERVAL_MS = 1000.0 / 165.0;
    private double nextMouseUpdateMs = 0;
    private static float sensitivity = 900f;

    private boolean mouseLoopRunning = false;

    // mirrors the dashboard's cursor state; "disabled" suppresses all mouse output and
    // only "cursor" moves the pointer. Assumed "cursor" until the dashboard says otherwise.
    private volatile boolean cursorDisabled = false;
    private volatile boolean cursorMoves = true;
    private volatile boolean cursorScrolls = false;
    private Button cycleButton;

    // scroll tilt: angle (rad) about the phone x axis, relative to where it was when scroll began
    private static final double SCROLL_TICKS_PER_SEC_PER_RAD = 0.5 * 180 / Math.PI;
    private static final double SCROLL_MAX_TICKS_PER_SEC = 40;
    private double scrollReferenceAngle = 0;
    private double scrollCarry = 0;
    private boolean scrollReferencePending = false;
    private volatile boolean gravityValid = false;
    private long lastScrollLogMs = 0;
    private volatile int stateStreamGeneration = 0;
    private volatile HttpURLConnection stateStreamConn;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingStreamRestart;
    private volatile boolean isLeftDown = false;
    private volatile boolean isRightDown = false;

    // the magnitude matters for this one
    private volatile Vector3 gyroscope_vec = new Vector3();

    // phone-fixed front vector; needs to be customizable later
    private volatile Vector3 phone_front_vec = new Vector3(0, 1, 0);
    // ground-fixed front vector
    private volatile Vector3 ground_front_vec = new Vector3(0, 1, 0);
    // ground fixed, phone oriented right hand vector
    private volatile Vector3 ground_right_vec = new Vector3(1, 0, 0);
    // ground fixed gravity vector
    private volatile Vector3 ground_gravity_vec = new Vector3(0, -1, 0);

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
                startSensors();
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (targetDevice != null && targetDevice.equals(device)) {
                    targetDevice = null;
                    stopSensors();
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

            if (action == null) {
                return;
            }

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    Log.d(TAG, "Discovery started");
                    statusTextView.setText("Scanning for devices...");
                    break;

                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    Log.d(TAG, "Discovery finished");

                    // cancelDiscovery() is asynchronous, so a rescan requested
                    // mid-scan waits until the old one has actually stopped
                    if (restartDiscoveryPending) {
                        restartDiscoveryPending = false;
                        startDeviceDiscovery();
                    } else if (targetDevice == null && pendingBondDevice == null) {
                        statusTextView.setText("Scan finished. Tap a device to connect.");
                    }
                    break;

                case BluetoothDevice.ACTION_FOUND:
                    if (device != null) {
                        // Classic inquiry often reports the device before its
                        // friendly name is known, so the name may be null here
                        // and arrive later through ACTION_NAME_CHANGED
                        Log.d(TAG, "Found: " + device.getAddress() + " name=" + device.getName());
                        addOrUpdateDeviceButton(device);
                    }
                    break;

                case BluetoothDevice.ACTION_NAME_CHANGED:
                    if (device != null) {
                        Log.d(TAG, "Name resolved: " + device.getAddress() + " -> " + device.getName());
                        addOrUpdateDeviceButton(device);
                    }
                    break;

                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    onBondStateChanged(device, intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createSimpleUI();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        hidThread = new HandlerThread("hid-report-thread");
        hidThread.start();
        hidHandler = new Handler(hidThread.getLooper());

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            statusTextView.setText("Error: Bluetooth not supported.");
            return;
        }

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        ContextCompat.registerReceiver(this, discoveryReceiver, filter, ContextCompat.RECEIVER_EXPORTED);

        BluetoothPermissionManager manager = new BluetoothPermissionManager(this);

        // requesting is asynchronous; if anything is missing, startBluetooth()
        // runs from onRequestPermissionsResult once the user responds
        if (manager.checkAndRequestPermissions()) {
            startBluetooth();
        } else {
            statusTextView.setText("Waiting for Bluetooth permissions...");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != BluetoothPermissionManager.REQUEST_BLUETOOTH_PERMISSIONS) {
            return;
        }

        if (BluetoothPermissionManager.allGranted(grantResults)) {
            startBluetooth();
        } else {
            statusTextView.setText("Error: Bluetooth permissions denied.");
        }
    }

    @SuppressLint("MissingPermission")
    private void startBluetooth() {
        if (bluetoothStarted) {
            return;
        }

        bluetoothStarted = true;

        setupHidProfile();
        loadPairedDevices();
        startDeviceDiscovery();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createSimpleUI() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(32, 32, 32, 32);
        rootLayout.setBackgroundColor(Color.parseColor("#F5F5F7"));
        // edge-to-edge: the window doesn't resize for the keyboard, so pad by the insets ourselves
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(32 + bars.left, 32 + bars.top, 32 + bars.right, 32 + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

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
        scrollParams.setMargins(0, 24, 0, 24);
        scrollView.setLayoutParams(scrollParams);
        scrollView.setBackgroundColor(Color.WHITE);

        deviceContainerLayout = new LinearLayout(this);
        deviceContainerLayout.setOrientation(LinearLayout.VERTICAL);
        deviceContainerLayout.setPadding(16, 16, 16, 16);

        scrollView.addView(deviceContainerLayout);
        rootLayout.addView(scrollView);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        EditText hostInput = new EditText(this);
        hostInput.setHint("Dashboard host (e.g. 192.168.1.5:4321)");
        hostInput.setTextColor(Color.BLACK);
        hostInput.setHintTextColor(Color.GRAY);
        hostInput.setSingleLine(true);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        hostInput.setText(prefs.getString(PREF_HOST, ""));
        hostInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String host = s.toString().trim();
                prefs.edit().putString(PREF_HOST, host).apply();
                uiHandler.removeCallbacks(pendingStreamRestart);
                pendingStreamRestart = () -> startStateStream(host);
                uiHandler.postDelayed(pendingStreamRestart, 600);
            }
        });
        rootLayout.addView(hostInput);

        LinearLayout clickContainer = new LinearLayout(this);
        clickContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clickContainer.setLayoutParams(containerParams);

        LinearLayout.LayoutParams leftButtonParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        leftButtonParams.setMargins(0, 0, 8, 0);

        Button leftClickButton = new AppCompatButton(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int width = getMeasuredWidth();
                setMeasuredDimension(width, width);
            }
        };
        leftClickButton.setText("Left Click");
        leftClickButton.setLayoutParams(leftButtonParams);
        leftClickButton.setOnTouchListener((v, event) -> {
            handleButtonTouch(event, true);
            return true;
        });

        LinearLayout.LayoutParams rightButtonParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        rightButtonParams.setMargins(8, 0, 0, 0);

        LinearLayout.LayoutParams cycleButtonParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        cycleButtonParams.setMargins(8, 0, 8, 0);

        Button rightClickButton = new AppCompatButton(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int width = getMeasuredWidth();
                setMeasuredDimension(width, width);
            }
        };
        rightClickButton.setText("Right Click");
        rightClickButton.setLayoutParams(rightButtonParams);
        rightClickButton.setOnTouchListener((v, event) -> {
            handleButtonTouch(event, false);
            return true;
        });

        cycleButton = new AppCompatButton(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int width = getMeasuredWidth();
                setMeasuredDimension(width, width);
            }
        };
        cycleButton.setText("Cycle");
        cycleButton.setLayoutParams(cycleButtonParams);
        cycleButton.setOnClickListener(v -> cycleCursor(hostInput.getText().toString().trim()));

        clickContainer.addView(leftClickButton);
        clickContainer.addView(cycleButton);
        clickContainer.addView(rightClickButton);
        rootLayout.addView(clickContainer);

        setContentView(rootLayout);
        startStateStream(hostInput.getText().toString().trim());
    }

    private static String dashboardBase(String host) {
        String base = host.contains("://") ? host : "http://" + host;
        if (!host.matches(".*:\\d+$")) {
            base += ":" + DASHBOARD_PORT;
        }
        return base;
    }

    /** Follows the dashboard's state events, reconnecting until the host changes or we're destroyed. */
    private void startStateStream(String host) {
        final int generation = ++stateStreamGeneration;
        HttpURLConnection old = stateStreamConn;
        if (old != null) {
            old.disconnect();
        }
        if (host.isEmpty()) {
            cycleButton.setText("Cycle");
            return;
        }
        final String url = dashboardBase(host) + "/api/state/cursor/events";
        new Thread(() -> {
            while (generation == stateStreamGeneration) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(2000);
                    stateStreamConn = conn;
                    try (BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while (generation == stateStreamGeneration && (line = in.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                applyCursorState(line.substring(6).trim());
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w("GyroCursor", "state stream: " + e.getMessage());
                }
                if (generation == stateStreamGeneration) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
            }
        }, "state-stream").start();
    }

    @SuppressLint("MissingPermission")
    private void applyCursorState(String state) {
        Log.d(TAG, "state event: '" + state + "'");
        runOnUiThread(() -> cycleButton.setText(state));
        final boolean disabled = state.equals("disabled");
        final boolean moves = state.equals("cursor");
        final boolean scrolls = state.equals("scroll");
        // run on the hid thread so the release report can't interleave with the mouse loop
        hidHandler.post(() -> {
            if (disabled) {
                // release anything held so the host isn't left with a stuck button
                isLeftDown = false;
                isRightDown = false;
                sendHidReport((byte) 0, (byte) 0);
                smooth.set(0, 0, 0);
            }
            cursorDisabled = disabled;
            cursorMoves = moves;
            if (!moves) {
                smooth.set(0, 0, 0);
            }
            if (scrolls && !cursorScrolls) {
                // the reference is captured on the first tick with real gravity data
                scrollReferencePending = true;
                scrollCarry = 0;
            }
            cursorScrolls = scrolls;
        });
    }

    private void cycleCursor(String host) {
        if (host.isEmpty()) {
            Toast.makeText(this, "Enter the dashboard host first", Toast.LENGTH_SHORT).show();
            return;
        }
        final String url = dashboardBase(host) + "/api/state/cursor/cycle";
        Executors.newSingleThreadExecutor().execute(() -> {
            String error = null;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                int code = conn.getResponseCode();
                if (code >= 400) {
                    error = "Dashboard returned " + code;
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e("GyroCursor", "cycle failed", e);
                error = "Cycle failed: " + e.getMessage();
            }
            if (error != null) {
                final String msg = error;
                runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @SuppressLint("MissingPermission")
    private static String getDeviceName(BluetoothDevice device) {
        String name = device.getName();

        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        return name.trim();
    }

    // unnamed devices stay hidden until ACTION_NAME_CHANGED gives them a name
    private void addOrUpdateDeviceButton(BluetoothDevice device) {
        String name = getDeviceName(device);

        if (name == null) {
            return;
        }

        String displayName = name + "\n[" + device.getAddress() + "]";
        Button existing = deviceButtons.get(device.getAddress());

        if (existing != null) {
            existing.setText(displayName);
            return;
        }

        Button deviceButton = new Button(this);
        deviceButton.setText(displayName);
        deviceButton.setTransformationMethod(null);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        deviceButton.setLayoutParams(params);

        deviceButton.setOnClickListener(v -> onDeviceSelected(device));

        deviceButtons.put(device.getAddress(), deviceButton);
        deviceContainerLayout.addView(deviceButton);
    }

    @SuppressLint("MissingPermission")
    private void onDeviceSelected(BluetoothDevice device) {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            restartDiscoveryPending = false;
            bluetoothAdapter.cancelDiscovery();
        }

        // the HID Device profile won't bond on its own, so pair first and
        // connect once the bond completes
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            pendingBondDevice = device;
            statusTextView.setText("Pairing with: " + device.getAddress() + "...");

            if (device.getBondState() == BluetoothDevice.BOND_NONE && !device.createBond()) {
                Log.w(TAG, "createBond() returned false for " + device.getAddress());
                pendingBondDevice = null;
                statusTextView.setText("Error: could not start pairing.");
            }
            return;
        }

        connectHid(device);
    }

    @SuppressLint("MissingPermission")
    private void onBondStateChanged(BluetoothDevice device, int bondState) {
        if (device == null) {
            return;
        }

        Log.d(TAG, "Bond state for " + device.getAddress() + ": " + bondState);

        if (pendingBondDevice == null || !pendingBondDevice.equals(device)) {
            return;
        }

        if (bondState == BluetoothDevice.BOND_BONDED) {
            pendingBondDevice = null;
            connectHid(device);
        } else if (bondState == BluetoothDevice.BOND_NONE) {
            pendingBondDevice = null;
            statusTextView.setText("Pairing failed or was rejected.");
        }
    }

    @SuppressLint("MissingPermission")
    private void connectHid(BluetoothDevice device) {
        statusTextView.setText("Connecting to: " + device.getAddress() + "...");

        if (hidDeviceProfile == null) {
            statusTextView.setText("Error: HID profile not ready yet.");
            return;
        }

        if (!hidDeviceProfile.connect(device)) {
            Log.w(TAG, "connect() returned false for " + device.getAddress());
            statusTextView.setText("Error: could not connect to " + device.getAddress());
        }
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
                    stopSensors();
                    runOnUiThread(() -> statusTextView.setText("HID Profile State: Disconnected"));
                }
            }
        }, BluetoothProfile.HID_DEVICE);
    }

    @SuppressLint("MissingPermission")
    private void startDeviceDiscovery() {
        if (bluetoothAdapter == null || !bluetoothStarted) {
            return;
        }

        stopSensors();

        if (bluetoothAdapter.isDiscovering()) {
            // restarted from ACTION_DISCOVERY_FINISHED
            restartDiscoveryPending = true;
            bluetoothAdapter.cancelDiscovery();
            return;
        }

        boolean started = bluetoothAdapter.startDiscovery();
        Log.d(TAG, "startDiscovery() returned " + started);

        if (!started) {
            statusTextView.setText("Error: could not start scan. Is Bluetooth on?");
        }
    }

    private void startSensors() {
        if (gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME);
        }

        if (!mouseLoopRunning) {
            mouseLoopRunning = true;
            lastMouseUpdateNs = 0L;
            nextMouseUpdateMs = SystemClock.uptimeMillis();

            hidHandler.post(mouseRunnable);
        }
    }

    private void stopSensors() {
        sensorManager.unregisterListener(this);

        mouseLoopRunning = false;
        hidHandler.removeCallbacks(mouseRunnable);

        lastMouseUpdateNs = 0L;
        smooth.set(0, 0, 0);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                gyroscope_vec.set(event.values);
                break;

            case Sensor.TYPE_GRAVITY:
                gravityValid = true;
                // andriod gravity sensor points opposite to the direction of
                // gravity; the abstraction of "down" is better so we amend
                // this

                ground_gravity_vec
                        .set(event.values)
                        .multiply(-1)
                        .normalize();
                break;
        }

        ground_right_vec.copy(ground_gravity_vec.cross(phone_front_vec)).normalize();
        ground_front_vec.copy(ground_right_vec.cross(ground_gravity_vec)).normalize();
    }

    private final Runnable mouseRunnable = new Runnable() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void run() {

            if (!mouseLoopRunning) {
                return;
            }

            long now = System.nanoTime();

            if (lastMouseUpdateNs == 0L) {
                lastMouseUpdateNs = now;
            }

            float deltaTime = (now - lastMouseUpdateNs) / 1_000_000_000f;

            lastMouseUpdateNs = now;

            processSmoothMouseMovement(deltaTime);

            double uptimeMs = SystemClock.uptimeMillis();
            nextMouseUpdateMs += MOUSE_INTERVAL_MS;

            // fell behind (thread was starved); resync instead of bursting
            if (nextMouseUpdateMs < uptimeMs) {
                nextMouseUpdateMs = uptimeMs + MOUSE_INTERVAL_MS;
            }

            hidHandler.postAtTime(this, (long) nextMouseUpdateMs);
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void processSmoothMouseMovement(float deltaTime) {
        if (cursorScrolls) {
            processScroll(deltaTime);
            return;
        }
        if (!cursorMoves) {
            return;
        }
        Vector3 displacement = new Vector3(gyroscope_vec.z, gyroscope_vec.x, 0)
                .multiply(sensitivity)
                .multiply(deltaTime);
        smooth.add(displacement);

        Vector3 quantized = smooth.clone().__cursorQuantize();

        smooth.subtract(quantized);
        quantized.multiply(-1).__cursorClamp();

        sendHidReport((byte) quantized.x, (byte) quantized.y);
    }

    /**
     * Signed angle of gravity (projected onto the plane perpendicular to the phone x axis)
     * from the phone's -z axis, about +x. Zero when the phone lies flat face-up; positive
     * when the top edge tips down.
     */
    private double currentTiltAngle() {
        Vector3 g = ground_gravity_vec;
        // cross(-z, g).x = g.y and dot(-z, g) = -g.z; the x component of g drops out
        return Math.atan2(g.y, -g.z);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void processScroll(float deltaTime) {
        if (!gravityValid) {
            return;
        }
        if (scrollReferencePending) {
            // remember where the phone is held now; tilt is measured relative to this
            scrollReferenceAngle = currentTiltAngle();
            scrollReferencePending = false;
            Log.d(TAG, "scroll start: reference=" + Math.toDegrees(scrollReferenceAngle) + "deg");
        }
        double rel = currentTiltAngle() - scrollReferenceAngle;
        rel = Math.atan2(Math.sin(rel), Math.cos(rel)); // wrap to [-pi, pi]

        double excess = Math.abs(rel) - Math.toRadians(SCROLL_DEADZONE_DEG);
        long nowMs = SystemClock.uptimeMillis();
        if (nowMs - lastScrollLogMs >= 100) {
            lastScrollLogMs = nowMs;
            Vector3 g = ground_gravity_vec;
            Log.d(TAG, String.format("scroll tilt=%.1f ref=%.1f rel=%.1f g=(%.2f,%.2f,%.2f)",
                    Math.toDegrees(currentTiltAngle()), Math.toDegrees(scrollReferenceAngle),
                    Math.toDegrees(rel), g.x, g.y, g.z));
        }
        if (excess <= 0) {
            scrollCarry = 0;
            return;
        }

        double rate = Math.min(excess * SCROLL_TICKS_PER_SEC_PER_RAD, SCROLL_MAX_TICKS_PER_SEC);
        scrollCarry += Math.signum(rel) * rate * deltaTime;

        int ticks = (int) scrollCarry;
        if (ticks == 0) {
            return;
        }
        scrollCarry -= ticks;

        Log.d(TAG, "scroll ticks=" + ticks);
        // tipping the top edge down (positive angle) scrolls down; HID wheel + is up
        sendHidReport((byte) 0, (byte) 0, (byte) Math.clamp(-ticks, -127, 127));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @SuppressLint("MissingPermission")
    private void handleButtonTouch(MotionEvent event, boolean isSideLeft) {
        int action = event.getActionMasked();

        if (cursorDisabled) {
            return;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            if (isSideLeft) {
                isLeftDown = true;
            } else {
                isRightDown = true;
            }

            hidHandler.post(() -> sendHidReport((byte) 0, (byte) 0));
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (isSideLeft) {
                isLeftDown = false;
            } else {
                isRightDown = false;
            }

            hidHandler.post(() -> sendHidReport((byte) 0, (byte) 0));
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void sendHidReport(byte du, byte dv) {
        sendHidReport(du, dv, (byte) 0);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void sendHidReport(byte du, byte dv, byte wheel) {
        if (hidDeviceProfile == null || targetDevice == null || cursorDisabled) {
            return;
        }

        byte buttonState = 0;

        if (isLeftDown) {
            buttonState |= 0b00000001;
        }

        if (isRightDown) {
            buttonState |= 0b00000010;
        }

        byte[] mouseReport = new byte[]{ buttonState, du, dv, wheel };

        hidDeviceProfile.sendReport(targetDevice, (byte) 0x01, mouseReport);
        // hidDeviceProfile.sendReport(targetDevice, (byte) 0x02, keyboardReport);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stateStreamGeneration++;
        if (stateStreamConn != null) {
            stateStreamConn.disconnect();
        }
        uiHandler.removeCallbacksAndMessages(null);
        stopSensors();
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void loadPairedDevices() {
        if (bluetoothAdapter == null) {
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : pairedDevices) {
            addOrUpdateDeviceButton(device);
        }
    }
}