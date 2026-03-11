package com.tresabhi.gyrocursor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class BluetoothPermissionManager {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_BLUETOOTH_SCAN = 2;
    private static final int REQUEST_BLUETOOTH_CONNECT = 3;
    private static final int REQUEST_BLUETOOTH_ADVERTISE = 4;
    private static final int REQUEST_BLUETOOTH_ADMIN = 5;

    private final Context context;

    public BluetoothPermissionManager(Context context, MainActivity controller) {
        this.context = context;
    }

    public void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((MainActivity) context,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_BLUETOOTH_SCAN);
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((MainActivity) context,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH_CONNECT);
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((MainActivity) context,
                    new String[]{Manifest.permission.BLUETOOTH_ADVERTISE},
                    REQUEST_BLUETOOTH_ADVERTISE);
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((MainActivity) context,
                    new String[]{Manifest.permission.BLUETOOTH_ADMIN},
                    REQUEST_BLUETOOTH_ADMIN);
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((MainActivity) context,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSION || requestCode == REQUEST_BLUETOOTH_SCAN
                || requestCode == REQUEST_BLUETOOTH_CONNECT || requestCode == REQUEST_BLUETOOTH_ADVERTISE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("BluetoothPermissionManager", "Permission granted");
                // Handle permission granted scenario
            } else {
                Log.e("BluetoothPermissionManager", "Permission denied");
                // Handle permission denied scenario
            }
        }
    }
}

