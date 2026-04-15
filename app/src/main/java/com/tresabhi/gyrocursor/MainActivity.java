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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executor;


@SuppressLint("MissingPermission")
public class MainActivity extends Activity {
    private static final String TAG = "GYRO_DEBUG_MAIN";

    public static BluetoothHidDevice sharedHid;
    public static BluetoothDevice sharedTarget;

    private final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
    private final ArrayList<BluetoothDevice> pairedDevices = new ArrayList<>();
    private final ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private final HashMap<String, BluetoothDevice> discoveredMap = new HashMap<>();
    private BluetoothDevice targetDevice;
    private BluetoothHidDevice hidDevice;
    private BroadcastReceiver receiver;
    private LinearLayout pairedContainer;

    private int regState;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate ran");
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        controller.hide(android.view.WindowInsets.Type.statusBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        setContentView(R.layout.device_selector);

        pairedContainer = findViewById(R.id.container);
        String[] devices = {"TRESLAPTOP", "OORT_CLOUD", "TrèsAuditory", "fedora", "Someone’s iPad", "TrèsTemporal"};
        int index = 0;

        for (String device : devices) {
            View entry = getLayoutInflater().inflate(R.layout.device_selector_entry_mixed, pairedContainer, false);
            TextView nameView = entry.findViewById(R.id.name);
            ImageView iconView = entry.findViewById(R.id.imageView);

            nameView.setText(device);
            pairedContainer.addView(entry);
            iconView.setImageResource(index++ % 2 == 0 ? R.drawable.mixed_icon : R.drawable.computer_icon);
        }

        BluetoothPermissionManager bluetoothPermissionManager = new BluetoothPermissionManager(this);
        bluetoothPermissionManager.checkAndRequestPermissions();
        getProxy();
        rebuildDeviceUI();
        findAvailableDevices();

        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN not granted");
            return;
        }

        boolean started = btAdapter.startDiscovery();
        Log.d(TAG, "startDiscovery returned=" + started);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getProxy();
        Log.d(TAG, "on Resume");
        if (targetDevice != null && hidDevice != null) {
            Log.d(TAG, "TD: " + targetDevice.getName() + "HID: " + hidDevice);
            connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    protected void onDestroy() {
        super.onDestroy();
        if (hidDevice != null && targetDevice != null) {
            hidDevice.disconnect(targetDevice);
        }
    }

    private void getProxy() {
        btAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
                        @Override
                        public void onConnectionStateChanged(BluetoothDevice device, final int state) {
                            if (!device.equals(targetDevice)) return;

                            runOnUiThread(() -> {
                                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                                    Log.d(TAG, "Disconnected: " + device.getName());

                                } else if (state == BluetoothProfile.STATE_CONNECTING) {
                                    toastMessage("Connecting...");

                                } else if (state == BluetoothProfile.STATE_CONNECTED) {
                                    toastMessage("Connected");

                                    Intent intent = new Intent(MainActivity.this, ForwardChooserActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);

                                } else if (state == BluetoothProfile.STATE_DISCONNECTING) {
                                    Log.d(TAG, "Disconnecting: " + device.getName());
                                }
                            });
                        }

                        @Override
                        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
                            super.onAppStatusChanged(pluggedDevice, registered);
                            Log.d(TAG, registered ? "HID Device registered successfully" : "HID Device registration failed");
                            if (registered) {
                                regState = 1;
                            } else {
                                regState = 0;
                            }
                        }

                        @Override
                        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
                            Log.d(TAG, "onGetReport: device=" + device + " type=" + type
                                    + " id=" + id + " bufferSize=" + bufferSize);
                        }

                        @Override
                        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] report) {
                            Log.d(TAG, "onSetReport: device=" + device + " type=" + type
                                    + " id=" + id + " report length=" + (report != null ? report.length : "null"));
                        }
                    };
                    registerHidDevice(proxy, callback);
                }
            }


            @Override
            public void onServiceDisconnected(int profile) {
            }
        }, BluetoothProfile.HID_DEVICE);
    }

    private void registerHidDevice(BluetoothProfile proxy, BluetoothHidDevice.Callback callback) {
        hidDevice = (BluetoothHidDevice) proxy;

        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "BlueHID",
                "Android HID hackery",
                "Android",
                SUBCLASS1_KEYBOARD,
                getDescriptor()
        );
        Executor executor = runnable -> new Thread(runnable).start();

        hidDevice.registerApp(sdp, null, null, executor, callback);

        hidDevice = (BluetoothHidDevice) proxy;
        sharedHid = hidDevice;
        sharedTarget = targetDevice;
    }

    private void addSectionTitle(String title) {
        View header = getLayoutInflater().inflate(
                R.layout.device_selector_section_header,
                pairedContainer,
                false
        );

        TextView text = header.findViewById(R.id.sectionTitle);
        text.setText(title);

        pairedContainer.addView(header);
    }


    private void addDeviceRow(BluetoothDevice device, int index) {

        View entry = getLayoutInflater().inflate(
                R.layout.device_selector_entry_mixed,
                pairedContainer,
                false
        );

        TextView nameView = entry.findViewById(R.id.name);
        ImageView iconView = entry.findViewById(R.id.imageView);

        String name = device.getName() != null ? device.getName() : device.getAddress();
        nameView.setText(name);

        iconView.setImageResource(
                index % 2 == 0 ? R.drawable.mixed_icon : R.drawable.computer_icon
        );

        entry.setOnClickListener(v -> {
            targetDevice = device;

            Intent intent = new Intent(MainActivity.this, ConnectingActivity.class);
            intent.putExtra("device_name", device.getName());
            intent.putExtra("device_address", device.getAddress());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            connect();
        });

        pairedContainer.addView(entry);
    }


    private void rebuildDeviceUI() {
        pairedContainer.removeAllViews();

        pairedDevices.clear();
        pairedDevices.addAll(btAdapter.getBondedDevices());

        addSectionTitle("Paired");

        int index = 0;
        for (BluetoothDevice device : pairedDevices) {
            addDeviceRow(device, index++);
        }

        addSectionTitle("Other Devices");

        for (BluetoothDevice device : discoveredDevices) {
            if (isPaired(device)) continue;
            addDeviceRow(device, index++);
        }
    }

    private boolean isPaired(BluetoothDevice device) {
        for (BluetoothDevice d : pairedDevices) {
            if (d.getAddress().equals(device.getAddress())) return true;
        }
        return false;
    }


    private void findAvailableDevices() {

        receiver = new BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "ACTION_RECEIVED: " + intent.getAction());

                if (!BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) return;

                BluetoothDevice device =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device == null || device.getAddress() == null) return;

                String addr = device.getAddress();

                if (!discoveredMap.containsKey(addr)) {
                    discoveredMap.put(addr, device);
                    discoveredDevices.add(device);

                    runOnUiThread(() -> rebuildDeviceUI());

                    Log.d(TAG, "RAW DEVICE: name=" + device.getName() + " addr=" + device.getAddress());
                }

                Log.d(TAG, "DEVICE_EVENT: " + device);
            }
        };

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        Log.d(TAG, "IntentFilter set for ACTION_FOUND");

        registerReceiver(receiver, filter);
        Log.d(TAG, "Receiver registered");


        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }

        btAdapter.startDiscovery();
    }

    private void connect() {
        Handler handler = new Handler();
        Runnable checkCondition = new Runnable() {
            @Override
            public void run() {
                if (regState == 1) {
                    hidDevice.connect(targetDevice);
                } else {
                    handler.postDelayed(this, 500);
                }
            }
        };

        handler.post(checkCondition);
    }

    private void toastMessage(String message) {
        Log.d(TAG, message);
        ((Activity) this).runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private byte[] getDescriptor() {
        return new byte[]{
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
    }
}