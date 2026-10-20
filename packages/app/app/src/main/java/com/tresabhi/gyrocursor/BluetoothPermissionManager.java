package com.tresabhi.gyrocursor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class BluetoothPermissionManager {
    public static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;

    // BLUETOOTH_SCAN is declared neverForLocation, so no location permission
    // is needed on API 31+
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
    };

    private final Context context;

    public BluetoothPermissionManager(Context context) {
        this.context = context;
    }

    public static boolean allGranted(int[] grantResults) {
        if (grantResults.length == 0) {
            return false;
        }

        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    // returns true if everything is already granted; otherwise requests the
    // missing permissions and the result arrives in onRequestPermissionsResult
    public boolean checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            return true;
        }

        ActivityCompat.requestPermissions(
                (MainActivity) context,
                permissionsToRequest.toArray(new String[0]),
                REQUEST_BLUETOOTH_PERMISSIONS
        );

        return false;
    }
}